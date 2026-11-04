<script setup lang="ts">
import { ref, onMounted, nextTick, onBeforeUnmount, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getConversation, sendMessage, markConversationRead, checkUserOnline, deleteConversation, recallMessage } from '@/api/message'
import { uploadFile } from '@/api/file'
import { getUserProfile } from '@/api/user'
import type { MessageVO } from '@/api/message'
import { useUserStore } from '@/stores/user'
import { subscribe, unsubscribe, connectWebSocket } from '@/utils/websocket'
import type { IMessage } from '@stomp/stompjs'
import { ArrowLeft, Picture, ChatLineSquare, Delete, MoreFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const partnerId = Number(route.params.partnerId)
const partnerAvatar = ref<string | null>(null)
const messages = ref<MessageVO[]>([])
const newMessage = ref('')
const loading = ref(false)
const sending = ref(false)
const currentPage = ref(0)
const hasMore = ref(true)
const chatContainer = ref<HTMLElement | null>(null)
const partnerOnline = ref(false)
const showEmojiPicker = ref(false)
const imageInputRef = ref<HTMLInputElement | null>(null)

const currentUserId = userStore.userInfo?.id

const sendStatusMap = ref<Map<number, string>>(new Map())

interface PendingImage {
  file: File
  url: string
}
const pendingImage = ref<PendingImage | null>(null)
const enlargeImageUrl = ref<string | null>(null)

const emojis = [
  '😀', '😁', '😂', '🤣', '😃', '😄', '😅', '😆',
  '😉', '😊', '😋', '😎', '😍', '😘', '🥰', '😗',
  '🤔', '🤨', '😐', '😑', '😶', '😏', '😒', '🙄',
  '😬', '😮', '😯', '😲', '😳', '🥺', '😢', '😭',
  '😤', '😠', '😡', '🤬', '😈', '👿', '💀', '☠️',
  '👍', '👎', '👏', '🙌', '🤝', '❤️', '🔥', '⭐',
  '🎉', '🎊', '💯', '✨', '🌟', '💪', '🤗', '🫡',
]

function getDateLabel(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const yesterday = new Date(today.getTime() - 86400000)
  const msgDate = new Date(date.getFullYear(), date.getMonth(), date.getDate())

  if (msgDate.getTime() === today.getTime()) return '今天'
  if (msgDate.getTime() === yesterday.getTime()) return '昨天'
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

function shouldShowDateDivider(index: number): boolean {
  if (index === 0) return true
  const prev = new Date(messages.value[index - 1].createdAt)
  const curr = new Date(messages.value[index].createdAt)
  return prev.toDateString() !== curr.toDateString()
}

function shouldShowTime(index: number): boolean {
  if (index === messages.value.length - 1) return true
  const curr = new Date(messages.value[index].createdAt)
  const next = new Date(messages.value[index + 1].createdAt)
  return next.getTime() - curr.getTime() > 5 * 60 * 1000
}

function formatTime(dateStr: string): string {
  const date = new Date(dateStr)
  const h = date.getHours().toString().padStart(2, '0')
  const m = date.getMinutes().toString().padStart(2, '0')
  return `${h}:${m}`
}

function parseShareContent(content: string): { postId: number; title: string } | null {
  try {
    const data = JSON.parse(content)
    if (data.postId && data.title) {
      return data
    }
  } catch {
    // ignore
  }
  return null
}

function goToPost(postId: number) {
  router.push(`/post/${postId}`)
}

function canRecall(msg: MessageVO): boolean {
  if (msg.senderId !== currentUserId) return false
  if (msg.isRecalled) return false
  const createdAt = new Date(msg.createdAt)
  const now = new Date()
  return now.getTime() - createdAt.getTime() <= 2 * 60 * 1000
}

async function loadMessages(page = 0) {
  loading.value = true
  try {
    const res = await getConversation(partnerId, page, 20)
    const data = res.data.data
    const olderContent = data.content.reverse()
    if (page === 0) {
      messages.value = olderContent
    } else {
      messages.value = [...olderContent, ...messages.value]
    }
    currentPage.value = data.number
    hasMore.value = data.number + 1 < data.totalPages
    if (page === 0) {
      await nextTick()
      scrollToBottom()
    }
  } finally {
    loading.value = false
  }
}

async function handleSend() {
  const content = newMessage.value.trim()
  const hasText = !!content
  const hasImage = !!pendingImage.value
  if ((!hasText && !hasImage) || sending.value) return

  sending.value = true
  try {
    // 先发图片（如果有）
    if (pendingImage.value) {
      const uploadRes = await uploadFile(pendingImage.value.file)
      const imageUrl = uploadRes.data.data
      const res = await sendMessage(partnerId, imageUrl, 'IMAGE')
      const sentMsg = res.data.data
      messages.value.push(sentMsg)
      sendStatusMap.value.set(sentMsg.id, 'sent')
    }
    // 再发文字（如果有）
    if (hasText) {
      const res = await sendMessage(partnerId, content, 'TEXT')
      newMessage.value = ''
      const sentMsg = res.data.data
      messages.value.push(sentMsg)
      sendStatusMap.value.set(sentMsg.id, 'sent')
    }
    clearPendingImage()
    await nextTick()
    scrollToBottom()
  } catch (err: any) {
    const code = err?.response?.data?.code
    if (code === 8001) {
      ElMessage.warning('对方已关闭私信')
    } else if (code === 8002) {
      ElMessage.warning('对方仅接受关注者的私信')
    } else {
      ElMessage.error('发送失败')
    }
  } finally {
    sending.value = false
  }
}

async function handleSendImage() {
  imageInputRef.value?.click()
}

async function onImageSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    input.value = ''
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过5MB')
    input.value = ''
    return
  }

  if (pendingImage.value) {
    URL.revokeObjectURL(pendingImage.value.url)
  }
  pendingImage.value = { file, url: URL.createObjectURL(file) }
  input.value = ''
}

function removePendingImage() {
  if (pendingImage.value) {
    URL.revokeObjectURL(pendingImage.value.url)
    pendingImage.value = null
  }
}

function clearPendingImage() {
  removePendingImage()
}

function openEnlargedImage(url: string) {
  enlargeImageUrl.value = url
}

function closeEnlargedImage() {
  enlargeImageUrl.value = null
}

function insertEmoji(emoji: string) {
  newMessage.value += emoji
  showEmojiPicker.value = false
}

async function loadOlderMessages() {
  if (!hasMore.value || loading.value) return
  const prevHeight = chatContainer.value?.scrollHeight ?? 0
  await loadMessages(currentPage.value + 1)
  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight - prevHeight
  }
}

function scrollToBottom() {
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

function onWebSocketMessage(message: IMessage) {
  try {
    const data = JSON.parse(message.body)
    if (!data.message) return
    const msg = data.message as MessageVO
    // 撤回消息：更新已有消息，不新增（双方都走此分支）
    if (msg.isRecalled) {
      const idx = messages.value.findIndex(m => m.id === msg.id)
      if (idx !== -1) {
        messages.value[idx] = { ...messages.value[idx], isRecalled: true }
      }
      return
    }
    // 对方发来的新消息：追加
    if (msg.senderId === partnerId) {
      messages.value.push(msg)
      nextTick(() => scrollToBottom())
      markConversationRead(partnerId)
    }
  } catch {
    // ignore
  }
}

function onOnlineStatusMessage(message: IMessage) {
  try {
    const data = JSON.parse(message.body)
    if (data.userId === partnerId) {
      partnerOnline.value = data.online
    }
  } catch {
    // ignore
  }
}

async function loadOnlineStatus() {
  try {
    const res = await checkUserOnline(partnerId)
    partnerOnline.value = res.data.data
  } catch {
    // ignore
  }
}

async function loadPartnerProfile() {
  try {
    const res = await getUserProfile(partnerId)
    partnerAvatar.value = res.data.data.avatarUrl
  } catch {
    // ignore
  }
}

function goToProfile(userId: number) {
  router.push(`/profile/${userId}`)
}

async function handleDeleteConversation() {
  try {
    await ElMessageBox.confirm('确定删除与该用户的聊天记录吗？删除后无法恢复！', '删除会话', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteConversation(partnerId)
    ElMessage.success('会话已删除')
    router.back()
  } catch {
    // 用户取消
  }
}

async function handleRecall(msg: MessageVO) {
  if (!canRecall(msg)) return
  try {
    await recallMessage(msg.id)
    msg.isRecalled = true
    ElMessage.success('消息已撤回')
  } catch (err: any) {
    const code = err?.response?.data?.code
    if (code === 8004) {
      ElMessage.warning('只能撤回自己发送的消息')
    } else if (code === 8005) {
      ElMessage.warning('消息已撤回')
    } else if (code === 8006) {
      ElMessage.warning('只能撤回2分钟内的消息')
    } else {
      ElMessage.error('撤回失败')
    }
  }
}

function handleClickOutside(event: MouseEvent) {
  const target = event.target as HTMLElement
  if (!target.closest('.emoji-wrapper')) {
    showEmojiPicker.value = false
  }
}

async function initChat() {
  await loadMessages()
  await markConversationRead(partnerId)
  window.dispatchEvent(new CustomEvent('messages-refreshed'))
  subscribe('chat-messages', '/user/topic/messages', onWebSocketMessage)
  subscribe('chat-online', '/topic/online', onOnlineStatusMessage)
  loadOnlineStatus()
  loadPartnerProfile()
}

onMounted(() => {
  initChat()
  document.addEventListener('click', handleClickOutside)
})

onBeforeUnmount(() => {
  unsubscribe('chat-messages')
  unsubscribe('chat-online')
  clearPendingImage()
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div class="chat-page">
    <div class="chat-header">
      <el-button text @click="router.back()">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
      <h2>
        私信
        <span class="online-status" :class="{ online: partnerOnline }">
          {{ partnerOnline ? '在线' : '离线' }}
        </span>
      </h2>
      <el-button text type="danger" @click="handleDeleteConversation">
        <el-icon><Delete /></el-icon>
      </el-button>
    </div>

    <div class="chat-body" ref="chatContainer">
      <div v-if="hasMore" class="load-older">
        <el-button text size="small" @click="loadOlderMessages" :loading="loading">
          加载更早消息
        </el-button>
      </div>

      <template v-for="(msg, index) in messages" :key="msg.id">
        <div v-if="shouldShowDateDivider(index)" class="date-divider">
          <span>{{ getDateLabel(msg.createdAt) }}</span>
        </div>

        <div
          class="message-row"
          :class="{ 'is-self': msg.senderId === currentUserId }"
        >
          <el-avatar
            v-if="msg.senderId === currentUserId"
            class="message-avatar"
            :size="36"
            :src="userStore.userInfo?.avatarUrl || undefined"
            @click.stop="goToProfile(userStore.userInfo!.id)"
          >
            {{ userStore.userInfo?.nickname?.charAt(0) }}
          </el-avatar>
          <div class="message-bubble">
            <template v-if="msg.isRecalled">
              <div class="recalled-message">消息已撤回</div>
            </template>
            <template v-else>
              <div v-if="(msg.messageType || 'TEXT') === 'IMAGE'" class="message-image">
                <el-image
                  :src="msg.content"
                  fit="cover"
                  :preview-src-list="[msg.content]"
                  :initial-index="0"
                  style="max-width: 200px; max-height: 200px; border-radius: 8px; cursor: pointer;"
                />
              </div>
              <div v-else-if="(msg.messageType || 'TEXT') === 'SHARE'" class="message-share">
                <template v-if="parseShareContent(msg.content)">
                  <div class="share-label">分享了一篇帖子</div>
                  <div class="share-title" @click="goToPost(parseShareContent(msg.content)!.postId)">
                    {{ parseShareContent(msg.content)!.title }}
                  </div>
                </template>
                <template v-else>
                  <div class="message-content">{{ msg.content }}</div>
                </template>
              </div>
              <div v-else class="message-content">{{ msg.content }}</div>
            </template>
            <div class="message-meta">
              <span v-if="shouldShowTime(index) && !msg.isRecalled" class="message-time">{{ formatTime(msg.createdAt) }}</span>
              <span v-if="msg.senderId === currentUserId && sendStatusMap.get(msg.id) === 'sending'" class="send-status sending">发送中</span>
            </div>
            <button
              v-if="canRecall(msg)"
              class="recall-btn"
              @click.stop="handleRecall(msg)"
              title="撤回"
            >撤回</button>
          </div>
          <el-avatar
            v-if="msg.senderId !== currentUserId"
            class="message-avatar"
            :size="36"
            :src="partnerAvatar || undefined"
            @click.stop="goToProfile(partnerId)"
          >
            {{ route.params.partnerName?.toString()?.charAt(0) || '?' }}
          </el-avatar>
        </div>
      </template>

      <el-empty v-if="!loading && messages.length === 0" description="发送第一条消息开始聊天吧" />
    </div>

    <div class="chat-footer">
      <div v-if="pendingImage" class="pending-images-row">
          <div class="pending-image-item">
            <img :src="pendingImage.url" class="pending-image-thumb" @click="openEnlargedImage(pendingImage.url)" />
            <button class="pending-image-remove" @click.stop="removePendingImage()">&times;</button>
          </div>
        </div>
      <div class="chat-toolbar">
        <el-button text @click="handleSendImage" :disabled="sending || !!pendingImage" title="发送图片">
          <el-icon :size="18"><Picture /></el-icon>
        </el-button>
        <div class="emoji-wrapper">
          <el-button text @click="showEmojiPicker = !showEmojiPicker" title="表情">
            <el-icon :size="18"><ChatLineSquare /></el-icon>
          </el-button>
          <div v-if="showEmojiPicker" class="emoji-picker">
            <span
              v-for="emoji in emojis"
              :key="emoji"
              class="emoji-item"
              @click="insertEmoji(emoji)"
            >{{ emoji }}</span>
          </div>
        </div>
      </div>
      <div class="chat-input-row">
        <el-input
          v-model="newMessage"
          placeholder="输入消息..."
          @keyup.enter="handleSend"
          :disabled="sending"
          maxlength="500"
          show-word-limit
        />
        <el-button
          type="primary"
          @click="handleSend"
          :loading="sending"
          :disabled="!newMessage.trim() && !pendingImage"
        >
          发送
        </el-button>
      </div>
      <input
        ref="imageInputRef"
        type="file"
        accept="image/*"
        style="display: none"
        @change="onImageSelected"
      />
    </div>

    <Teleport to="body">
      <div v-if="enlargeImageUrl" class="enlarge-overlay" @click.self="closeEnlargedImage">
        <img :src="enlargeImageUrl" class="enlarge-img" />
        <button class="enlarge-close" @click="closeEnlargedImage">&times;</button>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.chat-page {
  max-width: 700px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 160px);
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  overflow: hidden;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color);
}

.chat-header h2 {
  flex: 1;
  text-align: center;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
}

.online-status {
  font-size: 12px;
  font-weight: normal;
  padding: 2px 8px;
  border-radius: 10px;
  background: var(--bg-card-hover);
  color: var(--text-muted);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.online-status.online {
  background: #e8f8e8;
  color: #67c23a;
}

.online-status.online::before {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #67c23a;
  animation: onlinePulse 1.5s ease-in-out infinite;
}

@keyframes onlinePulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.5;
    transform: scale(1.4);
  }
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: var(--bg-page);
}

.load-older {
  text-align: center;
  padding: 8px 0;
}

.date-divider {
  display: flex;
  align-items: center;
  padding: 16px 0 8px;
  gap: 12px;
}

.date-divider::before,
.date-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--border-color);
}

.date-divider span {
  font-size: 12px;
  color: var(--text-muted);
  white-space: nowrap;
  padding: 2px 0;
}

.message-row {
  display: flex;
  justify-content: flex-end;
  padding: 8px 0;
  gap: 10px;
  align-items: flex-end;
}

.message-row.is-self {
  justify-content: flex-start;
}

.message-avatar {
  cursor: pointer;
  flex-shrink: 0;
  transition: transform 0.2s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.message-avatar:hover {
  transform: scale(1.08);
}

.message-bubble {
  max-width: 65%;
  padding: 12px 16px;
  border-radius: 18px 18px 4px 18px;
  background: linear-gradient(135deg, #ff6b8a, #ff8fa3);
  color: #fff;
  word-break: break-word;
  box-shadow: 0 2px 12px rgba(255, 107, 138, 0.2);
  position: relative;
}

.message-row.is-self .message-bubble {
  border-radius: 18px 18px 18px 4px;
  background: var(--bg-card);
  color: var(--text-primary);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-color);
}

.message-content {
  font-size: 15px;
  line-height: 1.6;
}

.message-image {
  line-height: 0;
}

.message-share {
  min-width: 140px;
}

.share-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
  margin-bottom: 8px;
}

.message-row.is-self .share-label {
  color: var(--text-muted);
}

.share-title {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
  padding: 10px 14px;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 10px;
  transition: all 0.2s;
}

.message-row.is-self .share-title {
  background: linear-gradient(135deg, #fff0f2 0%, #ffe4e8 100%);
  color: #ff2442;
}

.share-title:hover {
  background: rgba(255, 255, 255, 0.35);
  color: #fff;
  transform: translateY(-1px);
}

.message-row.is-self .share-title:hover {
  background: linear-gradient(135deg, #ffe4e8 0%, #ffd0d6 100%);
  color: #ff2442;
}

.recalled-message {
  font-size: 13px;
  color: var(--text-muted);
  font-style: italic;
  padding: 4px 0;
}

.message-meta {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 4px;
}

.message-time {
  font-size: 11px;
  opacity: 0.5;
}

.message-row.is-self .message-time {
  opacity: 0.6;
}

.send-status {
  font-size: 10px;
  opacity: 0.6;
}

.send-status.sending {
  color: #e6a23c;
  opacity: 1;
}

.chat-footer {
  flex-shrink: 0;
  border-top: 1px solid var(--border-color);
  padding: 12px 20px 16px;
  background: var(--bg-card);
}

.chat-toolbar {
  display: flex;
  gap: 4px;
  margin-bottom: 8px;
}

.emoji-wrapper {
  position: relative;
}

.emoji-picker {
  position: absolute;
  bottom: 36px;
  left: 0;
  background: var(--bg-card);
  border-radius: 12px;
  box-shadow: var(--shadow-lg);
  padding: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  width: 280px;
  z-index: 10;
}

.emoji-item {
  font-size: 20px;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: background 0.15s;
  user-select: none;
  display: inline-block;
  width: 28px;
  height: 28px;
  text-align: center;
  line-height: 28px;
}

.emoji-item:hover {
  background: var(--primary-bg);
}

.chat-input-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.chat-input-row .el-input {
  flex: 1;
}

.chat-input-row .el-input :deep(.el-input__wrapper) {
  border-radius: 12px;
}

.chat-input-row .el-button {
  border-radius: 12px;
}

.recall-btn {
  position: absolute;
  right: 6px;
  bottom: -22px;
  border: none;
  background: var(--bg-card-hover);
  color: var(--text-muted);
  font-size: 11px;
  padding: 2px 10px;
  border-radius: var(--radius-full);
  cursor: pointer;
  opacity: 0;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.message-bubble:hover .recall-btn {
  opacity: 1;
}

.recall-btn:hover {
  background: var(--primary-bg);
  color: var(--primary);
}

.pending-images-row {
  display: flex;
  gap: 8px;
  padding: 8px 0 4px;
  flex-wrap: wrap;
}

.pending-image-item {
  position: relative;
  width: 64px;
  height: 64px;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid #e0e0e0;
  flex-shrink: 0;
}

.pending-image-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  cursor: pointer;
  transition: opacity 0.2s;
}

.pending-image-thumb:hover {
  opacity: 0.8;
}

.pending-image-remove {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  border: 2px solid #fff;
  font-size: 12px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 0;
  z-index: 1;
}

.pending-image-remove:hover {
  background: #ff4d4f;
}

.enlarge-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
}

.enlarge-img {
  max-width: 90vw;
  max-height: 90vh;
  border-radius: 8px;
  object-fit: contain;
}

.enlarge-close {
  position: fixed;
  top: 20px;
  right: 28px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  border: none;
  font-size: 22px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.enlarge-close:hover {
  background: rgba(255, 255, 255, 0.3);
}
</style>

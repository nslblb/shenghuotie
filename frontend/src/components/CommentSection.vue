<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { getComments, createComment, deleteComment, type CommentPageData } from '@/api/comment'
import { useUserStore } from '@/stores/user'
import type { CommentVO } from '@/types'
import { formatRelativeTime } from '@/utils/date'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps<{
  postId: number
  postAuthorId: number
  comments: CommentVO[]
  commentCount: number
  loadFailed: boolean
}>()

const emit = defineEmits<{
  'update:commentCount': [count: number]
  'update:comments': [comments: CommentVO[]]
  reload: []
}>()

const userStore = useUserStore()
const router = useRouter()
const commentText = ref('')
const submitting = ref(false)

const currentPage = ref(0)
const totalPages = ref(1)
const allComments = ref<CommentVO[]>([])
const commentsLoading = ref(false)

// 记录当前正在回复的评论ID
const replyingToId = ref<number | null>(null)
const replyTexts = ref<Record<number, string>>({})
// 记录子评论展开/收起状态
const collapsedReplies = ref<Set<number>>(new Set())
// 记录正在回复的子评论ID
const replyingToReplyId = ref<number | null>(null)
const replyReplyTexts = ref<Record<number, string>>({})

const displayComments = computed(() => allComments.value)
const hasMore = computed(() => currentPage.value < totalPages.value - 1)
const isEmpty = computed(() => !commentsLoading.value && allComments.value.length === 0)

function stripHtml(html: string): string {
  return html.replace(/<[^>]*>/g, '')
}

function onInputComment() {
  commentText.value = stripHtml(commentText.value)
}

function onInputReply(commentId: number) {
  if (replyTexts.value[commentId]) {
    replyTexts.value[commentId] = stripHtml(replyTexts.value[commentId])
  }
}

async function loadComments() {
  try {
    commentsLoading.value = true
    const res = await getComments(props.postId, currentPage.value, 20)
    const data = res.data.data
    
    if (currentPage.value === 0) {
      allComments.value = data.comments
      collapsedReplies.value.clear()
    } else {
      allComments.value = [...allComments.value, ...data.comments]
    }

    for (const comment of data.comments) {
      if (comment.replies?.length) {
        collapsedReplies.value.add(comment.id)
      }
    }
    
    totalPages.value = data.totalPages
    emit('update:comments', allComments.value)
  } catch {
    if (currentPage.value === 0) {
      emit('reload')
    }
  } finally {
    commentsLoading.value = false
  }
}

async function loadMore() {
  currentPage.value++
  await loadComments()
}

async function handleComment() {
  if (!userStore.isLoggedIn) {
    window.location.href = '/login'
    return
  }
  if (!commentText.value.trim() || submitting.value) return

  const savedText = commentText.value
  const savedCount = props.commentCount
  
  const tempComment: CommentVO = {
    id: Date.now(),
    content: commentText.value.trim(),
    userName: userStore.userInfo?.nickname || '用户',
    userAvatar: userStore.userInfo?.avatarUrl || '',
    createdAt: new Date().toISOString(),
    replies: [],
  } as CommentVO
  
  allComments.value.unshift(tempComment)
  emit('update:commentCount', savedCount + 1)
  commentText.value = ''
  submitting.value = true
  
  try {
    await createComment(props.postId, {
      content: savedText.trim(),
    })
    ElMessage.success('评论成功')
  } catch {
    allComments.value = allComments.value.filter(c => c.id !== tempComment.id)
    emit('update:commentCount', savedCount)
    commentText.value = savedText
    ElMessage.error('评论发布失败')
  } finally {
    submitting.value = false
  }
}

function handleReply(comment: CommentVO) {
  if (replyingToId.value === comment.id) {
    replyingToId.value = null
    return
  }
  replyingToId.value = comment.id
  if (!replyTexts.value[comment.id]) {
    replyTexts.value[comment.id] = ''
  }
  nextTick(() => {
    const el = document.querySelector(`[data-reply-input="${comment.id}"] textarea`) as HTMLElement
    el?.focus()
  })
}

function handleCancelReply() {
  replyingToId.value = null
}

function toggleReplies(commentId: number) {
  if (collapsedReplies.value.has(commentId)) {
    collapsedReplies.value.delete(commentId)
  } else {
    collapsedReplies.value.add(commentId)
  }
}

function handleReplyToReply(reply: CommentVO, parentComment: CommentVO) {
  if (replyingToReplyId.value === reply.id) {
    replyingToReplyId.value = null
    return
  }
  replyingToReplyId.value = reply.id
  if (!replyReplyTexts.value[reply.id]) {
    replyReplyTexts.value[reply.id] = ''
  }
  nextTick(() => {
    const el = document.querySelector(`[data-reply-reply-input="${reply.id}"] textarea`) as HTMLElement
    el?.focus()
  })
}

function handleCancelReplyToReply() {
  replyingToReplyId.value = null
}

async function handleSubmitReplyToReply(reply: CommentVO, parentComment: CommentVO) {
  if (!userStore.isLoggedIn) {
    window.location.href = '/login'
    return
  }
  const text = replyReplyTexts.value[reply.id]?.trim()
  if (!text || submitting.value) return

  const savedText = text
  const savedCount = props.commentCount
  
  const tempReply: CommentVO = {
    id: Date.now(),
    content: savedText,
    userName: userStore.userInfo?.nickname || '用户',
    userAvatar: userStore.userInfo?.avatarUrl || '',
    createdAt: new Date().toISOString(),
    parentId: parentComment.id,
    replyToUserId: reply.userId,
    replyToUserName: reply.userName,
    replies: [],
  } as CommentVO
  
  if (!parentComment.replies) {
    parentComment.replies = []
  }
  parentComment.replies.push(tempReply)
  emit('update:commentCount', savedCount + 1)
  replyReplyTexts.value[reply.id] = ''
  replyingToReplyId.value = null
  submitting.value = true
  
  try {
    await createComment(props.postId, {
      content: savedText,
      parentId: parentComment.id,
      replyToUserId: reply.userId,
    })
    ElMessage.success('回复成功')
  } catch {
    if (parentComment.replies) {
      parentComment.replies = parentComment.replies.filter(r => r.id !== tempReply.id)
    }
    emit('update:commentCount', savedCount)
    replyReplyTexts.value[reply.id] = savedText
    ElMessage.error('回复发布失败')
  } finally {
    submitting.value = false
  }
}

async function handleSubmitReply(comment: CommentVO) {
  if (!userStore.isLoggedIn) {
    window.location.href = '/login'
    return
  }
  const text = replyTexts.value[comment.id]?.trim()
  if (!text || submitting.value) return

  const savedText = text
  const savedCount = props.commentCount
  
  const tempReply: CommentVO = {
    id: Date.now(),
    content: savedText,
    userName: userStore.userInfo?.nickname || '用户',
    userAvatar: userStore.userInfo?.avatarUrl || '',
    createdAt: new Date().toISOString(),
    parentId: comment.id,
    replyToUserName: comment.userName,
    replies: [],
  } as CommentVO
  
  if (!comment.replies) {
    comment.replies = []
  }
  comment.replies.push(tempReply)
  emit('update:commentCount', savedCount + 1)
  replyTexts.value[comment.id] = ''
  submitting.value = true
  
  try {
    await createComment(props.postId, {
      content: savedText,
      parentId: comment.id,
    })
    ElMessage.success('回复成功')
    replyingToId.value = null
  } catch {
    if (comment.replies) {
      comment.replies = comment.replies.filter(r => r.id !== tempReply.id)
    }
    emit('update:commentCount', savedCount)
    replyTexts.value[comment.id] = savedText
    ElMessage.error('回复发布失败')
  } finally {
    submitting.value = false
  }
}

function canDeleteComment(comment: CommentVO): boolean {
  return userStore.userInfo?.id === comment.userId || userStore.userInfo?.id === props.postAuthorId
}

async function handleDeleteComment(comment: CommentVO) {
  try {
    await ElMessageBox.confirm('确定删除这条评论吗？删除后无法恢复', '删除评论', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    
    await deleteComment(props.postId, comment.id)
    
    removeCommentFromList(comment.id)
    emit('update:commentCount', props.commentCount - 1)
    ElMessage.success('评论已删除')
  } catch {
  }
}

function removeCommentFromList(commentId: number) {
  for (let i = 0; i < allComments.value.length; i++) {
    const comment = allComments.value[i]
    if (comment.id === commentId) {
      allComments.value.splice(i, 1)
      return
    }
    if (comment.replies) {
      for (let j = 0; j < comment.replies.length; j++) {
        if (comment.replies[j].id === commentId) {
          comment.replies.splice(j, 1)
          return
        }
      }
    }
  }
}

onMounted(() => {
  if (userStore.isLoggedIn) {
    loadComments()
  }
})

defineExpose({ loadComments })
</script>

<template>
  <div class="comment-section">
    <div class="section-title">
      <span class="title-text">评论</span>
      <span class="title-count" v-if="commentCount">{{ commentCount }}</span>
    </div>

    <!-- 未登录提示 -->
    <div v-if="!userStore.isLoggedIn" class="login-prompt">
      <el-empty description="登录后查看评论" :image-size="80">
        <el-button type="primary" @click="router.push('/login')">去登录</el-button>
      </el-empty>
    </div>

    <div v-else class="comment-list">
      <div v-if="loadFailed" class="comments-error">
        <el-empty description="评论加载失败" :image-size="80">
          <el-button type="primary" text @click="emit('reload')">重新加载</el-button>
        </el-empty>
      </div>
      <template v-else>
        <div v-loading="commentsLoading && allComments.length === 0">
          <div v-for="comment in displayComments" :key="comment.id" class="comment-item">
            <el-avatar :size="36" :src="comment.userAvatar || undefined" class="comment-avatar clickable-avatar" @click="router.push(`/profile/${comment.userId}`)">
              {{ comment.userName?.charAt(0) }}
            </el-avatar>
            <div class="comment-body">
              <div class="comment-header">
                <span class="comment-author">{{ comment.userName }}</span>
                <span class="comment-time">{{ formatRelativeTime(comment.createdAt) }}</span>
                <span v-if="comment.replies?.length && collapsedReplies.has(comment.id)" class="replies-toggle" @click="toggleReplies(comment.id)">
                  <svg viewBox="0 0 24 24" width="14" height="14" class="toggle-icon collapsed"><path d="M7 10l5 5 5-5z" fill="currentColor"/></svg>
                  <span>展开</span>
                </span>
                <button class="reply-btn" @click="handleReply(comment)">
                  回复
                </button>
                <button v-if="canDeleteComment(comment)" class="delete-btn" @click="handleDeleteComment(comment)">
                  删除
                </button>
              </div>
              <p class="comment-text">{{ comment.content }}</p>

              <!-- 内联回复输入框 -->
              <div v-if="replyingToId === comment.id" class="inline-reply">
                <div class="inline-reply-header">
                  <span class="inline-reply-label">回复 {{ comment.userName }}</span>
                  <button class="cancel-reply-btn" @click="handleCancelReply">
                    <svg viewBox="0 0 24 24" width="14" height="14"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" fill="currentColor"/></svg>
                  </button>
                </div>
                <div class="inline-reply-input-row">
                  <el-input
                    v-model="replyTexts[comment.id]"
                    type="textarea"
                    :rows="2"
                    :placeholder="`回复 ${comment.userName}...`"
                    maxlength="200"
                    show-word-limit
                    :data-reply-input="comment.id"
                    @input="onInputReply(comment.id)"
                    @keyup.enter.ctrl="handleSubmitReply(comment)"
                  />
                  <el-button
                    type="primary"
                    size="small"
                    class="inline-reply-submit"
                    :loading="submitting"
                    :disabled="!replyTexts[comment.id]?.trim() || submitting"
                    @click="handleSubmitReply(comment)"
                  >
                    回复
                  </el-button>
                </div>
              </div>

              <div v-if="comment.replies?.length && !collapsedReplies.has(comment.id)" class="replies">
                <div v-for="reply in comment.replies" :key="reply.id" class="reply-item">
                  <el-avatar :size="26" :src="reply.userAvatar || undefined" class="reply-avatar clickable-avatar" @click="router.push(`/profile/${reply.userId}`)">
                    {{ reply.userName?.charAt(0) }}
                  </el-avatar>
                  <div class="reply-body">
                    <div class="reply-header">
                      <span class="reply-author">{{ reply.userName }}</span>
                      <span v-if="reply.replyToUserName" class="reply-to">
                        回复 <span class="reply-to-target">{{ reply.replyToUserName }}</span>
                      </span>
                      <span class="reply-time">{{ formatRelativeTime(reply.createdAt) }}</span>
                      <button class="reply-reply-btn" @click="handleReplyToReply(reply, comment)">
                        回复
                      </button>
                      <button v-if="canDeleteComment(reply)" class="reply-delete-btn" @click="handleDeleteComment(reply)">
                        删除
                      </button>
                    </div>
                    <p class="reply-text">{{ reply.content }}</p>
                    <!-- 回复子评论的输入框 -->
                    <div v-if="replyingToReplyId === reply.id" class="inline-reply-reply">
                      <div class="inline-reply-header">
                        <span class="inline-reply-label">回复 {{ reply.userName }}</span>
                        <button class="cancel-reply-btn" @click="handleCancelReplyToReply">
                          <svg viewBox="0 0 24 24" width="14" height="14"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" fill="currentColor"/></svg>
                        </button>
                      </div>
                      <div class="inline-reply-input-row">
                        <el-input
                          v-model="replyReplyTexts[reply.id]"
                          type="textarea"
                          :rows="2"
                          :placeholder="`回复 ${reply.userName}...`"
                          maxlength="200"
                          show-word-limit
                          :data-reply-reply-input="reply.id"
                          @keyup.enter.ctrl="handleSubmitReplyToReply(reply, comment)"
                        />
                        <el-button
                          type="primary"
                          size="small"
                          class="inline-reply-submit"
                          :loading="submitting"
                          :disabled="!replyReplyTexts[reply.id]?.trim() || submitting"
                          @click="handleSubmitReplyToReply(reply, comment)"
                        >
                          回复
                        </el-button>
                      </div>
                    </div>
                  </div>
                </div>
                <div class="collapse-btn-wrapper">
                  <span class="replies-toggle collapse-btn" @click="toggleReplies(comment.id)">
                    <svg viewBox="0 0 24 24" width="14" height="14" class="toggle-icon"><path d="M7 10l5 5 5-5z" fill="currentColor"/></svg>
                    <span>收起回复</span>
                  </span>
                </div>
              </div>
            </div>
          </div>

          <div v-if="hasMore" class="load-more-comments">
            <el-button text @click="loadMore" :loading="commentsLoading">
              展开更多评论
            </el-button>
          </div>

          <el-empty v-if="isEmpty" description="暂无评论，快来抢沙发吧" :image-size="80">
            <template #image>
              <svg viewBox="0 0 100 100" class="empty-svg">
                <circle cx="50" cy="40" r="20" fill="#f0f1f5" />
                <circle cx="42" cy="36" r="3" fill="#d0d0d0" />
                <circle cx="58" cy="36" r="3" fill="#d0d0d0" />
                <path d="M42 48 Q50 55 58 48" stroke="#d0d0d0" stroke-width="2" fill="none" />
                <rect x="30" y="65" width="40" height="20" rx="10" fill="#f0f1f5" />
                <line x1="38" y1="72" x2="62" y2="72" stroke="#d0d0d0" stroke-width="2" stroke-linecap="round" />
              </svg>
            </template>
          </el-empty>
        </div>
      </template>
    </div>

    <div class="comment-input">
      <el-input
        v-model="commentText"
        type="textarea"
        :rows="1"
        placeholder="写下你的评论..."
        maxlength="200"
        @input="onInputComment"
        @keyup.enter.ctrl="handleComment"
      />
      <el-button
        type="primary"
        class="submit-btn"
        :loading="submitting"
        :disabled="!commentText.trim() || submitting"
        @click="handleComment"
      >
        发布
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.comment-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.title-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.title-count {
  font-size: 12px;
  color: var(--text-muted);
  background: var(--bg-page);
  padding: 2px 8px;
  border-radius: var(--radius-full);
  font-weight: 500;
}

.comment-input {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 12px;
  padding-bottom: 12px;
  border-top: 1px solid #f0f0f0;
  margin-top: 8px;
  flex-shrink: 0;
}

.comment-input :deep(.el-textarea__inner) {
  border-radius: var(--radius-full);
  padding: 8px 14px;
}

.comment-input :deep(.el-textarea__inner:focus) {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px rgba(255, 36, 66, 0.1);
}

.submit-btn {
  border-radius: var(--radius-full);
  padding: 8px 20px;
  font-weight: 500;
  flex-shrink: 0;
}

.inline-reply {
  margin-top: 10px;
  padding: 12px;
  background: #f7f8fa;
  border-radius: var(--radius-sm);
  animation: fadeInReply 0.2s ease;
}

@keyframes fadeInReply {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.inline-reply-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.inline-reply-label {
  font-size: 12px;
  color: var(--primary);
  font-weight: 500;
}

.cancel-reply-btn {
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  padding: 2px;
  display: flex;
  align-items: center;
  border-radius: 4px;
  transition: all 0.2s;
}

.cancel-reply-btn:hover {
  color: var(--primary);
  background: rgba(255, 36, 66, 0.1);
}

.inline-reply :deep(.el-textarea__inner) {
  border-radius: var(--radius-sm);
  transition: border-color 0.2s;
}

.inline-reply :deep(.el-textarea__inner:focus) {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px rgba(255, 36, 66, 0.1);
}

.inline-reply-input-row {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.inline-reply-input-row .el-input {
  flex: 1;
}

.inline-reply-submit {
  border-radius: var(--radius-full);
  font-weight: 500;
  flex-shrink: 0;
  height: 32px;
  margin-top: 0;
}

.reply-to {
  color: var(--text-muted);
  font-size: 13px;
  margin-right: 2px;
}

.reply-to .reply-to-target {
  color: var(--primary);
  font-weight: 500;
}

.comments-error {
  padding: 24px 0;
  text-align: center;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  flex: 1;
  overflow-y: auto;
  padding-bottom: 8px;
  min-height: 0;
  max-height: 400px;
}

.comment-list::-webkit-scrollbar {
  width: 4px;
}

.comment-list::-webkit-scrollbar-track {
  background: transparent;
}

.comment-list::-webkit-scrollbar-thumb {
  background: #d0d0d0;
  border-radius: 2px;
}

.comment-item {
  display: flex;
  gap: 10px;
  padding: 12px 0;
}

.comment-item + .comment-item {
  border-top: 1px solid #f5f5f5;
}

.comment-avatar {
  flex-shrink: 0;
  border: 1.5px solid #f0f1f5;
}

.clickable-avatar {
  cursor: pointer;
  transition: transform 0.2s, border-color 0.2s;
}

.clickable-avatar:hover {
  transform: scale(1.08);
  border-color: #ffcdd2;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.comment-author {
  font-weight: 600;
  font-size: 14px;
  color: var(--text-primary);
}

.comment-time {
  font-size: 12px;
  color: var(--text-muted);
}

.comment-text {
  margin: 6px 0;
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  word-break: break-word;
}

.comment-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.reply-btn {
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  padding: 2px 0;
  transition: color 0.2s;
}

.reply-btn:hover {
  color: var(--primary);
}

.delete-btn {
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  padding: 2px 0;
  transition: color 0.2s;
}

.delete-btn:hover {
  color: #ff4d4f;
}

.replies-toggle {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 8px;
  background: #f7f8fa;
  border-radius: var(--radius-full);
  font-size: 11px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;
  margin-left: 4px;
}

.replies-toggle:hover {
  color: var(--primary);
  background: var(--primary-bg);
}

.toggle-icon {
  transition: transform 0.2s;
}

.toggle-icon.collapsed {
  transform: rotate(-90deg);
}

.replies {
  margin-top: 10px;
  padding: 10px 12px;
  background: #f7f8fa;
  border-radius: var(--radius-sm);
}

.collapse-btn-wrapper {
  text-align: center;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #eee;
}

.collapse-btn {
  font-size: 12px;
}

.reply-item {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.reply-avatar {
  flex-shrink: 0;
}

.reply-body {
  flex: 1;
  min-width: 0;
}

.reply-header {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  margin-bottom: 2px;
}

.reply-author {
  font-weight: 600;
  font-size: 13px;
  color: var(--text-primary);
}

.reply-text {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.reply-time {
  font-size: 11px;
  color: var(--text-muted);
}

.reply-delete-btn {
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 11px;
  cursor: pointer;
  padding: 0;
  margin-left: 8px;
  transition: color 0.2s;
}

.reply-delete-btn:hover {
  color: #ff4d4f;
}

.reply-reply-btn {
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 11px;
  cursor: pointer;
  padding: 0;
  margin-left: 8px;
  transition: color 0.2s;
}

.reply-reply-btn:hover {
  color: var(--primary);
}

.inline-reply-reply {
  margin-top: 8px;
  padding: 10px;
  background: #fff;
  border-radius: var(--radius-sm);
  border: 1px solid #eee;
  animation: fadeInReply 0.2s ease;
}

.load-more-comments {
  text-align: center;
  padding: 12px 0;
  border-top: 1px solid #f5f5f5;
  margin-top: 8px;
}

.empty-svg {
  width: 80px;
  height: 80px;
}
</style>

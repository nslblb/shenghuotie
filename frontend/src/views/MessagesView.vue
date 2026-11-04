<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getConversations, deleteConversation } from '@/api/message'
import type { ConversationVO } from '@/api/message'
import { formatRelativeTime } from '@/utils/date'
import { ArrowLeft, ChatDotRound, Delete, Check, Close } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const conversations = ref<ConversationVO[]>([])
const loading = ref(false)
const isSelectMode = ref(false)
const selectedIds = ref<number[]>([])

const isAllSelected = computed(() => {
  return conversations.value.length > 0 && selectedIds.value.length === conversations.value.length
})

async function loadConversations() {
  loading.value = true
  try {
    const res = await getConversations()
    conversations.value = res.data.data
  } finally {
    loading.value = false
  }
}

function openChat(partnerId: number) {
  if (isSelectMode.value) {
    toggleSelect(partnerId)
    return
  }
  router.push(`/messages/${partnerId}`)
}

function toggleSelectMode() {
  isSelectMode.value = !isSelectMode.value
  if (!isSelectMode.value) {
    selectedIds.value = []
  }
}

function toggleSelectAll() {
  if (isAllSelected.value) {
    selectedIds.value = []
  } else {
    selectedIds.value = conversations.value.map((c) => c.partnerId)
  }
}

function toggleSelect(partnerId: number) {
  const index = selectedIds.value.indexOf(partnerId)
  if (index === -1) {
    selectedIds.value.push(partnerId)
  } else {
    selectedIds.value.splice(index, 1)
  }
}

async function handleBatchDelete() {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先选择要删除的会话')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 个会话吗？删除后无法恢复！`, '批量删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await Promise.all(selectedIds.value.map((id) => deleteConversation(id)))
    conversations.value = conversations.value.filter((c) => !selectedIds.value.includes(c.partnerId))
    selectedIds.value = []
    ElMessage.success('删除成功')
  } catch {
    // 用户取消
  }
}

function formatLastMessage(message: string, messageType: string, recalled: boolean): string {
  if (recalled) {
    return '消息已撤回'
  }
  if (messageType === 'IMAGE') {
    return '[图片]'
  }
  if (messageType === 'SHARE') {
    try {
      const data = JSON.parse(message)
      if (data.postId && data.title) {
        return `[分享] ${data.title}`
      }
    } catch {
      // ignore
    }
  }
  return message
}

onMounted(() => loadConversations())
</script>

<template>
  <div class="messages-page">
    <div class="page-header">
      <el-button text @click="router.back()">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
      <h2>私信</h2>
      <div class="header-actions">
        <template v-if="isSelectMode">
          <el-button text type="primary" @click="toggleSelectAll">
            <el-icon><Check /></el-icon>
            {{ isAllSelected ? '取消全选' : '全选' }}
          </el-button>
          <el-button text type="danger" @click="handleBatchDelete" :disabled="selectedIds.length === 0">
            删除({{ selectedIds.length }})
          </el-button>
          <el-button text @click="toggleSelectMode">
            <el-icon><Close /></el-icon>
            取消
          </el-button>
        </template>
        <template v-else>
          <el-button text type="warning" @click="toggleSelectMode">
            <el-icon><Check /></el-icon>
            多选
          </el-button>
        </template>
      </div>
    </div>

    <div class="conversation-list" v-loading="loading">
      <div
        v-for="c in conversations"
        :key="c.partnerId"
        class="conversation-item"
        :class="{ 'has-unread': c.unreadCount > 0, selected: selectedIds.includes(c.partnerId), 'select-mode': isSelectMode }"
        @click="openChat(c.partnerId)"
      >
        <div v-if="isSelectMode" class="select-checkbox" :class="{ checked: selectedIds.includes(c.partnerId) }">
          <el-icon v-if="selectedIds.includes(c.partnerId)"><Check /></el-icon>
        </div>
        <el-avatar :size="48" :src="c.partnerAvatar || undefined">
          {{ c.partnerName?.charAt(0) }}
        </el-avatar>
        <div class="conversation-body">
          <div class="conversation-top">
            <span class="partner-name">{{ c.partnerName }}</span>
            <span class="last-time">{{ formatRelativeTime(c.lastMessageTime) }}</span>
          </div>
          <div class="conversation-bottom">
            <span class="last-message" :class="{ recalled: c.lastMessageRecalled }">{{ formatLastMessage(c.lastMessage, c.lastMessageType, c.lastMessageRecalled) }}</span>
            <el-badge v-if="c.unreadCount > 0 && !isSelectMode" :value="c.unreadCount" :max="99" />
          </div>
        </div>
      </div>

      <el-empty v-if="!loading && conversations.length === 0" description="暂无私信">
        <template #image>
          <el-icon :size="60" color="#c0c4cc"><ChatDotRound /></el-icon>
        </template>
      </el-empty>
    </div>
  </div>
</template>

<style scoped>
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.messages-page {
  max-width: 700px;
  margin: 0 auto;
  animation: fadeInUp 0.4s ease-out;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  gap: 16px;
}

.page-header h2 {
  flex: 1;
  text-align: center;
  font-weight: 700;
  font-size: 22px;
}

.header-actions {
  display: flex;
  gap: 4px;
}

.conversation-list {
  min-height: 200px;
}

.conversation-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  margin-bottom: 10px;
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  transition: transform 0.25s ease, box-shadow 0.25s ease;
  position: relative;
  overflow: hidden;
}

.conversation-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: transparent;
  border-radius: 4px 0 0 4px;
  transition: background 0.25s ease;
}

.conversation-item.has-unread::before {
  background: var(--primary);
}

.conversation-item:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.conversation-item.selected {
  background: var(--primary-bg);
  border: 1px solid var(--primary);
}

.conversation-item.select-mode::before {
  width: 0;
}

.select-checkbox {
  width: 22px;
  height: 22px;
  border: 2px solid var(--border-color);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.2s;
}

.select-checkbox.checked {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
}

.conversation-body {
  flex: 1;
  min-width: 0;
}

.conversation-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.partner-name {
  font-weight: 600;
  font-size: 16px;
  color: var(--text-primary);
}

.last-time {
  font-size: 12px;
  color: var(--text-muted);
  flex-shrink: 0;
}

.conversation-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.last-message {
  font-size: 13px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.last-message.recalled {
  font-style: italic;
  color: var(--text-muted);
}
</style>

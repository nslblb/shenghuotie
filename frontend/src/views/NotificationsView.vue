<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getNotifications, markAllRead, markAsRead, deleteNotifications, deleteAllNotifications, getUnreadCountByType } from '@/api/notification'
import type { NotificationVO, PageData } from '@/types'
import { formatRelativeTime } from '@/utils/date'
import { subscribe, unsubscribe } from '@/utils/websocket'
import type { IMessage } from '@stomp/stompjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Delete, Check, Close } from '@element-plus/icons-vue'

const router = useRouter()
const notifications = ref<NotificationVO[]>([])
const loading = ref(false)
const currentPage = ref(0)
const totalElements = ref(0)
const activeTab = ref('')
const isSelectMode = ref(false)
const selectedIds = ref<number[]>([])
const unreadCounts = ref<Record<string, number>>({})

const tabs = [
  { label: '全部', value: '' },
  { label: '点赞', value: 'LIKE' },
  { label: '评论', value: 'COMMENT' },
  { label: '关注', value: 'FOLLOW' },
  { label: '举报', value: 'REPORT' },
  { label: '申诉', value: 'APPEAL' },
  { label: '系统', value: 'SYSTEM' },
]

const isAllSelected = computed(() => {
  return notifications.value.length > 0 && selectedIds.value.length === notifications.value.length
})

const isIndeterminate = computed(() => {
  return selectedIds.value.length > 0 && selectedIds.value.length < notifications.value.length
})

function getTabUnreadCount(tabValue: string): number {
  return unreadCounts.value[tabValue] || 0
}

async function loadUnreadCounts() {
  try {
    const res = await getUnreadCountByType()
    unreadCounts.value = res.data.data
  } catch {
    // ignore
  }
}

async function loadNotifications(page = 0) {
  loading.value = true
  try {
    const params: { page: number; size: number; type?: string } = { page, size: 20 }
    if (activeTab.value) params.type = activeTab.value
    const res = await getNotifications(params)
    const data: PageData<NotificationVO> = res.data.data
    if (page === 0) {
      notifications.value = data.content
    } else {
      notifications.value.push(...data.content)
    }
    currentPage.value = data.number
    totalElements.value = data.totalElements
  } finally {
    loading.value = false
  }
}

async function handleMarkAllRead() {
  try {
    await markAllRead()
    notifications.value.forEach((n) => (n.isRead = true))
    unreadCounts.value = {}
    window.dispatchEvent(new CustomEvent('notifications-refreshed'))
    ElMessage.success('已全部标为已读')
  } catch {
    // 错误由拦截器处理
  }
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
    selectedIds.value = notifications.value.map((n) => n.id)
  }
}

function toggleSelect(id: number) {
  const index = selectedIds.value.indexOf(id)
  if (index === -1) {
    selectedIds.value.push(id)
  } else {
    selectedIds.value.splice(index, 1)
  }
}

async function handleBatchDelete() {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先选择要删除的通知')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条通知吗？`, '批量删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteNotifications(selectedIds.value)
    notifications.value = notifications.value.filter((n) => !selectedIds.value.includes(n.id))
    totalElements.value -= selectedIds.value.length
    selectedIds.value = []
    loadUnreadCounts()
    window.dispatchEvent(new CustomEvent('notifications-refreshed'))
    ElMessage.success('删除成功')
  } catch {
    // 用户取消或错误
  }
}

async function handleDeleteAll() {
  try {
    await ElMessageBox.confirm('确定清空所有通知吗？此操作不可恢复！', '清空通知', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteAllNotifications()
    notifications.value = []
    totalElements.value = 0
    isSelectMode.value = false
    selectedIds.value = []
    unreadCounts.value = {}
    window.dispatchEvent(new CustomEvent('notifications-refreshed'))
    ElMessage.success('已清空所有通知')
  } catch {
    // 用户取消或错误
  }
}

function getTypeText(type: string) {
  const map: Record<string, string> = { LIKE: '赞了', COMMENT: '评论了', REPLY: '回复了', FOLLOW: '关注了', REPORT: '举报了', APPEAL: '申诉结果', SYSTEM: '' }
  return map[type] || type
}

function getTypeColor(type: string) {
  const map: Record<string, string> = { LIKE: '#f56c6c', COMMENT: '#409eff', REPLY: '#e6a23c', FOLLOW: '#67c23a', REPORT: '#e6a23c', APPEAL: '#9b59b6', SYSTEM: '#909399' }
  return map[type] || '#909399'
}

async function goTarget(n: NotificationVO) {
  if (isSelectMode.value) {
    toggleSelect(n.id)
    return
  }
  // 用户正在选中文本（复制操作），不触发跳转
  const selection = window.getSelection()
  if (selection && selection.toString().length > 0) return

  if (!n.isRead) {
    try {
      await markAsRead(n.id)
      n.isRead = true
      loadUnreadCounts()
      window.dispatchEvent(new CustomEvent('notifications-refreshed'))
    } catch {
      // ignore
    }
  }
  if (n.type === 'SYSTEM') {
    return
  }
  if (n.postId) {
    router.push(`/post/${n.postId}`)
  } else if (n.type === 'FOLLOW') {
    router.push(`/profile/${n.senderId}`)
  }
}

watch(activeTab, () => {
  selectedIds.value = []
  loadNotifications(0)
})

function onNotificationMessage(message: IMessage) {
  try {
    const data = JSON.parse(message.body)
    if (data.notification) {
      const n = data.notification as NotificationVO
      // 当前 tab 筛选：空字符串表示全部，否则只插入匹配类型的
      if (!activeTab.value || n.type === activeTab.value) {
        notifications.value.unshift(n)
        totalElements.value++
      }
      // 更新未读数
      loadUnreadCounts()
    }
    if (typeof data.unreadCount === 'number') {
      // NavBar 会自行处理 unreadCount
    }
  } catch {
    // ignore
  }
}

onMounted(() => {
  loadNotifications()
  loadUnreadCounts()
  subscribe('notifications-page', '/user/topic/notifications', onNotificationMessage)
})

onBeforeUnmount(() => {
  unsubscribe('notifications-page')
})
</script>

<template>
  <div class="notifications-page">
    <div class="page-header">
      <el-button text @click="router.back()">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
      <h2>消息通知</h2>
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
          <el-button text type="primary" @click="handleMarkAllRead">全部已读</el-button>
          <el-button text type="warning" @click="toggleSelectMode">
            <el-icon><Check /></el-icon>
            多选
          </el-button>
          <el-button text type="danger" @click="handleDeleteAll">
            <el-icon><Delete /></el-icon>
            清空
          </el-button>
        </template>
      </div>
    </div>

    <div class="notification-tabs">
      <div
        v-for="tab in tabs"
        :key="tab.value"
        class="tab-item"
        :class="{ active: activeTab === tab.value }"
        @click="activeTab = tab.value"
      >
        {{ tab.label }}
        <span v-if="getTabUnreadCount(tab.value) > 0" class="tab-badge">
          {{ getTabUnreadCount(tab.value) > 99 ? '99+' : getTabUnreadCount(tab.value) }}
        </span>
      </div>
    </div>

    <div class="notification-list" v-loading="loading">
      <div
        v-for="n in notifications"
        :key="n.id"
        class="notification-item"
        :class="{ unread: !n.isRead, selected: selectedIds.includes(n.id), 'select-mode': isSelectMode }"
        :style="{ '--type-color': getTypeColor(n.type) }"
        @click="goTarget(n)"
      >
        <div v-if="isSelectMode" class="select-checkbox" :class="{ checked: selectedIds.includes(n.id) }">
          <el-icon v-if="selectedIds.includes(n.id)"><Check /></el-icon>
        </div>
        <el-avatar :size="40" :src="n.senderAvatar || undefined">
          {{ n.senderName?.charAt(0) }}
        </el-avatar>
        <div class="notification-body">
          <div class="notification-text">
            <strong v-if="n.type === 'SYSTEM'">系统通知 </strong>
            {{ n.content }}
          </div>
          <div class="notification-time">{{ formatRelativeTime(n.createdAt) }}</div>
        </div>
        <div v-if="!n.isRead && !isSelectMode" class="unread-dot"></div>
      </div>

      <el-empty v-if="!loading && notifications.length === 0" description="暂无通知，去逛逛帖子吧" />
    </div>

    <div v-if="currentPage + 1 < Math.ceil(totalElements / 20)" class="load-more">
      <el-button @click="loadNotifications(currentPage + 1)" :loading="loading">加载更多</el-button>
    </div>
  </div>
</template>

<style scoped>
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.5);
  }
  70% {
    box-shadow: 0 0 0 6px rgba(245, 108, 108, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(245, 108, 108, 0);
  }
}

.notifications-page {
  max-width: 700px;
  margin: 0 auto;
  animation: fadeInUp 0.4s ease-out;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 16px;
}

.page-header h2 {
  flex: 1;
  text-align: center;
  font-weight: 700;
}

.header-actions {
  display: flex;
  gap: 4px;
}

.notification-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 16px;
  background: var(--bg-card-hover);
  border-radius: 10px;
  padding: 6px;
}

.tab-item {
  text-align: center;
  padding: 8px 14px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
  transition: all 0.2s;
  user-select: none;
  position: relative;
}

.tab-item:hover {
  color: var(--text-primary);
  background: var(--bg-card);
}

.tab-item.active {
  background: var(--bg-card);
  color: var(--primary);
  font-weight: 600;
  box-shadow: var(--shadow-sm);
}

.tab-badge {
  position: absolute;
  top: 2px;
  right: 2px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  font-size: 10px;
  font-weight: 600;
  color: #fff;
  background: var(--primary);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: translate(50%, -50%);
}

.notification-list {
  min-height: 200px;
}

.notification-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.25s ease;
  position: relative;
  overflow: hidden;
}

.notification-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: var(--type-color, #909399);
  border-radius: 4px 0 0 4px;
  transition: width 0.2s ease;
}

.notification-item:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

.notification-item.unread {
  background: var(--primary-bg);
}

.notification-item.unread::before {
  width: 4px;
  background: var(--type-color, var(--primary));
  box-shadow: 0 0 6px var(--type-color, var(--primary));
}

.notification-item.selected {
  background: var(--primary-bg);
  border: 1px solid var(--primary);
}

.notification-item.select-mode::before {
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

.notification-body {
  flex: 1;
}

.notification-text {
  font-size: 14px;
  line-height: 1.6;
}

.notification-time {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}

.unread-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--primary);
  flex-shrink: 0;
  animation: pulse 2s infinite;
}

.load-more {
  text-align: center;
  margin-top: 16px;
}

.load-more :deep(.el-button) {
  border-radius: 20px;
}
</style>

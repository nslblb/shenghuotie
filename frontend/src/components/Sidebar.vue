<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { adminState } from '@/stores/admin'
import { ElMessage } from 'element-plus'
import {
  HomeFilled,
  Star,
  User,
  Collection,
  Document,
  EditPen,
  SwitchButton,
} from '@element-plus/icons-vue'
import { disconnectWebSocket } from '@/utils/websocket'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const isLoggedIn = computed(() => userStore.isLoggedIn)
const isAdmin = computed(() => userStore.userInfo?.role === 'ADMIN')
const userId = computed(() => userStore.userInfo?.id)
const showAdminPanel = computed(() => adminState.showAdminPanel)

const navItems = [
  { icon: 'HomeFilled', label: '推荐', path: '/' },
  { icon: 'Star', label: '点赞', path: '/likes' },
  { icon: 'User', label: '关注', path: '/follows' },
  { icon: 'Collection', label: '收藏', path: '/favorites' },
  { icon: 'Document', label: '我的帖子', path: '/my-posts' },
  { icon: 'EditPen', label: '草稿箱', path: '/drafts' },
]

function isActive(path: string) {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}

function navigateTo(item: (typeof navItems)[number]) {
  if (!item.path) return
  router.push(item.path)
}

function handleLogout() {
  disconnectWebSocket()
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/')
}

function handleOpenAdmin() {
  adminState.showAdminPanel = true
  router.push('/admin')
}
</script>

<template>
  <aside class="sidebar" v-if="isLoggedIn && !showAdminPanel">
    <div class="sidebar-nav">
      <div
        v-for="item in navItems"
        :key="item.label"
        class="sidebar-item"
        :class="{ active: isActive(item.path) }"
        @click="navigateTo(item)"
        :title="item.label"
      >
        <el-icon :size="22">
          <component :is="item.icon" />
        </el-icon>
        <span class="sidebar-label">{{ item.label }}</span>
      </div>
    </div>

    <div class="sidebar-bottom">
      <div v-if="isAdmin" class="sidebar-item admin-entry" @click="handleOpenAdmin">
        <el-icon :size="20"><EditPen /></el-icon>
        <span class="sidebar-label">管理后台</span>
      </div>

      <div class="sidebar-item logout-btn" @click="handleLogout" title="退出登录">
        <el-icon :size="20"><SwitchButton /></el-icon>
        <span class="sidebar-label">退出登录</span>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  position: fixed;
  top: 64px;
  left: 0;
  bottom: 0;
  width: 108px;
  background: var(--navbar-bg);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 16px 0;
  z-index: 99;
  box-sizing: border-box;
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-width: thin;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.sidebar.hidden {
  display: none;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 0 8px;
}

.sidebar-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  padding: 12px 4px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.2s ease;
  color: var(--text-secondary);
  user-select: none;
}

.sidebar-item:hover {
  background: var(--bg-card-hover);
  color: var(--text-primary);
}

.sidebar-item.active {
  background: var(--primary-bg);
  color: var(--primary);
  font-weight: 600;
}

.sidebar-item.active::before {
  content: '';
  position: absolute;
  left: -8px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 26px;
  border-radius: 0 2px 2px 0;
  background: var(--primary);
}

.sidebar-label {
  font-size: 12px;
  font-weight: 500;
  line-height: 1.2;
  text-align: center;
  white-space: nowrap;
}

.sidebar-bottom {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 0 0;
  margin: 0 8px;
  border-top: 1px solid var(--border-color);
}

.admin-entry:hover {
  background: var(--bg-card-hover);
  color: var(--text-primary);
}

.logout-btn:hover {
  color: #f56c6c;
  background: #fef0f0;
}
</style>

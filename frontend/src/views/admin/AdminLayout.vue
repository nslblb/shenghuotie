<script setup lang="ts">
import { computed, ref, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { adminState } from '@/stores/admin'
import { getToken } from '@/utils/auth'
import { Loading, Back } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loading = ref(true)

const isAdmin = computed(() => userStore.userInfo?.role === 'ADMIN')
const activeMenu = computed(() => route.path)

watch(() => route.path, (newPath) => {
  if (!newPath.startsWith('/admin')) {
    adminState.showAdminPanel = false
  } else {
    adminState.showAdminPanel = true
  }
})

onMounted(() => {
  if (!getToken()) {
    router.push('/admin/login')
    return
  }
  
  loading.value = false
  adminState.showAdminPanel = true
})

function handleLogout() {
  userStore.logout()
  router.push('/')
}

function handleExitAdmin() {
  adminState.showAdminPanel = false
  router.push('/')
}
</script>

<template>
  <div v-if="loading" class="loading-container">
    <el-icon class="is-loading" :size="40"><Loading /></el-icon>
    <p>加载中...</p>
  </div>
  <div class="admin-layout" v-else-if="isAdmin">
    <aside class="admin-sidebar">
      <div class="admin-menu-container">
        <el-menu :default-active="activeMenu" router background-color="#304156" text-color="#bfcbd9" active-text-color="#409eff" class="admin-menu" style="border-right: none;">
          <el-menu-item index="/admin/dashboard">
            <el-icon :size="18"><DataAnalysis /></el-icon>
            <span class="menu-text">仪表盘</span>
          </el-menu-item>
          <el-menu-item index="/admin/users">
            <el-icon :size="18"><User /></el-icon>
            <span class="menu-text">用户</span>
          </el-menu-item>
          <el-menu-item index="/admin/posts">
            <el-icon :size="18"><Document /></el-icon>
            <span class="menu-text">帖子</span>
          </el-menu-item>
          <el-menu-item index="/admin/comments">
            <el-icon :size="18"><ChatLineRound /></el-icon>
            <span class="menu-text">评论</span>
          </el-menu-item>
          <el-menu-item index="/admin/reports">
            <el-icon :size="18"><Warning /></el-icon>
            <span class="menu-text">举报</span>
          </el-menu-item>
          <el-menu-item index="/admin/appeals">
            <el-icon :size="18"><ChatDotRound /></el-icon>
            <span class="menu-text">申诉</span>
          </el-menu-item>
          <el-menu-item index="/admin/topics">
            <el-icon :size="18"><PriceTag /></el-icon>
            <span class="menu-text">话题</span>
          </el-menu-item>
          <el-menu-item index="/admin/sensitive-words">
            <el-icon :size="18"><WarningFilled /></el-icon>
            <span class="menu-text">敏感词</span>
          </el-menu-item>
          <el-menu-item index="/admin/knowledge">
            <el-icon :size="18"><Collection /></el-icon>
            <span class="menu-text">知识库</span>
          </el-menu-item>
          <el-menu-item index="/admin/logs">
            <el-icon :size="18"><List /></el-icon>
            <span class="menu-text">日志</span>
          </el-menu-item>
          <el-menu-item index="/admin/settings">
            <el-icon :size="18"><Setting /></el-icon>
            <span class="menu-text">设置</span>
          </el-menu-item>
        </el-menu>
      </div>
      <div class="admin-exit-btn" @click="handleExitAdmin">
        <el-icon><Back /></el-icon>
        <span>退出后台</span>
      </div>
    </aside>
    <main class="admin-main">
      <RouterView />
    </main>
  </div>
  <div v-else style="text-align: center; padding: 100px">
    <h2>无权限访问</h2>
    <p>需要管理员权限</p>
    <el-button type="primary" @click="router.push('/')">返回首页</el-button>
  </div>
</template>

<style scoped>
.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  color: #409eff;
}

.loading-container p {
  margin-top: 16px;
  font-size: 14px;
  color: #606266;
}

.admin-layout {
  display: flex;
  flex-direction: column;
  min-height: 590px;
  margin: 5px;
  padding: 5px;
}

.admin-sidebar {
  width: 108px;
  background: #304156;
  display: flex;
  flex-direction: column;
  position: fixed;
  top: 64px;
  left: 0;
  bottom: 0;
  z-index: 200;
}

.admin-menu-container {
  flex: 1;
  overflow-y: auto;
}

.admin-menu-container::-webkit-scrollbar {
  width: 4px;
}

.admin-menu-container::-webkit-scrollbar-thumb {
  background: #4a5f7f;
  border-radius: 2px;
}

.admin-exit-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 12px 4px;
  color: #bfcbd9;
  background: #263445;
  cursor: pointer;
  transition: all 0.2s;
  border-top: 1px solid #3a4d5e;
}

.admin-exit-btn:hover {
  color: #409eff;
  background: #2a3d52;
}

.admin-exit-btn span {
  font-size: 10px;
  white-space: nowrap;
}

.admin-sidebar :deep(.el-menu) {
  border-right: none;
  padding-top: 8px;
}

.admin-sidebar :deep(.el-menu-item) {
  height: 38px;
  line-height: 38px;
  padding: 0 6px !important;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  margin: 0 3px;
  border-radius: 8px;
}

.menu-text {
  font-size: 12px;
  line-height: 1;
  white-space: nowrap;
}

.admin-main {
  width: 100%;
  margin: 0;
  padding: 0;
  background: #f0f2f5;
  min-height: calc(100vh - 200px);
  box-sizing: border-box;
}
</style>

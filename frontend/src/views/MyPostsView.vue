<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { getUserPosts, batchDeletePosts, appealPost } from '@/api/post'
import type { PostVO } from '@/types'
import PostCard from '@/components/PostCard.vue'
import { ElMessageBox, ElMessage } from 'element-plus'

defineOptions({ name: 'my-posts' })

const userStore = useUserStore()
const posts = ref<PostVO[]>([])
const loading = ref(false)
const currentPage = ref(0)
const totalPages = ref(0)
const isSelectMode = ref(false)
const selectedIds = ref<Set<number>>(new Set())
const deleting = ref(false)

const selectedCount = computed(() => selectedIds.value.size)
const isAllSelected = computed(() => posts.value.length > 0 && selectedIds.value.size === posts.value.length)

async function loadPosts(page = 0) {
  if (!userStore.userInfo?.id) return
  loading.value = true
  try {
    const res = await getUserPosts(userStore.userInfo.id, { page, size: 20 })
    const data = res.data.data
    if (page === 0) {
      posts.value = data.content
    } else {
      posts.value.push(...data.content)
    }
    currentPage.value = data.number
    totalPages.value = data.totalPages
  } finally {
    loading.value = false
  }
}

function loadMore() {
  if (currentPage.value + 1 < totalPages.value) {
    loadPosts(currentPage.value + 1)
  }
}

function enterSelectMode() {
  isSelectMode.value = true
  selectedIds.value.clear()
}

function exitSelectMode() {
  isSelectMode.value = false
  selectedIds.value.clear()
}

function toggleSelect(postId: number) {
  if (selectedIds.value.has(postId)) {
    selectedIds.value.delete(postId)
  } else {
    selectedIds.value.add(postId)
  }
}

function toggleSelectAll() {
  if (isAllSelected.value) {
    selectedIds.value.clear()
  } else {
    posts.value.forEach(p => selectedIds.value.add(p.id))
  }
}

async function handleBatchDelete() {
  if (selectedIds.value.size === 0) {
    ElMessage.warning('请先选择要删除的帖子')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedIds.value.size} 篇帖子吗？此操作不可撤销。`,
      '批量删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  deleting.value = true
  try {
    await batchDeletePosts(Array.from(selectedIds.value))
    ElMessage.success(`成功删除 ${selectedIds.value.size} 篇帖子`)
    exitSelectMode()
    loadPosts(0)
  } catch {
    ElMessage.error('删除失败')
  } finally {
    deleting.value = false
  }
}

async function handleAppeal(post: PostVO) {
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入申诉理由，管理员将审核您的请求。',
      '请求解封',
      {
        confirmButtonText: '提交请求',
        cancelButtonText: '取消',
        inputPlaceholder: '请输入申诉理由',
        inputType: 'textarea',
        inputValidator: (val) => {
          if (!val || val.trim().length === 0) {
            return '请输入申诉理由'
          }
          if (val.length > 500) {
            return '申诉理由不能超过500字'
          }
          return true
        }
      }
    )
    try {
      await appealPost(post.id, value.trim())
      ElMessage.success('解封请求已提交，请等待管理员审核')
      loadPosts(0)
    } catch {
      ElMessage.error('提交失败')
    }
  } catch {
    // 用户取消
  }
}

function handleCardClick(post: PostVO) {
  if (isSelectMode.value) {
    toggleSelect(post.id)
  }
}

function getStatusLabel(status: string) {
  if (status === 'DISABLED') return '已封禁'
  if (status === 'APPEALING') return '申诉中'
  return ''
}

onMounted(() => loadPosts())
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">我的帖子</h2>
      <div class="header-actions">
        <template v-if="isSelectMode">
          <el-button @click="exitSelectMode">取消</el-button>
          <el-button
            type="danger"
            :disabled="selectedCount === 0"
            :loading="deleting"
            @click="handleBatchDelete"
          >
            删除选中{{ selectedCount > 0 ? `(${selectedCount})` : '' }}
          </el-button>
        </template>
        <template v-else>
          <el-button type="primary" @click="enterSelectMode">
            <el-icon><Delete /></el-icon>
            批量删除
          </el-button>
        </template>
      </div>
    </div>

    <div v-if="isSelectMode" class="select-bar">
      <el-checkbox :model-value="isAllSelected" @change="toggleSelectAll">
        全选
      </el-checkbox>
      <span class="select-info">已选择 {{ selectedCount }} 篇帖子</span>
    </div>

    <div class="post-grid" v-loading="loading">
      <div
        v-for="post in posts"
        :key="post.id"
        class="post-card-wrapper"
        :class="{
          'is-selected': selectedIds.has(post.id),
          'select-mode': isSelectMode,
          'is-disabled': post.status === 'DISABLED',
          'is-appealing': post.status === 'APPEALING'
        }"
        @click="handleCardClick(post)"
      >
        <div v-if="isSelectMode" class="select-checkbox">
          <el-checkbox :model-value="selectedIds.has(post.id)" />
        </div>
        <div v-if="isSelectMode" class="select-overlay" @click.stop="toggleSelect(post.id)"></div>
        <div v-if="post.status === 'DISABLED' || post.status === 'APPEALING'" class="disabled-overlay">
          <div class="disabled-info">
            <el-tag :type="post.status === 'DISABLED' ? 'danger' : 'warning'" size="small">
              {{ getStatusLabel(post.status) }}
            </el-tag>
            <el-button
              v-if="post.status === 'DISABLED'"
              type="warning"
              size="small"
              round
              @click.stop="handleAppeal(post)"
            >
              请求解封
            </el-button>
            <span v-else class="appealing-hint">等待管理员审核</span>
          </div>
        </div>
        <PostCard :post="post" />
      </div>
      <el-empty v-if="!loading && posts.length === 0" description="还没有发布过帖子">
        <template #image>
          <el-icon :size="60" color="#c0c4cc"><Document /></el-icon>
        </template>
      </el-empty>
    </div>
    <div class="load-more" v-if="currentPage + 1 < totalPages">
      <el-button @click="loadMore" :loading="loading">加载更多</el-button>
    </div>
  </div>
</template>

<script lang="ts">
import { Delete, Document } from '@element-plus/icons-vue'
export default { components: { Delete, Document } }
</script>

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

.page-container {
  max-width: 1200px;
  margin: 0 auto;
  animation: fadeInUp 0.5s ease both;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  margin: 0;
  color: #1a1a1a;
  position: relative;
  display: inline-block;
}

.page-title::after {
  content: '';
  position: absolute;
  bottom: -4px;
  left: 0;
  width: 100%;
  height: 3px;
  background: #ff6b81;
  border-radius: 2px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.select-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 16px;
  background: #fff;
  backdrop-filter: blur(12px);
  border-radius: 12px;
  margin-bottom: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  border: 1px solid rgba(0, 0, 0, 0.04);
}

.select-info {
  font-size: 14px;
  color: var(--text-secondary);
}

.post-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
  min-height: 200px;
}

.post-card-wrapper {
  position: relative;
  transition: all 0.3s ease;
  display: flex;
}

.post-card-wrapper :deep(.post-card) {
  margin-bottom: 0;
  width: 100%;
  flex: 1;
}

.post-card-wrapper.select-mode {
  cursor: pointer;
}

.select-overlay {
  position: absolute;
  inset: 0;
  z-index: 8;
  cursor: pointer;
}

.post-card-wrapper.is-selected {
  outline: 2px solid #ff2442;
  outline-offset: 2px;
  border-radius: 12px;
}

.post-card-wrapper.is-selected .select-checkbox :deep(.el-checkbox__inner) {
  background-color: #ff2442;
  border-color: #ff2442;
}

.post-card-wrapper.is-disabled {
  opacity: 0.75;
}

.post-card-wrapper.is-appealing {
  opacity: 0.85;
}

.disabled-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 5;
  padding: 8px 10px;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.5), transparent);
  border-radius: 12px 12px 0 0;
}

.disabled-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.appealing-hint {
  font-size: 12px;
  color: #e6a23c;
  font-weight: 500;
}

.select-checkbox {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 10;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 50%;
  padding: 2px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.load-more {
  text-align: center;
  margin-top: 24px;
}

.load-more :deep(.el-button) {
  border-radius: 20px;
}
</style>

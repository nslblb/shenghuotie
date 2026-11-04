<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  getAdminPosts,
  getAdminPostTopics,
  getAdminPostDetail,
  adminDeletePost,
  adminDisablePost,
  adminEnablePost,
  adminBatchDeletePosts,
  adminBatchDisablePosts,
  adminBatchEnablePosts,
  getAdminComments
} from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()

interface AdminPost {
  id: number
  userId: number
  authorName: string
  authorAvatar: string
  title: string
  content: string
  topic: string | null
  likeCount: number
  commentCount: number
  favoriteCount: number
  status: string
  images: string[]
  createdAt: string
}

const posts = ref<AdminPost[]>([])
const loading = ref(false)
const currentPage = ref(0)
const totalElements = ref(0)
const keyword = ref('')
const filterStatus = ref('')
const filterTopic = ref('')
const topics = ref<string[]>([])

const selectedPosts = ref<AdminPost[]>([])
const previewVisible = ref(false)
const currentPost = ref<AdminPost | null>(null)
const commentsVisible = ref(false)
const currentPostComments = ref<any[]>([])
const commentsLoading = ref(false)

const hasSelection = computed(() => selectedPosts.value.length > 0)

async function loadPosts(page = 0) {
  loading.value = true
  try {
    const res = await getAdminPosts({
      page,
      size: 20,
      keyword: keyword.value || undefined,
      status: filterStatus.value || undefined,
      topic: filterTopic.value || undefined
    })
    const data = res.data.data
    posts.value = data.content
    currentPage.value = data.number
    totalElements.value = data.totalElements
    selectedPosts.value = []
  } finally {
    loading.value = false
  }
}

async function loadTopics() {
  try {
    const res = await getAdminPostTopics()
    topics.value = res.data.data
  } catch {
    // 忽略错误
  }
}

async function showPostDetail(post: AdminPost) {
  previewVisible.value = true
  try {
    const res = await getAdminPostDetail(post.id)
    currentPost.value = res.data.data
  } catch {
    currentPost.value = post
  }
}

async function showPostComments(post: AdminPost) {
  commentsVisible.value = true
  commentsLoading.value = true
  currentPost.value = post
  try {
    const res = await getAdminComments({ postId: post.id, size: 50 })
    currentPostComments.value = res.data.data.content
  } catch {
    currentPostComments.value = []
  } finally {
    commentsLoading.value = false
  }
}

async function handleDelete(post: AdminPost) {
  await ElMessageBox.confirm(`确定删除帖子「${post.title}」吗？`, '提示', { type: 'warning' })
  try {
    await adminDeletePost(post.id)
    ElMessage.success('已删除')
    loadPosts(currentPage.value)
  } catch {
    // 错误由拦截器处理
  }
}

async function handleDisable(post: AdminPost) {
  await ElMessageBox.confirm(`确定禁用帖子「${post.title}」吗？`, '提示', { type: 'warning' })
  try {
    await adminDisablePost(post.id)
    ElMessage.success('已禁用')
    loadPosts(currentPage.value)
  } catch {
    // 错误由拦截器处理
  }
}

async function handleEnable(post: AdminPost) {
  await ElMessageBox.confirm(`确定解禁帖子「${post.title}」吗？`, '提示', { type: 'info' })
  try {
    await adminEnablePost(post.id)
    ElMessage.success('已解禁')
    loadPosts(currentPage.value)
  } catch {
    // 错误由拦截器处理
  }
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(`确定删除选中的 ${selectedPosts.value.length} 篇帖子吗？`, '提示', { type: 'warning' })
  try {
    await adminBatchDeletePosts(selectedPosts.value.map(p => p.id))
    ElMessage.success('已批量删除')
    loadPosts(currentPage.value)
  } catch {
    // 错误由拦截器处理
  }
}

async function handleBatchDisable() {
  await ElMessageBox.confirm(`确定禁用选中的 ${selectedPosts.value.length} 篇帖子吗？`, '提示', { type: 'warning' })
  try {
    await adminBatchDisablePosts(selectedPosts.value.map(p => p.id))
    ElMessage.success('已批量禁用')
    loadPosts(currentPage.value)
  } catch {
    // 错误由拦截器处理
  }
}

async function handleBatchEnable() {
  await ElMessageBox.confirm(`确定解禁选中的 ${selectedPosts.value.length} 篇帖子吗？`, '提示', { type: 'info' })
  try {
    await adminBatchEnablePosts(selectedPosts.value.map(p => p.id))
    ElMessage.success('已批量解禁')
    loadPosts(currentPage.value)
  } catch {
    // 错误由拦截器处理
  }
}

function goToProfile(userId: number) {
  router.push(`/profile/${userId}`)
}

function getStatusType(status: string) {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'DISABLED') return 'danger'
  if (status === 'APPEALING') return 'warning'
  if (status === 'DRAFT') return 'info'
  return 'info'
}

function getStatusLabel(status: string) {
  if (status === 'PUBLISHED') return '正常'
  if (status === 'DISABLED') return '已禁用'
  if (status === 'APPEALING') return '申诉中'
  if (status === 'DRAFT') return '草稿'
  return status
}

function handleSearch() {
  loadPosts(0)
}

function handleReset() {
  keyword.value = ''
  filterStatus.value = ''
  filterTopic.value = ''
  loadPosts(0)
}

function handlePageChange(page: number) {
  loadPosts(page)
}

function handleSelectionChange(selection: AdminPost[]) {
  selectedPosts.value = selection
}

onMounted(() => {
  loadPosts()
  loadTopics()
})
</script>

<template>
  <div class="posts-page">
    <h2 style="margin-bottom: 20px">帖子管理</h2>

    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索标题"
        style="width: 200px"
        clearable
        @clear="handleSearch"
        @keyup.enter="handleSearch"
      />
      <el-select v-model="filterStatus" placeholder="状态筛选" style="width: 120px" clearable>
        <el-option label="正常" value="PUBLISHED" />
        <el-option label="已禁用" value="DISABLED" />
        <el-option label="申诉中" value="APPEALING" />
        <el-option label="草稿" value="DRAFT" />
      </el-select>
      <el-select v-model="filterTopic" placeholder="话题筛选" style="width: 140px" clearable filterable>
        <el-option v-for="t in topics" :key="t" :label="t" :value="t" />
      </el-select>
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <div class="batch-actions" v-if="hasSelection">
      <span class="selection-info">已选择 {{ selectedPosts.length }} 篇帖子</span>
      <el-button type="danger" @click="handleBatchDelete">批量删除</el-button>
      <el-button type="warning" @click="handleBatchDisable">批量禁用</el-button>
      <el-button type="success" @click="handleBatchEnable">批量解禁</el-button>
    </div>

    <el-table
      :data="posts"
      v-loading="loading"
      stripe
      style="width: 100%"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="50" />
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="title" label="标题" show-overflow-tooltip min-width="160">
        <template #default="{ row }">
          <span class="post-title" @click="showPostDetail(row)">{{ row.title }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="topic" label="话题" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.topic" size="small" type="info">{{ row.topic }}</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="likeCount" label="点赞" width="70" />
      <el-table-column prop="commentCount" label="评论" width="70">
        <template #default="{ row }">
          <span class="comment-count" @click="showPostComments(row)">{{ row.commentCount }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="发布时间" width="160" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" text size="small" @click="showPostDetail(row)">
            预览
          </el-button>
          <el-button
            v-if="row.status === 'PUBLISHED'"
            type="warning"
            text
            size="small"
            @click="handleDisable(row)"
          >
            禁用
          </el-button>
          <el-button
            v-if="row.status === 'DISABLED' || row.status === 'APPEALING'"
            type="success"
            text
            size="small"
            @click="handleEnable(row)"
          >
            解禁
          </el-button>
          <el-button type="danger" text size="small" @click="handleDelete(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        background
        layout="prev, pager, next"
        :total="totalElements"
        :page-size="20"
        :current-page="currentPage + 1"
        @current-change="handlePageChange"
      />
    </div>

    <el-dialog v-model="previewVisible" title="帖子预览" width="700px">
      <template v-if="currentPost">
        <div class="preview-header">
          <h3>{{ currentPost.title }}</h3>
          <div class="preview-meta">
            <span>作者：{{ currentPost.authorName }}</span>
            <span>发布时间：{{ currentPost.createdAt }}</span>
            <el-tag :type="getStatusType(currentPost.status)" size="small">
              {{ getStatusLabel(currentPost.status) }}
            </el-tag>
          </div>
        </div>
        
        <div class="preview-images" v-if="currentPost.images && currentPost.images.length > 0">
          <el-carousel :interval="5000" height="300px" indicator-position="outside">
            <el-carousel-item v-for="(img, index) in currentPost.images" :key="index">
              <el-image :src="img" fit="contain" style="width: 100%; height: 100%" />
            </el-carousel-item>
          </el-carousel>
        </div>
        
        <div class="preview-content">
          <p>{{ currentPost.content }}</p>
        </div>
        
        <div class="preview-stats">
          <span>点赞：{{ currentPost.likeCount }}</span>
          <span>评论：{{ currentPost.commentCount }}</span>
          <span>收藏：{{ currentPost.favoriteCount }}</span>
        </div>
      </template>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button type="info" @click="goToProfile(currentPost!.userId); previewVisible = false">
          查看作者主页
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="commentsVisible" title="评论列表" width="700px">
      <div v-loading="commentsLoading">
        <div class="comments-header">
          <span>帖子：{{ currentPost?.title }}</span>
          <span>共 {{ currentPostComments.length }} 条评论</span>
        </div>
        
        <el-table :data="currentPostComments" max-height="400">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column label="评论者" width="120">
            <template #default="{ row }">
              {{ row.userNickname }}
            </template>
          </el-table-column>
          <el-table-column prop="content" label="内容" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="时间" width="160" />
        </el-table>
        
        <el-empty v-if="currentPostComments.length === 0" description="暂无评论" />
      </div>
      <template #footer>
        <el-button @click="commentsVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.batch-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
}

.selection-info {
  color: #606266;
  font-size: 14px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.post-title {
  cursor: pointer;
  color: #303133;
}

.post-title:hover {
  color: #409eff;
}

.comment-count {
  cursor: pointer;
  color: #409eff;
}

.preview-header {
  margin-bottom: 20px;
}

.preview-header h3 {
  margin: 0 0 10px 0;
  font-size: 18px;
}

.preview-meta {
  display: flex;
  gap: 16px;
  color: #909399;
  font-size: 14px;
}

.preview-images {
  margin-bottom: 20px;
  background: #f5f7fa;
  border-radius: 8px;
  overflow: hidden;
}

.preview-content {
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
  margin-bottom: 16px;
  max-height: 300px;
  overflow-y: auto;
}

.preview-content p {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.8;
}

.preview-stats {
  display: flex;
  gap: 24px;
  color: #606266;
  font-size: 14px;
}

.comments-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
  color: #606266;
}

:deep(.el-table .el-button + .el-button) {
  margin-left: 4px;
}
</style>

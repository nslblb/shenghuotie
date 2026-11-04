<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getAdminComments, adminDeleteComment, type CommentAdminVO } from '@/api/admin'
import type { PageData } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import DOMPurify from 'dompurify'

const comments = ref<CommentAdminVO[]>([])
const loading = ref(false)
const currentPage = ref(0)
const totalElements = ref(0)
const keyword = ref('')
const userId = ref<number | undefined>()
const postId = ref<number | undefined>()
const hasSensitiveWord = ref<boolean | undefined>()
const detailVisible = ref(false)
const currentComment = ref<CommentAdminVO | null>(null)

async function loadComments(page = 0) {
  loading.value = true
  try {
    const res = await getAdminComments({
      page,
      size: 20,
      keyword: keyword.value || undefined,
      userId: userId.value,
      postId: postId.value,
      hasSensitiveWord: hasSensitiveWord.value
    })
    const data: PageData<CommentAdminVO> = res.data.data
    comments.value = data.content
    currentPage.value = data.number
    totalElements.value = data.totalElements
  } finally {
    loading.value = false
  }
}

async function handleDelete(comment: CommentAdminVO) {
  await ElMessageBox.confirm(
    `确定删除该评论吗？${comment.parentId ? '该评论的子评论也会被删除。' : ''}`,
    '删除确认',
    { type: 'warning' }
  )
  try {
    await adminDeleteComment(comment.id)
    ElMessage.success('删除成功')
    loadComments(currentPage.value)
  } catch {
    // 错误由拦截器处理
  }
}

function handleSearch() {
  loadComments(0)
}

function handleReset() {
  keyword.value = ''
  userId.value = undefined
  postId.value = undefined
  hasSensitiveWord.value = undefined
  loadComments(0)
}

function handlePageChange(page: number) {
  loadComments(page)
}

function showDetail(comment: CommentAdminVO) {
  currentComment.value = comment
  detailVisible.value = true
}

function highlightSensitive(content: string, hasSensitive: boolean): string {
  const escaped = DOMPurify.sanitize(content, { ALLOWED_TAGS: [] })
  if (!hasSensitive) return escaped
  return `<span style="color: #f56c6c; font-weight: 500;">${escaped}</span>`
}

onMounted(() => loadComments())
</script>

<template>
  <div class="comments-page">
    <h2 style="margin-bottom: 20px">评论管理</h2>

    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索评论内容"
        style="width: 200px"
        clearable
        @clear="handleSearch"
        @keyup.enter="handleSearch"
      />
      <el-input
        v-model.number="userId"
        placeholder="用户ID"
        style="width: 120px"
        clearable
        type="number"
      />
      <el-input
        v-model.number="postId"
        placeholder="帖子ID"
        style="width: 120px"
        clearable
        type="number"
      />
      <el-select
        v-model="hasSensitiveWord"
        placeholder="敏感词筛选"
        style="width: 140px"
        clearable
      >
        <el-option label="含敏感词" :value="true" />
        <el-option label="无敏感词" :value="false" />
      </el-select>
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <el-table :data="comments" v-loading="loading" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="评论者" width="150">
        <template #default="{ row }">
          <div class="user-cell">
            <el-avatar :size="32" :src="row.userAvatar" />
            <span class="nickname">{{ row.userNickname || '未知用户' }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="内容" min-width="250">
        <template #default="{ row }">
          <div class="content-cell">
            <span
              v-html="highlightSensitive(row.content, row.hasSensitiveWord)"
              class="content-text"
            />
            <el-tag
              v-if="row.hasSensitiveWord"
              type="danger"
              size="small"
              style="margin-left: 8px"
            >
              敏感词
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="所属帖子" width="180">
        <template #default="{ row }">
          <span class="post-title">{{ row.postTitle || '帖子已删除' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="时间" width="170" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" text size="small" @click="showDetail(row)">
            详情
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

    <el-dialog v-model="detailVisible" title="评论详情" width="600px">
      <template v-if="currentComment">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="评论ID">
            {{ currentComment.id }}
          </el-descriptions-item>
          <el-descriptions-item label="评论者">
            {{ currentComment.userNickname }} (ID: {{ currentComment.userId }})
          </el-descriptions-item>
          <el-descriptions-item label="所属帖子">
            {{ currentComment.postTitle }} (ID: {{ currentComment.postId }})
          </el-descriptions-item>
          <el-descriptions-item v-if="currentComment.parentId" label="父评论">
            <div class="parent-comment">
              <span class="parent-label">回复：</span>
              <span class="parent-content">{{ currentComment.parentContent }}</span>
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="评论内容">
            <div
              v-html="highlightSensitive(currentComment.content, currentComment.hasSensitiveWord)"
              class="comment-content"
            />
          </el-descriptions-item>
          <el-descriptions-item label="敏感词标记">
            <el-tag :type="currentComment.hasSensitiveWord ? 'danger' : 'success'">
              {{ currentComment.hasSensitiveWord ? '含敏感词' : '正常' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="发布时间">
            {{ currentComment.createdAt }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          type="danger"
          @click="handleDelete(currentComment!); detailVisible = false"
        >
          删除评论
        </el-button>
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

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nickname {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.content-cell {
  display: flex;
  align-items: center;
}

.content-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 300px;
  display: inline-block;
}

.post-title {
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

.parent-comment {
  background: #f5f7fa;
  padding: 8px 12px;
  border-radius: 4px;
}

.parent-label {
  color: #909399;
  font-size: 12px;
}

.parent-content {
  color: #606266;
}

.comment-content {
  line-height: 1.6;
}

:deep(.sensitive-word) {
  color: #f56c6c;
  font-weight: 500;
  background: #fef0f0;
  padding: 0 4px;
  border-radius: 2px;
}
</style>

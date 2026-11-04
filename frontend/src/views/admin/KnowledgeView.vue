<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '@/api/index'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Result, PageData } from '@/types'

interface KnowledgeVO {
  id: number
  sourceType: string
  title: string
  content: string
  postId: number | null
  enabled: boolean
  createdAt: string
  updatedAt: string
}

interface PostRef {
  id: number
  title: string
}

const items = ref<KnowledgeVO[]>([])
const loading = ref(false)
const total = ref(0)
const currentPage = ref(0)
const pageSize = ref(20)

// 对话框
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const form = ref({
  id: 0,
  sourceType: 'MANUAL_TEXT',
  title: '',
  content: '',
  postId: null as number | null,
  enabled: true,
})

// 导入对话框
const importDialogVisible = ref(false)
const availablePosts = ref<PostRef[]>([])
const importLoading = ref(false)

// 重建索引
const reindexing = ref(false)

// ==================== 列表加载 ====================

async function loadItems() {
  loading.value = true
  try {
    const res = await request.get<Result<PageData<KnowledgeVO>>>('/admin/knowledge', {
      params: { page: currentPage.value, size: pageSize.value },
    })
    items.value = res.data.data.content
    total.value = res.data.data.totalElements
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  currentPage.value = page - 1
  loadItems()
}

// ==================== 创建 ====================

function openCreateDialog() {
  form.value = {
    id: 0,
    sourceType: 'MANUAL_TEXT',
    title: '',
    content: '',
    postId: null,
    enabled: true,
  }
  dialogMode.value = 'create'
  dialogVisible.value = true
}

// ==================== 编辑 ====================

function openEditDialog(item: KnowledgeVO) {
  form.value = {
    id: item.id,
    sourceType: item.sourceType,
    title: item.title,
    content: item.content,
    postId: item.postId,
    enabled: item.enabled,
  }
  dialogMode.value = 'edit'
  dialogVisible.value = true
}

// ==================== 提交表单 ====================

async function submitForm() {
  if (!form.value.title.trim()) {
    ElMessage.warning('请输入标题')
    return
  }
  if (!form.value.content.trim()) {
    ElMessage.warning('请输入内容')
    return
  }
  try {
    if (dialogMode.value === 'create') {
      await request.post<Result<KnowledgeVO>>('/admin/knowledge', null, {
        params: {
          sourceType: form.value.sourceType,
          title: form.value.title.trim(),
          content: form.value.content.trim(),
          postId: form.value.postId || undefined,
        },
      })
      ElMessage.success('创建成功')
    } else {
      await request.put<Result<KnowledgeVO>>(`/admin/knowledge/${form.value.id}`, null, {
        params: {
          title: form.value.title.trim(),
          content: form.value.content.trim(),
          enabled: form.value.enabled,
        },
      })
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    loadItems()
  } catch {
    // 错误由拦截器处理
  }
}

// ==================== 启用/禁用 ====================

async function handleToggleEnabled(item: KnowledgeVO) {
  try {
    await request.put<Result<KnowledgeVO>>(`/admin/knowledge/${item.id}/toggle`)
    ElMessage.success(item.enabled ? '已禁用' : '已启用')
    loadItems()
  } catch {
    // 错误由拦截器处理
  }
}

// ==================== 删除 ====================

async function handleDelete(item: KnowledgeVO) {
  await ElMessageBox.confirm(`确定删除「${item.title}」吗？`, '提示', { type: 'warning' })
  try {
    await request.delete<Result<void>>(`/admin/knowledge/${item.id}`)
    ElMessage.success('删除成功')
    loadItems()
  } catch {
    // 错误由拦截器处理
  }
}

// ==================== 从笔记导入 ====================

async function openImportDialog() {
  importDialogVisible.value = true
  importLoading.value = true
  try {
    const res = await request.get<Result<PostRef[]>>('/admin/knowledge/posts')
    availablePosts.value = res.data.data
  } catch {
    ElMessage.error('加载笔记列表失败')
  } finally {
    importLoading.value = false
  }
}

async function handleImportPost(postId: number) {
  try {
    await request.post<Result<KnowledgeVO>>('/admin/knowledge/import-post', null, {
      params: { postId },
    })
    ElMessage.success('导入成功')
    importDialogVisible.value = false
    loadItems()
  } catch {
    // 错误由拦截器处理
  }
}

// ==================== 全量重建索引 ====================

async function handleReindex() {
  await ElMessageBox.confirm('确定要全量重建 AI 知识库向量索引吗？这可能需要一些时间。', '确认重建', { type: 'warning' })
  reindexing.value = true
  try {
    const res = await request.post<Result<{ total: number; success: number; failed: number }>>('/admin/ai/reindex')
    const d = res.data.data
    ElMessageBox.alert(
      `重建完成！\n总条目：${d.total}\n成功：${d.success}\n失败：${d.failed}`,
      '重建结果',
      { type: d.failed > 0 ? 'warning' : 'success', confirmButtonText: '知道了' },
    )
    loadItems()
  } catch {
    // 错误由拦截器处理
  } finally {
    reindexing.value = false
  }
}

// ==================== 工具函数 ====================

function formatDate(date: string) {
  return new Date(date).toLocaleString()
}

function sourceTypeLabel(type: string) {
  return type === 'MANUAL_TEXT' ? '手动录入' : type === 'POST_REF' ? '笔记引用' : type
}

onMounted(() => loadItems())
</script>

<template>
  <div class="knowledge-view">
    <div class="page-header">
      <h2>知识库管理</h2>
      <div class="header-actions">
        <el-button @click="openImportDialog">从笔记导入</el-button>
        <el-button type="success" :loading="reindexing" @click="handleReindex">重建索引</el-button>
        <el-button type="primary" @click="openCreateDialog">新增条目</el-button>
      </div>
    </div>

    <el-table :data="items" v-loading="loading" stripe style="width: 100%; max-width: 100%;">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.sourceType === 'MANUAL_TEXT' ? '' : 'warning'">
            {{ sourceTypeLabel(row.sourceType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
      <el-table-column prop="content" label="内容" min-width="250" show-overflow-tooltip />
      <el-table-column prop="postId" label="来源笔记" width="100" align="center">
        <template #default="{ row }">
          <span v-if="row.postId">#{{ row.postId }}</span>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" link @click="openEditDialog(row)">编辑</el-button>
          <el-button
            :type="row.enabled ? 'warning' : 'success'"
            size="small"
            link
            @click="handleToggleEnabled(row)"
          >
            {{ row.enabled ? '禁用' : '启用' }}
          </el-button>
          <el-button type="danger" size="small" link @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper" v-if="total > pageSize">
      <el-pagination
        background
        layout="prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="currentPage + 1"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 创建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增知识库条目' : '编辑知识库条目'" width="600px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="类型" v-if="dialogMode === 'create'">
          <el-select v-model="form.sourceType" :disabled="dialogMode === 'edit'">
            <el-option label="手动录入" value="MANUAL_TEXT" />
            <el-option label="笔记引用" value="POST_REF" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="请输入标题" maxlength="200" />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="form.content" type="textarea" :rows="6" placeholder="请输入正文内容" />
        </el-form-item>
        <el-form-item label="笔记ID" v-if="form.sourceType === 'POST_REF' && dialogMode === 'create'">
          <el-input-number v-model="form.postId" :min="0" placeholder="关联笔记ID（可选）" />
        </el-form-item>
        <el-form-item label="状态" v-if="dialogMode === 'edit'">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 从笔记导入对话框 -->
    <el-dialog v-model="importDialogVisible" title="从笔记导入" width="500px">
      <div v-loading="importLoading">
        <div v-if="availablePosts.length === 0 && !importLoading" class="empty-hint">
          暂无可导入的已发布笔记
        </div>
        <div v-else class="post-list">
          <div
            v-for="post in availablePosts"
            :key="post.id"
            class="post-item"
            @click="handleImportPost(post.id)"
          >
            <span class="post-id">#{{ post.id }}</span>
            <span class="post-title">{{ post.title }}</span>
            <el-icon class="import-arrow"><Right /></el-icon>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="importDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.knowledge-view {
  padding: 0 16px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.text-muted {
  color: #999;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

.empty-hint {
  text-align: center;
  padding: 24px;
  color: #999;
}

.post-list {
  max-height: 400px;
  overflow-y: auto;
}

.post-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.post-item:hover {
  background: #f5f7fa;
}

.post-id {
  color: #909399;
  font-size: 13px;
  flex-shrink: 0;
}

.post-title {
  flex: 1;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.import-arrow {
  color: #c0c4cc;
  flex-shrink: 0;
}
</style>

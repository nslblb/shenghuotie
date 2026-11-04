<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  getSensitiveWords,
  createSensitiveWord,
  updateSensitiveWord,
  deleteSensitiveWord,
  batchDeleteSensitiveWords,
  refreshSensitiveWordCache,
  type SensitiveWordVO
} from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const words = ref<SensitiveWordVO[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(20)
const selectedIds = ref<number[]>([])

const searchKeyword = ref('')
const searchCategory = ref('')
const searchEnabled = ref<boolean | ''>('')

const categories = ['政治', '色情', '暴力', '赌博', '毒品', '广告', '辱骂', '其他']

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const form = ref({
  id: 0,
  word: '',
  category: ''
})

async function loadWords() {
  loading.value = true
  try {
    const params: Record<string, any> = {
      page: page.value - 1,
      size: size.value
    }
    if (searchKeyword.value) params.keyword = searchKeyword.value
    if (searchCategory.value) params.category = searchCategory.value
    if (searchEnabled.value !== '') params.enabled = searchEnabled.value

    const res = await getSensitiveWords(params as any)
    words.value = res.data.data.content
    total.value = res.data.data.totalElements
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadWords()
}

function handlePageChange(val: number) {
  page.value = val
  loadWords()
}

function handleSizeChange(val: number) {
  size.value = val
  page.value = 1
  loadWords()
}

function openCreateDialog() {
  form.value = { id: 0, word: '', category: '' }
  dialogMode.value = 'create'
  dialogVisible.value = true
}

function openEditDialog(row: SensitiveWordVO) {
  form.value = { id: row.id, word: row.word, category: row.category || '' }
  dialogMode.value = 'edit'
  dialogVisible.value = true
}

async function submitForm() {
  if (!form.value.word.trim()) {
    ElMessage.warning('请输入敏感词')
    return
  }
  try {
    if (dialogMode.value === 'create') {
      await createSensitiveWord({
        word: form.value.word.trim(),
        category: form.value.category.trim() || undefined
      })
      ElMessage.success('创建成功')
    } else {
      await updateSensitiveWord(form.value.id, {
        word: form.value.word.trim(),
        category: form.value.category.trim() || undefined
      })
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    loadWords()
  } catch {
    // 错误由拦截器处理
  }
}

async function handleToggleEnabled(row: SensitiveWordVO) {
  try {
    await updateSensitiveWord(row.id, { enabled: !row.enabled })
    ElMessage.success(row.enabled ? '已禁用' : '已启用')
    loadWords()
  } catch {
    // 错误由拦截器处理
  }
}

async function handleDelete(row: SensitiveWordVO) {
  await ElMessageBox.confirm(`确定删除敏感词「${row.word}」吗？`, '提示', { type: 'warning' })
  try {
    await deleteSensitiveWord(row.id)
    ElMessage.success('删除成功')
    loadWords()
  } catch {
    // 错误由拦截器处理
  }
}

async function handleBatchDelete() {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先选择要删除的敏感词')
    return
  }
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 个敏感词吗？`, '提示', { type: 'warning' })
  try {
    await batchDeleteSensitiveWords(selectedIds.value)
    ElMessage.success('批量删除成功')
    selectedIds.value = []
    loadWords()
  } catch {
    // 错误由拦截器处理
  }
}

async function handleRefreshCache() {
  try {
    await refreshSensitiveWordCache()
    ElMessage.success('缓存刷新成功')
  } catch {
    // 错误由拦截器处理
  }
}

function handleSelectionChange(rows: SensitiveWordVO[]) {
  selectedIds.value = rows.map(r => r.id)
}

function formatDate(date: string) {
  return new Date(date).toLocaleString()
}

onMounted(() => loadWords())
</script>

<template>
  <div class="sensitive-words-view">
    <div class="page-header">
      <h2>敏感词管理</h2>
      <div class="header-actions">
        <el-button type="warning" @click="handleRefreshCache">刷新缓存</el-button>
        <el-button type="danger" :disabled="selectedIds.length === 0" @click="handleBatchDelete">
          批量删除{{ selectedIds.length > 0 ? `(${selectedIds.length})` : '' }}
        </el-button>
        <el-button type="primary" @click="openCreateDialog">新增敏感词</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索敏感词"
        clearable
        style="width: 200px"
        @keyup.enter="handleSearch"
      />
      <el-select v-model="searchCategory" placeholder="分类筛选" clearable style="width: 150px">
        <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
      </el-select>
      <el-select v-model="searchEnabled" placeholder="状态筛选" clearable style="width: 130px">
        <el-option label="启用" :value="true" />
        <el-option label="禁用" :value="false" />
      </el-select>
      <el-button type="primary" @click="handleSearch">搜索</el-button>
    </div>

    <el-table
      :data="words"
      v-loading="loading"
      stripe
      style="width: 100%; max-width: 100%;"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="50" />
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="word" label="敏感词" width="180" show-overflow-tooltip />
      <el-table-column label="分类" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.category" size="small">{{ row.category }}</el-tag>
          <span v-else>-</span>
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
      <el-table-column label="操作" width="180" fixed="right">
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

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增敏感词' : '编辑敏感词'" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="敏感词" required>
          <el-input v-model="form.word" placeholder="请输入敏感词" maxlength="100" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" placeholder="请选择分类" clearable style="width: 100%">
            <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sensitive-words-view {
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

.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  align-items: center;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding-bottom: 16px;
}
</style>

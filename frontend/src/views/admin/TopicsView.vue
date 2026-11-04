<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getAllTopics, createTopic, updateTopic, deleteTopic, type TopicVO } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const topics = ref<TopicVO[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const form = ref({
  id: 0,
  name: '',
  description: '',
  sortOrder: 0,
  enabled: true
})

async function loadTopics() {
  loading.value = true
  try {
    const res = await getAllTopics()
    topics.value = res.data.data
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  form.value = {
    id: 0,
    name: '',
    description: '',
    sortOrder: 0,
    enabled: true
  }
  dialogMode.value = 'create'
  dialogVisible.value = true
}

function openEditDialog(topic: TopicVO) {
  form.value = {
    id: topic.id,
    name: topic.name,
    description: topic.description || '',
    sortOrder: topic.sortOrder,
    enabled: topic.enabled
  }
  dialogMode.value = 'edit'
  dialogVisible.value = true
}

async function submitForm() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入话题名称')
    return
  }
  try {
    if (dialogMode.value === 'create') {
      await createTopic({
        name: form.value.name.trim(),
        description: form.value.description.trim() || undefined,
        sortOrder: form.value.sortOrder
      })
      ElMessage.success('创建成功')
    } else {
      await updateTopic(form.value.id, {
        name: form.value.name.trim(),
        description: form.value.description.trim() || undefined,
        sortOrder: form.value.sortOrder,
        enabled: form.value.enabled
      })
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    loadTopics()
  } catch {
    // 错误由拦截器处理
  }
}

async function handleToggleEnabled(topic: TopicVO) {
  try {
    await updateTopic(topic.id, { enabled: !topic.enabled })
    ElMessage.success(topic.enabled ? '已禁用' : '已启用')
    loadTopics()
  } catch {
    // 错误由拦截器处理
  }
}

async function handleDelete(topic: TopicVO) {
  await ElMessageBox.confirm(`确定删除话题「${topic.name}」吗？`, '提示', { type: 'warning' })
  try {
    await deleteTopic(topic.id)
    ElMessage.success('删除成功')
    loadTopics()
  } catch {
    // 错误由拦截器处理
  }
}

function formatDate(date: string) {
  return new Date(date).toLocaleString()
}

onMounted(() => loadTopics())
</script>

<template>
  <div class="topics-view">
    <div class="page-header">
      <h2>话题管理</h2>
      <el-button type="primary" @click="openCreateDialog">新增话题</el-button>
    </div>

    <el-table :data="topics" v-loading="loading" stripe style="width: 100%; max-width: 100%;">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="话题名称" width="150" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.description || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
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

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增话题' : '编辑话题'" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="话题名称" required>
          <el-input v-model="form.name" placeholder="请输入话题名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入话题描述" maxlength="200" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
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
  </div>
</template>

<style scoped>
.topics-view {
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
</style>

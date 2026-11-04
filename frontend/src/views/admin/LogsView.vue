<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getOperationLogsEnhanced, getOperationLogStats, type OperationLogVO, type OperationLogStats } from '@/api/admin'
import type { PageData } from '@/types'
import { ElMessage } from 'element-plus'

const logs = ref<OperationLogVO[]>([])
const loading = ref(false)
const currentPage = ref(0)
const totalElements = ref(0)

const filterUserId = ref('')
const filterOperation = ref('')
const filterStatus = ref('')
const dateRange = ref<[string, string] | null>(null)

const stats = ref<OperationLogStats | null>(null)
const statsLoading = ref(false)

async function loadLogs(page = 0) {
  loading.value = true
  try {
    const params: any = { page, size: 20 }
    if (filterUserId.value) params.userId = Number(filterUserId.value)
    if (filterOperation.value) params.operation = filterOperation.value
    if (filterStatus.value) params.status = filterStatus.value
    if (dateRange.value) {
      params.startTime = dateRange.value[0]
      params.endTime = dateRange.value[1]
    }
    const res = await getOperationLogsEnhanced(params)
    const data: PageData<OperationLogVO> = res.data.data
    logs.value = data.content
    currentPage.value = data.number
    totalElements.value = data.totalElements
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  statsLoading.value = true
  try {
    const res = await getOperationLogStats()
    stats.value = res.data.data
  } catch {
    ElMessage.error('加载统计失败')
  } finally {
    statsLoading.value = false
  }
}

function handlePageChange(page: number) {
  loadLogs(page - 1)
}

function handleSearch() {
  loadLogs(0)
}

function handleReset() {
  filterUserId.value = ''
  filterOperation.value = ''
  filterStatus.value = ''
  dateRange.value = null
  loadLogs(0)
}

function getStatusType(status: string) {
  return status === 'SUCCESS' ? 'success' : 'danger'
}

function getStatusLabel(status: string) {
  return status === 'SUCCESS' ? '成功' : '失败'
}

function formatDuration(ms: number) {
  if (ms < 1000) return ms + 'ms'
  return (ms / 1000).toFixed(2) + 's'
}

function formatDate(date: string) {
  return new Date(date).toLocaleString()
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

onMounted(() => {
  loadLogs()
  loadStats()
})
</script>

<template>
  <div class="logs-view">
    <div class="page-header">
      <h2>操作日志</h2>
    </div>

    <div class="stats-cards" v-loading="statsLoading">
      <el-card shadow="hover" v-if="stats">
        <el-statistic title="总操作数" :value="stats.total" />
      </el-card>
      <el-card shadow="hover" v-if="stats">
        <el-statistic title="成功数" :value="stats.successCount" />
      </el-card>
      <el-card shadow="hover" v-if="stats">
        <el-statistic title="失败数" :value="stats.failCount" />
      </el-card>
      <el-card shadow="hover" v-if="stats">
        <el-statistic title="平均耗时" :value="stats.avgDuration ?? 0" :precision="2" suffix="ms" />
      </el-card>
    </div>

    <div class="filter-bar">
      <el-input v-model="filterUserId" placeholder="用户ID" clearable style="width: 100px" @keyup.enter="handleSearch" />
      <el-input v-model="filterOperation" placeholder="操作名称" clearable style="width: 140px" @keyup.enter="handleSearch" />
      <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 100px">
        <el-option label="成功" value="SUCCESS" />
        <el-option label="失败" value="FAIL" />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        type="datetimerange"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        value-format="YYYY-MM-DDTHH:mm:ss"
        style="width: 360px"
      />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <el-table :data="logs" v-loading="loading" stripe style="width: 100%; max-width: 100%;">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="操作人" width="120">
        <template #default="{ row }">
          {{ row.username || '用户' + row.userId }}
        </template>
      </el-table-column>
      <el-table-column prop="operation" label="操作" width="140" />
      <el-table-column prop="method" label="方法" width="220" show-overflow-tooltip />
      <el-table-column prop="url" label="请求路径" width="180" show-overflow-tooltip />
      <el-table-column label="耗时" width="90">
        <template #default="{ row }">
          <span :class="{ 'slow-query': row.duration > 1000 }">
            {{ formatDuration(row.duration) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" width="130" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)" size="small">
            {{ getStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="参数" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.params || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="错误信息" width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.errorMsg" class="error-text">{{ row.errorMsg }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作时间" width="170">
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper" v-if="totalElements > 20">
      <el-pagination
        :current-page="currentPage + 1"
        :page-size="20"
        :total="totalElements"
        layout="prev, pager, next"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<style scoped>
.logs-view {
  padding: 0 16px;
  max-width: 100%;
  box-sizing: border-box;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.stats-cards {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.stats-cards .el-card {
  flex: 1;
}

.filter-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.slow-query {
  color: #e6a23c;
  font-weight: 600;
}

.error-text {
  color: #f56c6c;
  font-size: 13px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>

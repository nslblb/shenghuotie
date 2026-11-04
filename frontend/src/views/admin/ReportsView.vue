<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  getAdminReports,
  getAdminReportDetail,
  getAdminReportStats,
  adminHandleReport,
  adminDeleteReport,
  getAdminAutoHandleRules,
  createAdminAutoHandleRule,
  updateAdminAutoHandleRule,
  executeAdminAutoHandleRules,
  type ReportDetailVO,
  type ReportStatsResponse,
  type AutoHandleRuleVO
} from '@/api/admin'
import type { PageData } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'

const router = useRouter()
import type { ECharts } from 'echarts'

const reports = ref<ReportDetailVO[]>([])
const loading = ref(false)
const currentPage = ref(0)
const totalPages = ref(0)
const filterStatus = ref('')

const detailDialogVisible = ref(false)
const detailLoading = ref(false)
const currentDetail = ref<ReportDetailVO | null>(null)

const statsDialogVisible = ref(false)
const statsLoading = ref(false)
const reportStats = ref<ReportStatsResponse | null>(null)

const rulesDialogVisible = ref(false)
const rulesLoading = ref(false)
const autoHandleRules = ref<AutoHandleRuleVO[]>([])
const ruleForm = ref({
  id: 0,
  targetType: 'POST',
  reportThreshold: 5,
  action: 'DISABLE',
  timeWindowHours: 24,
  enabled: true
})
const ruleFormMode = ref<'create' | 'edit'>('create')

const handleDialogVisible = ref(false)
const handleForm = ref({
  reportId: 0,
  status: 'RESOLVED',
  handleResult: '',
  action: 'DISABLE' as 'DISABLE' | 'DELETE' | null
})

async function loadReports(page = 0) {
  loading.value = true
  try {
    const res = await getAdminReports({
      page,
      size: 20,
      status: filterStatus.value || undefined,
    })
    const data: PageData<ReportDetailVO> = res.data.data
    reports.value = data.content
    currentPage.value = data.number
    totalPages.value = data.totalPages
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  loadReports(page - 1)
}

function handleFilterChange() {
  loadReports(0)
}

function openDetailDialog(report: ReportDetailVO | { id: number }) {
  detailLoading.value = true
  detailDialogVisible.value = true
  getAdminReportDetail(report.id)
    .then(res => {
      currentDetail.value = res.data.data
    })
    .catch(() => {
      ElMessage.error('加载详情失败')
      detailDialogVisible.value = false
    })
    .finally(() => {
      detailLoading.value = false
    })
}

function openHandleDialog(report: ReportDetailVO | { id: number }, status: 'RESOLVED' | 'REJECTED') {
  handleForm.value = {
    reportId: report.id,
    status,
    handleResult: '',
    action: status === 'RESOLVED' ? 'DISABLE' : null
  }
  handleDialogVisible.value = true
}

async function submitHandle() {
  try {
    await adminHandleReport(
      handleForm.value.reportId,
      handleForm.value.status,
      handleForm.value.handleResult,
      handleForm.value.action || undefined
    )
    ElMessage.success('处理成功')
    handleDialogVisible.value = false
    loadReports(currentPage.value)
  } catch {
    ElMessage.error('处理失败')
  }
}

async function handleDeleteReport(report: ReportDetailVO) {
  await ElMessageBox.confirm('确定删除该举报记录吗？', '提示', { type: 'warning' })
  try {
    await adminDeleteReport(report.id)
    ElMessage.success('已删除')
    loadReports(currentPage.value)
  } catch {
    // 错误由拦截器处理
  }
}

function goToPostDetail(targetId: number, targetType: string) {
  if (targetType === 'POST') {
    router.push(`/post/${targetId}`)
  }
}

async function openStatsDialog() {
  statsLoading.value = true
  statsDialogVisible.value = true
  try {
    const res = await getAdminReportStats()
    reportStats.value = res.data.data
  } catch {
    ElMessage.error('加载统计失败')
    statsDialogVisible.value = false
  } finally {
    statsLoading.value = false
  }
}

async function openRulesDialog() {
  rulesLoading.value = true
  rulesDialogVisible.value = true
  try {
    const res = await getAdminAutoHandleRules()
    autoHandleRules.value = res.data.data
  } catch {
    ElMessage.error('加载规则失败')
    rulesDialogVisible.value = false
  } finally {
    rulesLoading.value = false
  }
}

function resetRuleForm() {
  ruleForm.value = {
    id: 0,
    targetType: 'POST',
    reportThreshold: 5,
    action: 'DISABLE',
    timeWindowHours: 24,
    enabled: true
  }
  ruleFormMode.value = 'create'
}

function editRule(rule: AutoHandleRuleVO) {
  ruleForm.value = {
    id: rule.id,
    targetType: rule.targetType,
    reportThreshold: rule.reportThreshold,
    action: rule.action,
    timeWindowHours: rule.timeWindowHours || 24,
    enabled: rule.enabled
  }
  ruleFormMode.value = 'edit'
}

async function saveRule() {
  try {
    const data = {
      targetType: ruleForm.value.targetType,
      reportThreshold: ruleForm.value.reportThreshold,
      action: ruleForm.value.action,
      timeWindowHours: ruleForm.value.timeWindowHours,
      enabled: ruleForm.value.enabled
    }
    if (ruleFormMode.value === 'create') {
      await createAdminAutoHandleRule(data)
    } else {
      await updateAdminAutoHandleRule(ruleForm.value.id, data)
    }
    ElMessage.success('保存成功')
    const res = await getAdminAutoHandleRules()
    autoHandleRules.value = res.data.data
    resetRuleForm()
  } catch {
    ElMessage.error('保存失败')
  }
}

async function toggleRuleEnabled(rule: AutoHandleRuleVO) {
  try {
    await updateAdminAutoHandleRule(rule.id, {
      targetType: rule.targetType,
      reportThreshold: rule.reportThreshold,
      action: rule.action,
      timeWindowHours: rule.timeWindowHours || undefined,
      enabled: !rule.enabled
    })
    ElMessage.success(rule.enabled ? '已禁用' : '已启用')
    const res = await getAdminAutoHandleRules()
    autoHandleRules.value = res.data.data
  } catch {
    ElMessage.error('操作失败')
  }
}

async function executeRules() {
  try {
    await ElMessageBox.confirm('确定要立即执行自动处理规则吗？', '确认执行')
    await executeAdminAutoHandleRules()
    ElMessage.success('执行完成')
    loadReports(currentPage.value)
  } catch {}
}

let chartInstance: ECharts | null = null

function initChart() {
  const chartDom = document.getElementById('report-chart')
  if (!chartDom || !reportStats.value) return
  
  if (chartInstance) {
    chartInstance.dispose()
  }
  
  chartInstance = echarts.init(chartDom)
  
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center'
    },
    series: [
      {
        name: '举报原因',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 14,
            fontWeight: 'bold'
          }
        },
        labelLine: {
          show: false
        },
        data: reportStats.value.reasonStats.map((s, i) => ({
          value: s.count,
          name: s.reason,
          itemStyle: {
            color: ['#f56c6c', '#e6a23c', '#409eff', '#67c23a', '#909399', '#ff69b4', '#9370db', '#20b2aa', '#ff8c00', '#87ceeb'][i % 10]
          }
        }))
      }
    ]
  }
  
  chartInstance.setOption(option)
}

watch(statsDialogVisible, (val) => {
  if (val && reportStats.value) {
    setTimeout(() => initChart(), 100)
  }
})

function getStatusType(status: string) {
  switch (status) {
    case 'PENDING': return 'warning'
    case 'RESOLVED': return 'danger'
    case 'REJECTED': return 'info'
    default: return 'info'
  }
}

function getStatusLabel(status: string) {
  switch (status) {
    case 'PENDING': return '待处理'
    case 'RESOLVED': return '已处理'
    case 'REJECTED': return '已驳回'
    default: return status
  }
}

function getTargetTypeLabel(type: string) {
  return type === 'POST' ? '帖子' : '评论'
}

function formatDate(date: string) {
  return new Date(date).toLocaleString()
}

function truncate(text: string, length: number) {
  return text.length > length ? text.slice(0, length) + '...' : text
}

onMounted(() => {
  loadReports()
})
</script>

<template>
  <div class="reports-view">
    <div class="page-header">
      <h2>举报管理</h2>
      <div class="header-actions">
        <el-button type="primary" size="small" @click="openStatsDialog">
          统计分析
        </el-button>
        <el-button type="warning" size="small" @click="openRulesDialog">
          自动处理规则
        </el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="filterStatus" placeholder="筛选状态" clearable style="width: 120px" @change="handleFilterChange">
        <el-option label="待处理" value="PENDING" />
        <el-option label="已处理" value="RESOLVED" />
        <el-option label="已驳回" value="REJECTED" />
      </el-select>
    </div>

    <el-table :data="reports" v-loading="loading" stripe style="width: 100%; max-width: 100%;">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column label="举报人" width="120">
        <template #default="{ row }">
          <div class="user-cell">
            <el-avatar :size="24" :src="row.reporterAvatar || undefined">
              {{ row.reporterName?.charAt(0) || '?' }}
            </el-avatar>
            <span>{{ row.reporterName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="举报目标" min-width="140">
        <template #default="{ row }">
          <div>
            <el-tag size="small" :type="row.targetType === 'POST' ? '' : 'info'" style="margin-right: 6px">
              {{ getTargetTypeLabel(row.targetType) }}
            </el-tag>
            <span
              class="target-title"
              :class="{ 'clickable': row.targetType === 'POST' }"
              @click="goToPostDetail(row.targetId, row.targetType)"
            >{{ row.targetTitle || '#' + row.targetId }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="举报原因" min-width="120" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)" size="small">
            {{ getStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="举报时间" width="150">
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="处理结果" min-width="120">
        <template #default="{ row }">
          <template v-if="row.handlerName">
            <div class="handle-info">
              <span>{{ row.handlerName }}</span>
              <span class="handle-result">{{ row.handleResult }}</span>
            </div>
          </template>
          <span v-else class="no-handle">-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button type="danger" size="small" link @click="handleDeleteReport(row)">
            删除
          </el-button>
          <template v-if="row.status === 'PENDING'">
            <el-button type="success" size="small" link @click="openHandleDialog(row, 'RESOLVED')">
              通过
            </el-button>
            <el-button size="small" link @click="openHandleDialog(row, 'REJECTED')">
              驳回
            </el-button>
          </template>
          <span v-else class="handled-text">已处理</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper" v-if="totalPages > 1">
      <el-pagination
        :current-page="currentPage + 1"
        :page-size="20"
        :total="totalPages * 20"
        layout="prev, pager, next"
        @current-change="handlePageChange"
      />
    </div>

    <el-dialog v-model="detailDialogVisible" title="举报详情" width="700px">
      <div v-loading="detailLoading">
        <template v-if="currentDetail">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="举报人">
              <div class="user-cell">
                <el-avatar :size="24" :src="currentDetail.reporterAvatar || undefined">
                  {{ currentDetail.reporterName?.charAt(0) || '?' }}
                </el-avatar>
                <span>{{ currentDetail.reporterName }}</span>
              </div>
            </el-descriptions-item>
            <el-descriptions-item label="举报时间">
              {{ formatDate(currentDetail.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="举报原因">
              {{ currentDetail.reason }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusType(currentDetail.status)" size="small">
                {{ getStatusLabel(currentDetail.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="被举报次数">
              <el-badge :value="currentDetail.reportCount" type="danger" />
            </el-descriptions-item>
            <el-descriptions-item label="处理人" v-if="currentDetail.handlerName">
              {{ currentDetail.handlerName }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-title">被举报内容</div>
          <el-card shadow="never">
            <template v-if="currentDetail.targetType === 'POST'">
              <div class="post-preview" v-if="currentDetail.targetCoverImage">
                <el-image :src="currentDetail.targetCoverImage" style="max-width: 200px; max-height: 150px" fit="cover" />
              </div>
              <h4>{{ currentDetail.targetTitle }}</h4>
              <p class="content-preview">{{ truncate(currentDetail.targetContent || '', 300) }}</p>
              <div class="meta-info">
                <span>作者：{{ currentDetail.targetAuthorName }}</span>
                <el-tag :type="currentDetail.targetStatus === 'PUBLISHED' ? 'success' : 'danger'" size="small">
                  {{ currentDetail.targetStatus === 'PUBLISHED' ? '正常' : '已禁用' }}
                </el-tag>
              </div>
            </template>
            <template v-else>
              <p class="content-preview">{{ currentDetail.targetContent }}</p>
              <div class="meta-info">
                <span>评论者：{{ currentDetail.targetAuthorName }}</span>
              </div>
            </template>
          </el-card>

          <div class="section-title" v-if="currentDetail.reportHistory?.length > 1">
            举报历史 ({{ currentDetail.reportHistory.length }}次)
          </div>
          <el-table v-if="currentDetail.reportHistory?.length > 1" :data="currentDetail.reportHistory" size="small" max-height="200">
            <el-table-column label="举报人" width="120">
              <template #default="{ row }">
                {{ row.reporterName }}
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="原因" />
            <el-table-column label="时间" width="150">
              <template #default="{ row }">
                {{ formatDate(row.createdAt) }}
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-dialog>

    <el-dialog v-model="handleDialogVisible" title="处理举报" width="500px">
      <el-form label-width="100px">
        <el-form-item label="处理结果">
          <el-radio-group v-model="handleForm.status">
            <el-radio value="RESOLVED">通过</el-radio>
            <el-radio value="REJECTED">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理说明">
          <el-input v-model="handleForm.handleResult" type="textarea" :rows="2" placeholder="请输入处理说明" />
        </el-form-item>
        <el-form-item label="内容操作" v-if="handleForm.status === 'RESOLVED'">
          <el-radio-group v-model="handleForm.action">
            <el-radio value="DISABLE">禁用内容</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitHandle">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="statsDialogVisible" title="举报统计分析" width="700px">
      <div v-loading="statsLoading">
        <template v-if="reportStats">
          <el-row :gutter="20" style="margin-bottom: 20px">
            <el-col :span="6">
              <el-statistic title="总举报数" :value="reportStats.totalReports" />
            </el-col>
            <el-col :span="6">
              <el-statistic title="待处理" :value="reportStats.pendingReports" />
            </el-col>
            <el-col :span="6">
              <el-statistic title="已处理" :value="reportStats.resolvedReports" />
            </el-col>
            <el-col :span="6">
              <el-statistic title="已驳回" :value="reportStats.rejectedReports" />
            </el-col>
          </el-row>

          <div class="chart-container" v-if="reportStats.reasonStats.length > 0">
            <h4 style="text-align: center; margin-bottom: 16px">举报原因分布</h4>
            <div id="report-chart" style="height: 300px"></div>
          </div>

          <el-table :data="reportStats.reasonStats" style="margin-top: 20px">
            <el-table-column prop="reason" label="举报原因" />
            <el-table-column prop="count" label="数量" width="100" align="center" />
            <el-table-column label="占比" width="120" align="center">
              <template #default="{ row }">
                {{ row.percentage.toFixed(1) }}%
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-dialog>

    <el-dialog v-model="rulesDialogVisible" title="自动处理规则" width="800px">
      <div v-loading="rulesLoading">
        <div class="rules-header">
          <el-button type="primary" size="small" @click="resetRuleForm">新建规则</el-button>
          <el-button type="warning" size="small" @click="executeRules">立即执行规则</el-button>
        </div>

        <el-form :model="ruleForm" label-width="120px" style="margin-top: 16px" inline>
          <el-form-item label="目标类型">
            <el-select v-model="ruleForm.targetType" style="width: 120px">
              <el-option label="帖子" value="POST" />
              <el-option label="评论" value="COMMENT" />
            </el-select>
          </el-form-item>
          <el-form-item label="举报阈值">
            <el-input-number v-model="ruleForm.reportThreshold" :min="1" :max="100" />
          </el-form-item>
          <el-form-item label="处理动作">
            <el-select v-model="ruleForm.action" style="width: 120px">
              <el-option label="禁用" value="DISABLE" />
            </el-select>
          </el-form-item>
          <el-form-item label="时间窗口(小时)">
            <el-input-number v-model="ruleForm.timeWindowHours" :min="0" :max="720" />
          </el-form-item>
          <el-form-item label="启用">
            <el-switch v-model="ruleForm.enabled" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="saveRule">
              {{ ruleFormMode === 'create' ? '创建' : '更新' }}
            </el-button>
            <el-button v-if="ruleFormMode === 'edit'" @click="resetRuleForm">取消</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="autoHandleRules" style="margin-top: 20px">
          <el-table-column label="目标类型" width="100">
            <template #default="{ row }">
              {{ getTargetTypeLabel(row.targetType) }}
            </template>
          </el-table-column>
          <el-table-column prop="reportThreshold" label="举报阈值" width="100" align="center" />
          <el-table-column label="处理动作" width="100">
            <template #default="{ row }">
              {{ row.action === 'DISABLE' ? '禁用' : '删除' }}
            </template>
          </el-table-column>
          <el-table-column label="时间窗口" width="120">
            <template #default="{ row }">
              {{ row.timeWindowHours ? row.timeWindowHours + '小时' : '不限' }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                {{ row.enabled ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button type="primary" size="small" link @click="editRule(row)">编辑</el-button>
              <el-button :type="row.enabled ? 'warning' : 'success'" size="small" link @click="toggleRuleEnabled(row)">
                {{ row.enabled ? '禁用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.reports-view {
  padding: 0 16px;
  max-width: 100%;
  box-sizing: border-box;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.filter-bar {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.target-title {
  color: #606266;
  font-size: 13px;
}

.target-title.clickable {
  color: #409eff;
  cursor: pointer;
}

.target-title.clickable:hover {
  text-decoration: underline;
}

.handle-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 13px;
}

.handle-result {
  color: #909399;
  font-size: 12px;
}

.no-handle {
  color: #c0c4cc;
}

.handled-text {
  color: #909399;
  font-size: 13px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

.section-title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin: 16px 0 8px;
}

.post-preview {
  margin-bottom: 12px;
}

.content-preview {
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
  margin: 8px 0;
}

.meta-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  font-size: 13px;
  color: #909399;
}

.chart-container {
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
}

.rules-header {
  display: flex;
  gap: 12px;
}
</style>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getAppeals, getAppealDetail, approveAppeal, rejectAppeal, deleteAppeal, getAppealHistory, type AppealVO } from '@/api/admin'
import type { PageData } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const appeals = ref<AppealVO[]>([])
const loading = ref(false)
const currentPage = ref(0)
const totalElements = ref(0)
const filterStatus = ref('')

const detailDialogVisible = ref(false)
const detailLoading = ref(false)
const currentDetail = ref<AppealVO | null>(null)

const handleDialogVisible = ref(false)
const handleForm = ref({
  id: 0,
  approved: true,
  handleNote: ''
})

async function loadAppeals(page = 0) {
  loading.value = true
  try {
    const res = await getAppeals({ page, size: 20, status: filterStatus.value || undefined })
    const data: PageData<AppealVO> = res.data.data
    appeals.value = data.content
    currentPage.value = data.number
    totalElements.value = data.totalElements
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  loadAppeals(page - 1)
}

function handleFilterChange() {
  loadAppeals(0)
}

function openDetailDialog(appeal: AppealVO) {
  detailLoading.value = true
  detailDialogVisible.value = true
  getAppealDetail(appeal.id)
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

function openHandleDialog(appeal: AppealVO, approved: boolean) {
  handleForm.value = {
    id: appeal.id,
    approved,
    handleNote: ''
  }
  handleDialogVisible.value = true
}

async function submitHandle() {
  try {
    if (handleForm.value.approved) {
      await approveAppeal(handleForm.value.id, handleForm.value.handleNote)
    } else {
      await rejectAppeal(handleForm.value.id, handleForm.value.handleNote)
    }
    ElMessage.success(handleForm.value.approved ? '已批准解封' : '已驳回申诉')
    handleDialogVisible.value = false
    loadAppeals(currentPage.value)
  } catch {
    ElMessage.error('操作失败')
  }
}

async function handleDeleteAppeal(appeal: AppealVO) {
  await ElMessageBox.confirm('确定删除该申诉记录吗？', '提示', { type: 'warning' })
  try {
    await deleteAppeal(appeal.id)
    ElMessage.success('已删除')
    loadAppeals(currentPage.value)
  } catch {
    // 错误由拦截器处理
  }
}

function getStatusType(status: string) {
  switch (status) {
    case 'PENDING': return 'warning'
    case 'APPROVED': return 'success'
    case 'REJECTED': return 'danger'
    default: return 'info'
  }
}

function getStatusLabel(status: string) {
  switch (status) {
    case 'PENDING': return '待处理'
    case 'APPROVED': return '已批准'
    case 'REJECTED': return '已驳回'
    default: return status
  }
}

function formatDate(date: string) {
  return new Date(date).toLocaleString()
}

function truncate(text: string, length: number) {
  if (!text) return '-'
  return text.length > length ? text.slice(0, length) + '...' : text
}

function goToPostDetail(postId: number) {
  router.push(`/post/${postId}`)
}

onMounted(() => loadAppeals())
</script>

<template>
  <div class="appeals-view">
    <div class="page-header">
      <h2>申诉管理</h2>
      <div class="header-actions">
        <el-tag type="info" size="large">共 {{ totalElements }} 条</el-tag>
        <el-select v-model="filterStatus" placeholder="筛选状态" clearable style="width: 120px" @change="handleFilterChange">
          <el-option label="待处理" value="PENDING" />
          <el-option label="已批准" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
      </div>
    </div>

    <el-table :data="appeals" v-loading="loading" stripe style="width: 100%; max-width: 100%;">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column label="申诉人" width="120">
        <template #default="{ row }">
          <div class="user-cell">
            <el-avatar :size="24" :src="row.userAvatar || undefined">
              {{ row.userName?.charAt(0) || '?' }}
            </el-avatar>
            <span>{{ row.userName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="帖子标题" min-width="160">
        <template #default="{ row }">
          <span class="post-title clickable" @click="goToPostDetail(row.postId)">{{ row.postTitle || '#' + row.postId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="申诉理由" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ truncate(row.reason, 50) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="原封禁原因" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.banReason || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)" size="small">
            {{ getStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="申诉时间" width="150">
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="处理结果" min-width="120">
        <template #default="{ row }">
          <template v-if="row.handlerName">
            <div class="handle-info">
              <span>{{ row.handlerName }}</span>
              <span class="handle-note">{{ row.handleNote }}</span>
            </div>
          </template>
          <span v-else class="no-handle">-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" link @click="openDetailDialog(row)">
            详情
          </el-button>
          <template v-if="row.status === 'PENDING'">
            <el-button type="success" size="small" link @click="openHandleDialog(row, true)">
              批准
            </el-button>
            <el-button type="danger" size="small" link @click="openHandleDialog(row, false)">
              驳回
            </el-button>
          </template>
          <template v-else>
            <el-button type="danger" size="small" link @click="handleDeleteAppeal(row)">
              删除
            </el-button>
          </template>
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

    <el-empty v-if="!loading && appeals.length === 0" description="暂无申诉记录" />

    <el-dialog v-model="detailDialogVisible" title="申诉详情" width="700px">
      <div v-loading="detailLoading">
        <template v-if="currentDetail">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="申诉人">
              <div class="user-cell">
                <el-avatar :size="24" :src="currentDetail.userAvatar || undefined">
                  {{ currentDetail.userName?.charAt(0) || '?' }}
                </el-avatar>
                <span>{{ currentDetail.userName }}</span>
              </div>
            </el-descriptions-item>
            <el-descriptions-item label="申诉时间">
              {{ formatDate(currentDetail.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusType(currentDetail.status)" size="small">
                {{ getStatusLabel(currentDetail.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="处理人" v-if="currentDetail.handlerName">
              {{ currentDetail.handlerName }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-title">申诉理由</div>
          <el-card shadow="never">
            <p class="reason-text">{{ currentDetail.reason || '未填写' }}</p>
          </el-card>

          <div class="section-title">原封禁原因</div>
          <el-card shadow="never">
            <p class="reason-text">{{ currentDetail.banReason || '未记录' }}</p>
          </el-card>

          <div class="section-title">被申诉帖子</div>
          <el-card shadow="never">
            <div class="post-preview" v-if="currentDetail.postCoverImage">
              <el-image :src="currentDetail.postCoverImage" style="max-width: 200px; max-height: 150px" fit="cover" />
            </div>
            <h4>{{ currentDetail.postTitle }}</h4>
            <p class="content-preview">{{ truncate(currentDetail.postContent || '', 300) }}</p>
          </el-card>

          <div class="section-title" v-if="currentDetail.handleNote">
            处理备注
          </div>
          <el-card shadow="never" v-if="currentDetail.handleNote">
            <p class="reason-text">{{ currentDetail.handleNote }}</p>
          </el-card>

          <div class="section-title" v-if="currentDetail.history && currentDetail.history.length > 0">
            历史申诉记录 ({{ currentDetail.history.length }}次)
          </div>
          <el-table v-if="currentDetail.history && currentDetail.history.length > 0" :data="currentDetail.history" size="small" max-height="200">
            <el-table-column label="申诉理由" min-width="150">
              <template #default="{ row }">
                {{ truncate(row.reason, 30) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" size="small">
                  {{ getStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="处理备注" min-width="120">
              <template #default="{ row }">
                {{ row.handleNote || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="申诉时间" width="150">
              <template #default="{ row }">
                {{ formatDate(row.createdAt) }}
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-dialog>

    <el-dialog v-model="handleDialogVisible" :title="handleForm.approved ? '批准申诉' : '驳回申诉'" width="500px">
      <el-form label-width="100px">
        <el-form-item label="处理备注">
          <el-input v-model="handleForm.handleNote" type="textarea" :rows="3" placeholder="请输入处理说明（将通知用户）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDialogVisible = false">取消</el-button>
        <el-button :type="handleForm.approved ? 'success' : 'danger'" @click="submitHandle">
          {{ handleForm.approved ? '批准解封' : '驳回申诉' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.appeals-view {
  padding: 0 16px;
  max-width: 100%;
  box-sizing: border-box;
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
  gap: 12px;
  align-items: center;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.post-title.clickable {
  color: #409eff;
  cursor: pointer;
}

.post-title.clickable:hover {
  text-decoration: underline;
}

.handle-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 13px;
}

.handle-note {
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

.reason-text {
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
  margin: 0;
}
</style>

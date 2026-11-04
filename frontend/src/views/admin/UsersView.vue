<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import {
  getAdminUsers,
  getAdminUserDetail,
  updateUserStatus,
  resetUserPassword,
  updateUserRole,
  batchUpdateUserStatus,
  type UserDetailVO
} from '@/api/admin'
import type { UserVO, PageData } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'

const users = ref<UserVO[]>([])
const loading = ref(false)
const currentPage = ref(0)
const totalElements = ref(0)
const keyword = ref('')
const filterRole = ref('')
const filterStatus = ref('')
const dateRange = ref<[string, string] | null>(null)

const selectedUsers = ref<UserVO[]>([])
const detailVisible = ref(false)
const currentUser = ref<UserDetailVO | null>(null)
const detailLoading = ref(false)

const batchStatus = ref('')
const batchBanReason = ref('')
const banReason = ref('')

const hasSelection = computed(() => selectedUsers.value.length > 0)

async function loadUsers(page = 0) {
  loading.value = true
  try {
    const params: any = {
      page,
      size: 20,
      keyword: keyword.value || undefined,
      role: filterRole.value || undefined,
      status: filterStatus.value || undefined
    }
    if (dateRange.value) {
      params.startTime = dateRange.value[0]
      params.endTime = dateRange.value[1]
    }
    const res = await getAdminUsers(params)
    const data: PageData<UserVO> = res.data.data
    users.value = data.content
    currentPage.value = data.number
    totalElements.value = data.totalElements
    selectedUsers.value = []
  } finally {
    loading.value = false
  }
}

async function showUserDetail(user: UserVO) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await getAdminUserDetail(user.id)
    currentUser.value = res.data.data
  } catch {
    ElMessage.error('获取用户详情失败')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function handleToggleStatus(user: UserVO) {
  const newStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const action = newStatus === 'DISABLED' ? '禁用' : '启用'
  
  if (newStatus === 'DISABLED') {
    const { value } = await ElMessageBox.prompt('请输入禁用原因', '禁用用户', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /\S+/,
      inputErrorMessage: '请输入禁用原因'
    })
    banReason.value = value
  } else {
    banReason.value = ''
    await ElMessageBox.confirm(`确定${action}用户 ${user.nickname} 吗？`, '提示', { type: 'warning' })
  }
  
  try {
    await updateUserStatus(user.id, newStatus, banReason.value || undefined)
    user.status = newStatus
    ElMessage.success(`已${action}`)
  } catch {
    // 错误由拦截器处理
  }
}

async function handleResetPassword(user: UserVO) {
  await ElMessageBox.confirm(`确定重置用户 ${user.nickname} 的密码为 123456 吗？`, '提示', { type: 'warning' })
  try {
    await resetUserPassword(user.id)
    ElMessage.success('密码已重置为 123456')
  } catch {
    // 错误由拦截器处理
  }
}

async function handleToggleRole(user: UserVO) {
  const newRole = user.role === 'ADMIN' ? 'USER' : 'ADMIN'
  const action = newRole === 'ADMIN' ? '设为管理员' : '撤销管理员'
  await ElMessageBox.confirm(`确定${action}吗？`, '提示', { type: 'warning' })
  try {
    await updateUserRole(user.id, newRole)
    user.role = newRole
    ElMessage.success(`已${action}`)
  } catch {
    // 错误由拦截器处理
  }
}

async function handleBatchStatus() {
  if (!batchStatus.value) {
    ElMessage.warning('请选择操作类型')
    return
  }
  
  const action = batchStatus.value === 'DISABLED' ? '禁用' : '启用'
  let banReasonText = ''
  
  if (batchStatus.value === 'DISABLED') {
    const { value } = await ElMessageBox.prompt('请输入禁用原因', '批量禁用', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /\S+/,
      inputErrorMessage: '请输入禁用原因'
    })
    banReasonText = value
  } else {
    await ElMessageBox.confirm(`确定批量${action} ${selectedUsers.value.length} 个用户吗？`, '提示', { type: 'warning' })
  }
  
  try {
    await batchUpdateUserStatus(
      selectedUsers.value.map(u => u.id),
      batchStatus.value,
      banReasonText || undefined
    )
    ElMessage.success(`已批量${action}`)
    loadUsers(currentPage.value)
  } catch {
    // 错误由拦截器处理
  }
}

function handleSearch() {
  loadUsers(0)
}

function handleReset() {
  keyword.value = ''
  filterRole.value = ''
  filterStatus.value = ''
  dateRange.value = null
  loadUsers(0)
}

function handlePageChange(page: number) {
  loadUsers(page)
}

function handleSelectionChange(selection: UserVO[]) {
  selectedUsers.value = selection
}

onMounted(() => loadUsers())
</script>

<template>
  <div class="users-page">
    <h2 style="margin-bottom: 20px">用户管理</h2>

    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索手机号/昵称"
        style="width: 200px"
        clearable
        @clear="handleSearch"
        @keyup.enter="handleSearch"
      />
      <el-select v-model="filterRole" placeholder="角色筛选" style="width: 120px" clearable>
        <el-option label="管理员" value="ADMIN" />
        <el-option label="普通用户" value="USER" />
      </el-select>
      <el-select v-model="filterStatus" placeholder="状态筛选" style="width: 120px" clearable>
        <el-option label="正常" value="ACTIVE" />
        <el-option label="已禁用" value="DISABLED" />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="注册开始日期"
        end-placeholder="注册结束日期"
        value-format="YYYY-MM-DD"
        style="width: 260px"
      />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <div class="batch-actions" v-if="hasSelection">
      <span class="selection-info">已选择 {{ selectedUsers.length }} 个用户</span>
      <el-select v-model="batchStatus" placeholder="选择操作" style="width: 120px">
        <el-option label="批量禁用" value="DISABLED" />
        <el-option label="批量启用" value="ACTIVE" />
      </el-select>
      <el-button type="primary" @click="handleBatchStatus">执行</el-button>
    </div>

    <el-table
      :data="users"
      v-loading="loading"
      stripe
      style="width: 100%"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="50" />
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="用户" width="200">
        <template #default="{ row }">
          <div class="user-cell">
            <el-avatar :size="36" :src="row.avatarUrl" />
            <div class="user-info">
              <span class="nickname">{{ row.nickname }}</span>
              <span class="phone">{{ row.phone }}</span>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="role" label="角色" width="100">
        <template #default="{ row }">
          <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" size="small">
            {{ row.role === 'ADMIN' ? '管理员' : '用户' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
            {{ row.status === 'ACTIVE' ? '正常' : '已禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="注册时间" width="170" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" text size="small" @click="showUserDetail(row)">
            详情
          </el-button>
          <el-button
            v-if="row.role !== 'ADMIN'"
            :type="row.status === 'ACTIVE' ? 'danger' : 'success'"
            text
            size="small"
            @click="handleToggleStatus(row)"
          >
            {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
          </el-button>
          <el-button
            v-if="row.role !== 'ADMIN'"
            type="warning"
            text
            size="small"
            @click="handleToggleRole(row)"
          >
            设为管理员
          </el-button>
          <el-button
            v-else
            type="info"
            text
            size="small"
            @click="handleToggleRole(row)"
          >
            撤销管理员
          </el-button>
          <el-button type="warning" text size="small" @click="handleResetPassword(row)">
            重置密码
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

    <el-dialog v-model="detailVisible" title="用户详情" width="600px" append-to-body destroy-on-close>
      <div v-loading="detailLoading">
        <template v-if="currentUser">
          <div class="user-header">
            <el-avatar :size="80" :src="currentUser.avatarUrl" />
            <div class="user-basic">
              <h3>{{ currentUser.nickname }}</h3>
              <p>{{ currentUser.phone }}</p>
              <div class="tags">
                <el-tag :type="currentUser.role === 'ADMIN' ? 'danger' : 'info'">
                  {{ currentUser.role === 'ADMIN' ? '管理员' : '普通用户' }}
                </el-tag>
                <el-tag :type="currentUser.status === 'ACTIVE' ? 'success' : 'danger'">
                  {{ currentUser.status === 'ACTIVE' ? '正常' : '已禁用' }}
                </el-tag>
              </div>
            </div>
          </div>
          
          <el-descriptions :column="2" border style="margin-top: 20px">
            <el-descriptions-item label="帖子数">
              {{ currentUser.postCount }}
            </el-descriptions-item>
            <el-descriptions-item label="评论数">
              {{ currentUser.commentCount }}
            </el-descriptions-item>
            <el-descriptions-item label="粉丝数">
              {{ currentUser.followerCount }}
            </el-descriptions-item>
            <el-descriptions-item label="关注数">
              {{ currentUser.followingCount }}
            </el-descriptions-item>
            <el-descriptions-item label="被举报次数">
              <el-tag :type="currentUser.reportCount > 0 ? 'danger' : 'success'">
                {{ currentUser.reportCount }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="注册时间">
              {{ currentUser.createdAt }}
            </el-descriptions-item>
            <el-descriptions-item v-if="currentUser.bio" label="简介" :span="2">
              {{ currentUser.bio }}
            </el-descriptions-item>
            <el-descriptions-item v-if="currentUser.banReason" label="禁用原因" :span="2">
              <span style="color: #f56c6c">{{ currentUser.banReason }}</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="currentUser.bannedAt" label="禁用时间">
              {{ currentUser.bannedAt }}
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="warning" @click="handleResetPassword(currentUser!); detailVisible = false">
          重置密码
        </el-button>
        <el-button
          v-if="currentUser && currentUser.role !== 'ADMIN'"
          :type="currentUser.status === 'ACTIVE' ? 'danger' : 'success'"
          @click="handleToggleStatus(currentUser as any); detailVisible = false"
        >
          {{ currentUser?.status === 'ACTIVE' ? '禁用' : '启用' }}
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

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-info {
  display: flex;
  flex-direction: column;
}

.nickname {
  font-weight: 500;
  color: #303133;
}

.phone {
  font-size: 12px;
  color: #909399;
}

.user-header {
  display: flex;
  align-items: center;
  gap: 20px;
}

.user-basic h3 {
  margin: 0 0 4px 0;
  font-size: 18px;
}

.user-basic p {
  margin: 0 0 8px 0;
  color: #909399;
}

.tags {
  display: flex;
  gap: 8px;
}

:deep(.el-table .el-button + .el-button) {
  margin-left: 4px;
}
</style>

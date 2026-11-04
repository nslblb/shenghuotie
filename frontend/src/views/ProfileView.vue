<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getUserProfile, follow, unfollow, updateUser, changePassword, uploadFile, deleteAccount, sendDeleteCode } from '@/api/user'
import { getUserPosts } from '@/api/post'
import { useUserStore } from '@/stores/user'
import type { UserProfileVO, PostVO, PageData } from '@/types'
import PostCard from '@/components/PostCard.vue'
import SliderCaptcha from '@/components/SliderCaptcha.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const profile = ref<UserProfileVO | null>(null)
const posts = ref<PostVO[]>([])
const loading = ref(true)
const currentPage = ref(0)
const totalPages = ref(0)

const userId = computed(() => {
  const id = Number(route.params.id)
  return isNaN(id) ? null : id
})
const isSelf = computed(() => profile.value?.id === userStore.userInfo?.id)

const profileCache = new Map<number, { data: UserProfileVO; timestamp: number }>()
const CACHE_DURATION = 5 * 60 * 1000

function getCachedProfile(id: number): UserProfileVO | null {
  const cached = profileCache.get(id)
  if (cached && Date.now() - cached.timestamp < CACHE_DURATION) {
    return cached.data
  }
  return null
}

function setCachedProfile(id: number, data: UserProfileVO) {
  profileCache.set(id, { data, timestamp: Date.now() })
}

const editDialogVisible = ref(false)
const editForm = reactive({ nickname: '', bio: '', avatarUrl: '', messagePrivacy: 'EVERYONE' })
const editLoading = ref(false)
const uploadingAvatar = ref(false)

const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const passwordLoading = ref(false)
const passwordDialogVisible = ref(false)

// 注销账号 - 多步验证
const deleteDialogVisible = ref(false)
const deleteStep = ref<'slider' | 'sms'>('slider')
const deleteLoading = ref(false)
const sliderPassed = ref(false)
const smsCode = ref('')
const smsSending = ref(false)
const smsCountdown = ref(0)
let smsTimer: ReturnType<typeof setInterval> | null = null

function clearSmsTimer() {
  if (smsTimer) {
    clearInterval(smsTimer)
    smsTimer = null
  }
}

function openEditDialog() {
  editForm.nickname = profile.value?.nickname || ''
  editForm.bio = profile.value?.bio || ''
  editForm.avatarUrl = profile.value?.avatarUrl || ''
  editForm.messagePrivacy = profile.value?.messagePrivacy || 'EVERYONE'
  editDialogVisible.value = true
}

function openPasswordDialog() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordDialogVisible.value = true
}

async function handleAvatarUpload(uploadEvent: any) {
  const file = uploadEvent.raw
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    return
  }
  uploadingAvatar.value = true
  try {
    const res = await uploadFile(file)
    editForm.avatarUrl = res.data.data
    ElMessage.success('头像上传成功')
  } catch {
    ElMessage.error('上传失败，请重试')
  } finally {
    uploadingAvatar.value = false
  }
}

async function handleEditSubmit() {
  if (!profile.value?.id) return
  editLoading.value = true
  try {
    await updateUser(profile.value.id, {
      nickname: editForm.nickname,
      bio: editForm.bio,
      avatarUrl: editForm.avatarUrl,
      messagePrivacy: editForm.messagePrivacy,
    })
    userStore.userInfo!.nickname = editForm.nickname
    userStore.userInfo!.avatarUrl = editForm.avatarUrl
    profile.value.nickname = editForm.nickname
    profile.value.bio = editForm.bio
    profile.value.avatarUrl = editForm.avatarUrl
    profile.value.messagePrivacy = editForm.messagePrivacy
    profileCache.delete(profile.value.id)
    ElMessage.success('保存成功')
    editDialogVisible.value = false
  } catch {
  } finally {
    editLoading.value = false
  }
}

async function handleChangePassword() {
  if (!profile.value?.id) return
  if (!passwordForm.oldPassword || !passwordForm.newPassword || !passwordForm.confirmPassword) {
    ElMessage.warning('请填写完整的密码信息')
    return
  }
  if (passwordForm.newPassword.length < 6) {
    ElMessage.warning('新密码长度不能少于6位')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  passwordLoading.value = true
  try {
    await changePassword(profile.value.id, passwordForm.oldPassword, passwordForm.newPassword)
    ElMessage.success('密码修改成功')
    passwordDialogVisible.value = false
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
  } catch {
  } finally {
    passwordLoading.value = false
  }
}

async function handleDeleteAccount() {
  // 第一步：确认弹窗
  try {
    await ElMessageBox.confirm(
      '账号注销后将无法恢复，确定要注销吗？',
      '注销账号',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  // 第二步：打开滑块验证对话框
  deleteStep.value = 'slider'
  sliderPassed.value = false
  smsCode.value = ''
  smsCountdown.value = 0
  clearSmsTimer()
  deleteDialogVisible.value = true
}

function handleSliderSuccess() {
  sliderPassed.value = true
  deleteStep.value = 'sms'
}

function handleSliderFail() {
  ElMessage.warning('验证失败，请重试')
}

async function handleSendDeleteCode() {
  smsSending.value = true
  try {
    await sendDeleteCode()
    ElMessage.success('验证码已发送')
    smsCountdown.value = 60
    clearSmsTimer()
    smsTimer = setInterval(() => {
      smsCountdown.value--
      if (smsCountdown.value <= 0) {
        clearSmsTimer()
      }
    }, 1000)
  } catch {
  } finally {
    smsSending.value = false
  }
}

async function handleConfirmDelete() {
  if (!smsCode.value) {
    ElMessage.warning('请输入验证码')
    return
  }
  deleteLoading.value = true
  try {
    await deleteAccount({ code: smsCode.value })
    ElMessage.success('账号已注销')
    deleteDialogVisible.value = false
    clearSmsTimer()
    userStore.logout()
    router.push('/login')
  } catch {
  } finally {
    deleteLoading.value = false
  }
}

function handleDeleteDialogClose() {
  clearSmsTimer()
}

async function loadAllData() {
  if (!userId.value) {
    ElMessage.error('用户ID无效')
    router.push('/')
    return
  }

  loading.value = true

  try {
    const cachedProfile = getCachedProfile(userId.value)

    const [profileResult, postsResult] = await Promise.allSettled([
      cachedProfile
        ? Promise.resolve({ data: { data: cachedProfile } })
        : getUserProfile(userId.value),
      getUserPosts(userId.value, { page: 0, size: 12 })
    ])

    if (profileResult.status === 'fulfilled') {
      profile.value = profileResult.value.data.data
      if (!cachedProfile) {
        setCachedProfile(userId.value, profileResult.value.data.data)
      }
    } else {
      throw new Error('获取用户信息失败')
    }

    if (postsResult.status === 'fulfilled') {
      const data: PageData<PostVO> = postsResult.value.data.data
      posts.value = data.content
      currentPage.value = data.number
      totalPages.value = data.totalPages
    }

  } catch (error: any) {
    console.error('加载个人主页失败:', error)
    ElMessage.error(error.message || '加载失败，请重试')
    router.push('/')
  } finally {
    loading.value = false
  }
}

async function loadMorePosts(page: number) {
  if (!userId.value) return
  try {
    const res = await getUserPosts(userId.value, { page, size: 12 })
    const data: PageData<PostVO> = res.data.data
    posts.value.push(...data.content)
    currentPage.value = data.number
    totalPages.value = data.totalPages
  } catch {
  }
}

async function handleFollow() {
  if (!userId.value) return
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  try {
    if (profile.value?.followed) {
      await unfollow(userId.value)
      profile.value!.followed = false
      profile.value!.followerCount--
    } else {
      await follow(userId.value)
      profile.value!.followed = true
      profile.value!.followerCount++
    }
    profileCache.delete(userId.value!)
  } catch {
  }
}

function openChat() {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  router.push(`/messages/${userId.value}`)
}

watch(userId, (newId) => {
  if (newId) {
    loadAllData()
  }
})

onMounted(() => {
  if (userId.value) {
    loadAllData()
  }
})
</script>

<template>
  <div class="profile-page" v-loading="loading">
    <template v-if="profile">
      <!-- 用户信息卡片 -->
      <el-card class="profile-card" shadow="always">
        <div class="profile-header">
          <el-avatar :size="80" :src="profile.avatarUrl || undefined">
            {{ profile.nickname?.charAt(0) }}
          </el-avatar>
          <div class="profile-info">
            <h2>{{ profile.nickname }}</h2>
            <p class="bio">{{ profile.bio || '这个人很懒，什么都没写~' }}</p>
            <div class="stats-row">
              <div class="stat-item">
                <strong>{{ profile.postCount || 0 }}</strong>
                <span>帖子</span>
              </div>
              <div class="stat-item">
                <strong>{{ profile.followerCount }}</strong>
                <span>粉丝</span>
              </div>
              <div class="stat-item">
                <strong>{{ profile.followingCount }}</strong>
                <span>关注</span>
              </div>
            </div>
          </div>
          <div class="profile-actions">
            <template v-if="!isSelf">
              <el-button
                :type="profile.followed ? 'default' : 'primary'"
                @click="handleFollow"
              >
                {{ profile.followed ? '已关注' : '关注' }}
              </el-button>
              <el-button @click="openChat">
                <el-icon><ChatDotRound /></el-icon>
                私信
              </el-button>
            </template>
            <template v-else>
              <el-button type="primary" @click="openEditDialog">编辑资料</el-button>
              <el-button @click="openPasswordDialog">修改密码</el-button>
              <el-button type="danger" @click="handleDeleteAccount">注销账号</el-button>
            </template>
            <el-button @click="router.back()">返回</el-button>
          </div>
        </div>
      </el-card>

      <!-- 帖子列表 -->
      <div class="post-grid">
        <PostCard v-for="post in posts" :key="post.id" :post="post" />
      </div>

      <div v-if="currentPage + 1 < totalPages" class="load-more">
        <el-button @click="loadMorePosts(currentPage + 1)">加载更多</el-button>
      </div>

      <el-empty v-if="posts.length === 0" :description="isSelf ? '还没有发布帖子，去发布第一篇吧' : 'TA还没有发布帖子'">
        <el-button v-if="isSelf" type="primary" @click="router.push('/create')">
          发布帖子
        </el-button>
      </el-empty>
    </template>

    <!-- 编辑资料对话框 -->
    <el-dialog v-model="editDialogVisible" title="编辑资料" width="520px">
      <div class="edit-dialog-body">
        <!-- 头像上传 -->
        <div class="avatar-upload-section">
          <el-avatar :size="72" :src="editForm.avatarUrl || undefined" class="avatar-preview">
            {{ editForm.nickname?.charAt(0) }}
          </el-avatar>
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            accept="image/*"
            @change="handleAvatarUpload"
            :disabled="uploadingAvatar"
          >
            <el-button size="small" :loading="uploadingAvatar">
              {{ uploadingAvatar ? '上传中...' : '更换头像' }}
            </el-button>
          </el-upload>
        </div>

        <el-form label-position="top">
          <el-form-item label="手机号">
            <el-input :model-value="userStore.userInfo?.phone || ''" disabled />
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model="editForm.nickname" maxlength="20" show-word-limit />
          </el-form-item>
          <el-form-item label="简介">
            <el-input
              v-model="editForm.bio"
              type="textarea"
              :rows="3"
              placeholder="这个人很懒，什么都没写~"
              maxlength="100"
              show-word-limit
            />
          </el-form-item>
        </el-form>

        <!-- 私信隐私设置 -->
        <div class="privacy-section">
          <el-form-item label="谁可以私信我">
            <el-radio-group v-model="editForm.messagePrivacy">
              <el-radio value="EVERYONE">所有人</el-radio>
              <el-radio value="FOLLOWERS_ONLY">仅关注的人</el-radio>
              <el-radio value="NONE">关闭私信</el-radio>
            </el-radio-group>
          </el-form-item>
        </div>
      </div>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editLoading" @click="handleEditSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码对话框 -->
    <el-dialog v-model="passwordDialogVisible" title="修改密码" width="420px">
      <el-form label-position="top">
        <el-form-item label="原密码">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="至少6位字符" minlength="6" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="warning" :loading="passwordLoading" @click="handleChangePassword">确认修改</el-button>
      </template>
    </el-dialog>

    <!-- 注销账号对话框 -->
    <el-dialog v-model="deleteDialogVisible" title="注销账号" width="420px" :close-on-click-modal="false" @close="handleDeleteDialogClose">
      <template v-if="deleteStep === 'slider'">
        <div class="delete-step">
          <el-steps :active="1" align-center finish-status="process" style="margin-bottom: 24px;">
            <el-step title="滑块验证" />
            <el-step title="手机验证" />
          </el-steps>
          <el-form label-position="top">
            <el-form-item label="请完成滑块验证">
              <SliderCaptcha :visible="true" inline @success="handleSliderSuccess" @fail="handleSliderFail" />
            </el-form-item>
          </el-form>
        </div>
      </template>
      <template v-if="deleteStep === 'sms'">
        <div class="delete-step">
          <el-steps :active="2" align-center finish-status="finish" style="margin-bottom: 24px;">
            <el-step title="人机验证" />
            <el-step title="手机验证" />
          </el-steps>
          <el-form label-position="top">
            <el-form-item label="短信验证码">
              <div class="sms-row">
                <el-input v-model="smsCode" placeholder="请输入6位验证码" maxlength="6" style="flex: 1" @keyup.enter="handleConfirmDelete" />
                <el-button
                  :disabled="smsCountdown > 0"
                  :loading="smsSending"
                  @click="handleSendDeleteCode"
                >
                  {{ smsCountdown > 0 ? `${smsCountdown}s` : '发送验证码' }}
                </el-button>
              </div>
            </el-form-item>
          </el-form>
        </div>
      </template>
      <template #footer>
        <el-button @click="deleteDialogVisible = false">取消</el-button>
        <el-button
          v-if="deleteStep === 'sms'"
          type="danger"
          :loading="deleteLoading"
          :disabled="!smsCode"
          @click="handleConfirmDelete"
        >
          确定注销
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* 页面入场动画 */
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(24px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.profile-page {
  max-width: 1000px;
  margin: 0 auto;
  animation: fadeInUp 0.5s ease-out;
}

/* profile-card 优化：圆角、柔和阴影、白色背景 */
.profile-card {
  margin-bottom: 28px;
  border-radius: 20px;
  border: none;
  background: #fff;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.04);
  animation: fadeInUp 0.5s ease-out;
}

.profile-card :deep(.el-card__body) {
  border-radius: 20px;
}

/* profile-header 优化：头像边框环、信息区间距更宽松 */
.profile-header {
  display: flex;
  gap: 28px;
  align-items: flex-start;
}

.profile-header .el-avatar {
  border: 3px solid #f0f0f0;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  flex-shrink: 0;
}

.profile-info {
  flex: 1;
}

.profile-info h2 {
  margin: 0 0 10px;
  font-size: 22px;
  font-weight: 700;
  color: #1a1a1a;
}

.bio {
  color: #888;
  margin: 0 0 18px;
  font-size: 14px;
  line-height: 1.6;
}

/* stats-row 优化：stat-item 加背景色区分、圆角卡片样式 */
.stats-row {
  display: flex;
  gap: 12px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  font-size: 14px;
  background: #f7f8fa;
  border-radius: 12px;
  padding: 12px 24px;
  min-width: 80px;
  transition: background 0.2s, transform 0.2s;
}

.stat-item:hover {
  background: #eef1f5;
  transform: translateY(-2px);
}

.stat-item strong {
  font-size: 20px;
  color: #1a1a1a;
  font-weight: 700;
  margin-bottom: 2px;
}

.stat-item span {
  color: #999;
  font-size: 12px;
  letter-spacing: 0.5px;
}

/* profile-actions 优化：按钮间距 */
.profile-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-end;
  flex-shrink: 0;
}

.profile-actions .el-button {
  min-width: 100px;
  border-radius: 10px;
}

/* post-grid 优化：卡片间距和圆角 */
.post-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 20px;
  animation: fadeInUp 0.6s ease-out 0.1s both;
}

.post-grid :deep(.post-card) {
  border-radius: 16px;
  overflow: hidden;
  transition: transform 0.25s, box-shadow 0.25s;
}

.post-grid :deep(.post-card):hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.load-more {
  text-align: center;
  margin-top: 32px;
}

.load-more .el-button {
  border-radius: 10px;
  padding: 10px 32px;
}

/* 编辑资料对话框优化 */
.edit-dialog-body {
  padding: 12px 0;
}

.avatar-upload-section {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 28px;
  padding-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
}

.avatar-preview {
  flex-shrink: 0;
  border: 2px solid #f0f0f0;
}

.edit-dialog-body :deep(.el-form-item) {
  margin-bottom: 20px;
}

.edit-dialog-body :deep(.el-form-item__label) {
  font-weight: 600;
  color: #333;
  padding-bottom: 4px;
}

.edit-dialog-body :deep(.el-input__wrapper),
.edit-dialog-body :deep(.el-textarea__inner) {
  border-radius: 10px;
}

.privacy-section {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
}

/* 对话框整体圆角 */
:deep(.el-dialog) {
  border-radius: 16px;
}

:deep(.el-dialog__header) {
  padding: 20px 24px 12px;
}

:deep(.el-dialog__body) {
  padding: 8px 24px;
}

:deep(.el-dialog__footer) {
  padding: 12px 24px 20px;
}

/* 注销对话框 */
.sms-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.delete-step {
  padding: 8px 0;
}
</style>

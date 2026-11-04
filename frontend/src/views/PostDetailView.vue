<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPostDetail, deletePost } from '@/api/post'
import { likePost, unlikePost } from '@/api/like'
import { favoritePost, unfavoritePost } from '@/api/favorite'
import { useUserStore } from '@/stores/user'
import type { PostDetailVO, CommentVO, UserVO } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Loading, Picture, Warning } from '@element-plus/icons-vue'
import AuthorBar from '@/components/AuthorBar.vue'
import CommentSection from '@/components/CommentSection.vue'
import { sanitizeHtml } from '@/utils/sanitize'
import { createReport } from '@/api/report'
import { getFollowings } from '@/api/user'
import { sendMessage } from '@/api/message'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const post = ref<PostDetailVO | null>(null)
const loading = ref(true)
const commentsLoadFailed = ref(false)
const imageLoadingStates = ref<boolean[]>([])
const commentSectionRef = ref<InstanceType<typeof CommentSection> | null>(null)
const currentImageIndex = ref(0)

const currentTab = ref<'content' | 'comments'>('content')

const postId = computed(() => Number(route.params.id))
const isAuthor = computed(() => post.value?.userId === userStore.userInfo?.id)
const isDisabled = computed(() => post.value?.status === 'DISABLED' || post.value?.status === 'APPEALING')
const sanitizedContent = computed(() => post.value ? sanitizeHtml(post.value.content) : '')
const hasImages = computed(() => post.value?.images && post.value.images.length > 0)

function switchTab(tab: 'content' | 'comments') {
  currentTab.value = tab
}

function onImageLoad(index: number) {
  imageLoadingStates.value[index] = false
}

function onImageError(index: number) {
  imageLoadingStates.value[index] = false
  const img = post.value?.images?.[index]
  if (img && !img.includes('/default-cover.png')) {
    post.value!.images![index] = '/default-cover.png'
  }
}

async function loadPost() {
  loading.value = true
  try {
    const res = await getPostDetail(postId.value)
    post.value = res.data.data
    if (post.value?.images) {
      imageLoadingStates.value = post.value.images.map(() => true)
    }
  } catch {
    ElMessage.error('帖子不存在')
    router.push('/')
  } finally {
    loading.value = false
  }
}

function handleCommentsReload() {
  commentSectionRef.value?.loadComments()
}

async function handleLike() {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  
  const previousLiked = post.value!.liked
  const previousLikeCount = post.value!.likeCount
  
  post.value!.liked = !post.value!.liked
  post.value!.likeCount += post.value!.liked ? 1 : -1
  
  try {
    if (post.value!.liked) {
      await likePost(postId.value)
    } else {
      await unlikePost(postId.value)
    }
  } catch {
    post.value!.liked = previousLiked
    post.value!.likeCount = previousLikeCount
    ElMessage.error('操作失败，请重试')
  }
}

async function handleFavorite() {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }

  const previousFavorited = post.value!.favorited
  const previousFavoriteCount = post.value!.favoriteCount

  post.value!.favorited = !post.value!.favorited
  post.value!.favoriteCount += post.value!.favorited ? 1 : -1

  try {
    if (post.value!.favorited) {
      await favoritePost(postId.value)
    } else {
      await unfavoritePost(postId.value)
    }
  } catch {
    post.value!.favorited = previousFavorited
    post.value!.favoriteCount = previousFavoriteCount
    ElMessage.error('操作失败，请重试')
  }
}

async function handleDelete() {
  await ElMessageBox.confirm('确定删除这条帖子吗？', '提示', { type: 'warning' })
  try {
    await deletePost(postId.value)
    ElMessage.success('已删除')
    router.push('/')
  } catch {
  }
}

function handleEdit() {
  router.push(`/post/${postId.value}/edit`)
}

const reportDialogVisible = ref(false)
const reportReason = ref('')
const reportSubmitting = ref(false)
const reportReasons = [
  '色情低俗',
  '广告骚扰',
  '虚假信息',
  '违法违规',
  '侵权抄袭',
  '引战骂战',
  '其他',
]

async function handleReport() {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  reportDialogVisible.value = true
  reportReason.value = ''
}

async function submitReport() {
  if (!reportReason.value.trim()) {
    ElMessage.warning('请选择或输入举报原因')
    return
  }
  reportSubmitting.value = true
  try {
    await createReport({
      targetId: postId.value,
      targetType: 'POST',
      reason: reportReason.value.trim(),
    })
    ElMessage.success('举报已提交，我们会尽快处理')
    reportDialogVisible.value = false
  } catch {
    // 错误已由后端返回，拦截器显示
  } finally {
    reportSubmitting.value = false
  }
}

const shareDialogVisible = ref(false)
const shareFollowingList = ref<UserVO[]>([])
const shareLoading = ref(false)
const shareSending = ref(false)
const shareSelectedUser = ref<UserVO | null>(null)

async function handleShare() {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  shareDialogVisible.value = true
  shareSelectedUser.value = null
  shareLoading.value = true
  try {
    const res = await getFollowings(userStore.userInfo!.id)
    shareFollowingList.value = res.data.data.content
  } catch {
    ElMessage.error('获取关注列表失败')
  } finally {
    shareLoading.value = false
  }
}

function selectUserForShare(user: UserVO) {
  shareSelectedUser.value = user
}

async function sendShareMessage() {
  if (!shareSelectedUser.value) {
    ElMessage.warning('请选择要分享的用户')
    return
  }
  shareSending.value = true
  try {
    const shareContent = JSON.stringify({
      postId: postId.value,
      title: post.value?.title || ''
    })
    await sendMessage(shareSelectedUser.value.id, shareContent, 'SHARE')
    ElMessage.success('分享成功')
    shareDialogVisible.value = false
  } catch {
    ElMessage.error('分享失败，请重试')
  } finally {
    shareSending.value = false
  }
}

onMounted(() => {
  loadPost()
})
</script>

<template>
  <div class="post-detail-page" v-loading="loading">
    <template v-if="post">
      <!-- 封禁提示（非作者看到） -->
      <div v-if="isDisabled && !isAuthor" class="disabled-banner">
        <div class="disabled-banner-content">
          <svg viewBox="0 0 24 24" width="48" height="48" fill="none">
            <circle cx="12" cy="12" r="10" stroke="#ff4d4f" stroke-width="2"/>
            <path d="M12 8v4M12 16h.01" stroke="#ff4d4f" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <h2 class="disabled-title">该帖子已被封禁</h2>
          <p class="disabled-desc" v-if="post.status === 'APPEALING'">作者已提交申诉，正在审核中</p>
          <p class="disabled-desc" v-else>该帖子因违反社区规范已被封禁，无法查看内容</p>
          <el-button type="primary" @click="router.push('/')">返回首页</el-button>
        </div>
      </div>

      <!-- 主内容区 -->
      <main v-else class="detail-main">
        <!-- 左侧：图片+标题区域 -->
        <section class="media-section">
          <div class="image-showcase" v-if="hasImages">
            <div class="main-image-wrapper">
              <el-image
                :src="post.images[currentImageIndex]"
                :preview-src-list="post.images"
                :initial-index="currentImageIndex"
                class="main-image"
                fit="contain"
                @load="onImageLoad(currentImageIndex)"
                @error="onImageError(currentImageIndex)"
              >
                <template #placeholder>
                  <div class="image-placeholder">
                    <el-icon class="is-loading" :size="40"><Loading /></el-icon>
                  </div>
                </template>
                <template #error>
                  <div class="image-placeholder">
                    <el-icon :size="48"><Picture /></el-icon>
                  </div>
                </template>
              </el-image>
              
              <div class="image-overlay-gradient" />
              
              <div class="image-counter" v-if="post.images.length > 1">
                <span>{{ currentImageIndex + 1 }} / {{ post.images.length }}</span>
              </div>

              <button 
                class="nav-arrow prev" 
                v-if="currentImageIndex > 0"
                @click="currentImageIndex--"
              >
                <svg viewBox="0 0 24 24"><path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"/></svg>
              </button>
              <button 
                class="nav-arrow next" 
                v-if="currentImageIndex < post.images.length - 1"
                @click="currentImageIndex++"
              >
                <svg viewBox="0 0 24 24"><path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"/></svg>
              </button>
            </div>

            <div class="thumbnail-list" v-if="post.images.length > 1">
              <div 
                v-for="(img, i) in post.images" 
                :key="i"
                class="thumbnail-item"
                :class="{ active: i === currentImageIndex }"
                @click="currentImageIndex = i"
              >
                <el-image :src="img" fit="cover" />
              </div>
            </div>
          </div>

          <div v-else class="no-image-placeholder">
            <svg viewBox="0 0 24 24" class="placeholder-icon">
              <path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z"/>
            </svg>
            <span class="placeholder-text">暂无图片</span>
          </div>

          <div class="post-meta-info">
            <div class="meta-row">
              <AuthorBar
                :author-name="post.authorName"
                :author-avatar="post.authorAvatar"
                :user-id="post.userId"
                :is-author="isAuthor"
                :created-at="post.createdAt"
                @delete="handleDelete"
                @edit="handleEdit"
              />
              <span class="topic-badge" v-if="post.topic">
                <svg viewBox="0 0 24 24" class="badge-icon">
                  <path d="M20 2H4c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-1 18H5V4h14v16zm-7-2h2v-4h4v-2h-4V8h-2v4H8v2h4z"/>
                </svg>
                {{ post.topic }}
              </span>
            </div>
          </div>
        </section>

        <!-- 右侧固定面板：操作按钮+内容/评论切换 -->
        <aside class="fixed-right-panel">
          <!-- 操作按钮组（垂直排列） -->
          <nav class="tab-navigation">
            <button class="tab-btn back-tab-btn" @click="router.back()">
              <div class="btn-icon-wrapper">
                <svg viewBox="0 0 24 24" class="tab-icon">
                  <path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/>
                </svg>
              </div>
              <span class="btn-label">返回</span>
            </button>

            <button class="tab-btn" :class="{ active: currentTab === 'content' }" @click="switchTab('content')">
              <div class="btn-icon-wrapper">
                <svg viewBox="0 0 24 24" class="tab-icon">
                  <path d="M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11z"/>
                </svg>
              </div>
              <span class="btn-label">内容</span>
            </button>

            <button class="tab-btn" :class="{ active: currentTab === 'comments' }" @click="switchTab('comments')">
              <div class="btn-icon-wrapper">
                <svg viewBox="0 0 24 24" class="tab-icon">
                  <path d="M20 2H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h14l4 4V4c0-1.1-.9-2-2-2zm-2 12H6v-2h12v2zm0-3H6V9h12v2zm0-3H6V6h12v2z"/>
                </svg>
              </div>
              <span class="btn-label">{{ post.commentCount || '评论' }}</span>
            </button>

            <button class="tab-btn like-tab-btn" :class="{ liked: post.liked }" @click="handleLike">
              <div class="btn-icon-wrapper">
                <svg viewBox="0 0 24 24" class="tab-icon heart-icon" :class="{ filled: post.liked }">
                  <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>
                </svg>
              </div>
              <span class="btn-label">{{ post.likeCount || '点赞' }}</span>
            </button>

            <button class="tab-btn favorite-tab-btn" :class="{ favorited: post.favorited }" @click="handleFavorite">
              <div class="btn-icon-wrapper">
                <svg viewBox="0 0 24 24" class="tab-icon star-icon" :class="{ filled: post.favorited }">
                  <path d="M17 3H7c-1.1 0-2 .9-2 2v16l7-3 7 3V5c0-1.1-.9-2-2-2z"/>
                </svg>
              </div>
              <span class="btn-label">{{ post.favoriteCount || '收藏' }}</span>
            </button>

            <button class="tab-btn report-tab-btn" @click="handleReport" v-if="!isAuthor">
              <div class="btn-icon-wrapper">
                <svg viewBox="0 0 24 24" class="tab-icon">
                  <path d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z"/>
                </svg>
              </div>
              <span class="btn-label">举报</span>
            </button>

            <button class="tab-btn share-tab-btn" @click="handleShare">
              <div class="btn-icon-wrapper">
                <svg viewBox="0 0 24 24" class="tab-icon">
                  <path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92s2.92-1.31 2.92-2.92-1.31-2.92-2.92-2.92z"/>
                </svg>
              </div>
              <span class="btn-label">分享</span>
            </button>
          </nav>

          <!-- 内容/评论切换面板 -->
          <div class="content-switcher">
            <transition name="fade-slide" mode="out-in">
              <article v-if="currentTab === 'content'" key="content" class="panel-content">
                <h1 class="post-title">{{ post.title }}</h1>
                <div class="post-body" v-html="sanitizedContent"></div>
              </article>

              <section v-else key="comments" class="panel-comments">
                <CommentSection
                  ref="commentSectionRef"
                  :post-id="postId"
                  :post-author-id="post.userId"
                  :comment-count="post.commentCount"
                  :load-failed="commentsLoadFailed"
                  @update:comment-count="post!.commentCount = $event"
                  @reload="handleCommentsReload"
                />
              </section>
            </transition>
          </div>
        </aside>
      </main>
    </template>

    <el-dialog v-model="reportDialogVisible" title="举报帖子" width="440px" :close-on-click-modal="false">
      <div class="report-form">
        <p class="report-hint">请选择举报原因：</p>
        <div class="report-reasons">
          <el-tag
            v-for="r in reportReasons"
            :key="r"
            :type="reportReason === r ? 'danger' : 'info'"
            :effect="reportReason === r ? 'dark' : 'plain'"
            class="report-reason-tag"
            @click="reportReason = r"
          >
            {{ r }}
          </el-tag>
        </div>
        <el-input
          v-model="reportReason"
          placeholder="或输入其他原因"
          maxlength="200"
          show-word-limit
          style="margin-top: 12px"
        />
      </div>
      <template #footer>
        <el-button @click="reportDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="reportSubmitting" @click="submitReport">提交举报</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="shareDialogVisible" title="分享给好友" width="440px" :close-on-click-modal="false">
      <div class="share-form">
        <div class="share-user-list" v-loading="shareLoading">
          <div v-if="shareFollowingList.length === 0 && !shareLoading" class="no-result">
            你还没有关注任何人
          </div>
          <div
            v-for="user in shareFollowingList"
            :key="user.id"
            class="share-user-item"
            :class="{ selected: shareSelectedUser?.id === user.id }"
            @click="selectUserForShare(user)"
          >
            <el-avatar :size="40" :src="user.avatarUrl || undefined">
              {{ user.nickname?.charAt(0) }}
            </el-avatar>
            <div class="user-info">
              <div class="user-name">{{ user.nickname }}</div>
              <div class="user-bio">{{ user.bio || '这个人很懒，什么都没写' }}</div>
            </div>
            <div v-if="shareSelectedUser?.id === user.id" class="selected-mark">
              <svg viewBox="0 0 24 24" width="20" height="20">
                <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z" fill="#ff2442"/>
              </svg>
            </div>
          </div>
        </div>
        <div v-if="shareSelectedUser" class="share-preview">
          <span class="preview-label">将分享给：</span>
          <span class="preview-name">{{ shareSelectedUser.nickname }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="shareDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="shareSending" :disabled="!shareSelectedUser" @click="sendShareMessage">
          发送分享
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.detail-main {
  max-width: 1200px;
  margin: 0 auto;
  padding: 10px 12px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  position: relative;
  min-height: calc(100vh - 100px);
  align-items: stretch;
}

.post-detail-page {
  min-height: calc(100vh - 160px);
  background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf1 100%);
  animation: pageEntry 0.5s cubic-bezier(0.23, 1, 0.32, 1);
  margin-top: -15px;
}

@keyframes pageEntry {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.media-section {
  background: white;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
  animation: slideInLeft 0.6s ease-out 0.1s both;
  display: flex;
  flex-direction: column;
}

@keyframes slideInLeft {
  from {
    opacity: 0;
    transform: translateX(-30px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.image-showcase {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
  background: #fafafa;
}

.image-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf1 100%);
  color: #b0bec5;
}

.no-image-placeholder {
  height: 400px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf1 100%);
}

.main-image-wrapper {
  position: relative;
  width: 300px;
  height: 400px;
  background: #fafafa;
  display: flex;
  align-items: center;
  justify-content: center;
}

.main-image {
  width: 100%;
  height: 100%;
}

.image-overlay-gradient {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 120px;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.08), transparent);
  pointer-events: none;
}

.image-counter {
  position: absolute;
  top: 20px;
  right: 20px;
  padding: 8px 16px;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(8px);
  border-radius: 20px;
  color: white;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.5px;
}

.nav-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 44px;
  height: 44px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border: none;
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.25s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.nav-arrow:hover {
  background: white;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.18);
  transform: translateY(-50%) scale(1.05);
}

.nav-arrow.prev {
  left: 16px;
}

.nav-arrow.next {
  right: 16px;
}

.nav-arrow svg {
  width: 22px;
  height: 22px;
  fill: #374151;
}

.thumbnail-list {
  display: flex;
  gap: 10px;
  padding: 16px;
  background: #fafafa;
  overflow-x: auto;
  border-top: 1px solid #f0f0f0;
}

.thumbnail-list::-webkit-scrollbar {
  height: 4px;
}

.thumbnail-list::-webkit-scrollbar-thumb {
  background: #d0d0d0;
  border-radius: 2px;
}

.thumbnail-item {
  flex-shrink: 0;
  width: 72px;
  height: 72px;
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  border: 3px solid transparent;
  transition: all 0.25s ease;
  opacity: 0.65;
}

.thumbnail-item:hover {
  opacity: 0.9;
  transform: translateY(-2px);
}

.thumbnail-item.active {
  border-color: #ff2442;
  opacity: 1;
  box-shadow: 0 4px 12px rgba(255, 36, 66, 0.25);
}

.thumbnail-item .el-image {
  width: 100%;
  height: 100%;
}

.placeholder-icon {
  width: 80px;
  height: 80px;
  fill: #b0bec5;
  opacity: 0.6;
}

.placeholder-text {
  font-size: 16px;
  color: #90a4ae;
  font-weight: 500;
}

.post-meta-info {
  padding: 16px 20px;
  border-top: 1px solid #f5f5f5;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.post-title {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.5;
  color: #1a1a1a;
  margin: 0 0 16px 0;
  letter-spacing: -0.3px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 5;
  -webkit-box-orient: vertical;
  word-break: break-word;
}

.meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.topic-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: linear-gradient(135deg, #fff0f2 0%, #ffe4e8 100%);
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  color: #ff2442;
  white-space: nowrap;
}

.badge-icon {
  width: 16px;
  height: 16px;
  fill: currentColor;
}

.fixed-right-panel {
  background: white;
  border-radius: 20px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
  animation: slideInRight 0.6s ease-out 0.2s both;
  display: flex;
  overflow: hidden;
}

@keyframes slideInRight {
  from {
    opacity: 0;
    transform: translateX(30px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.tab-navigation {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 8px;
  background: #fafafa;
  border-right: 1px solid #f0f0f0;
  flex-shrink: 0;
  width: 72px;
  align-items: center;
}

.tab-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 10px 6px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #f8f9fa 0%, #f1f3f5 100%);
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
  position: relative;
  overflow: hidden;
  width: 52px;
}

.tab-btn::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: #ff2442;
  transform: scaleY(0);
  transition: transform 0.25s ease;
  border-radius: 0 3px 3px 0;
}

.tab-btn.active::before,
.like-tab-btn.liked::before,
.favorite-tab-btn.favorited::before {
  transform: scaleY(1);
}

.tab-btn:hover {
  transform: translateX(-3px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.tab-btn.active {
  background: linear-gradient(135deg, #fff0f2 0%, #ffe4e8 100%);
  box-shadow: 0 4px 16px rgba(255, 36, 66, 0.12);
}

.btn-icon-wrapper {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: white;
  transition: all 0.3s ease;
  color: #6b7280;
  flex-shrink: 0;
}

.tab-btn.active .btn-icon-wrapper {
  color: #ff2442;
  box-shadow: 0 2px 8px rgba(255, 36, 66, 0.15);
}

.tab-icon {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  transition: all 0.3s ease;
}

.tab-btn.active .tab-icon {
  fill: currentColor;
  stroke: currentColor;
}

.heart-icon.filled {
  fill: #ff2442;
  stroke: #ff2442;
}

.star-icon.filled {
  fill: #f59e0b;
  stroke: #f59e0b;
}

.like-tab-btn.liked {
  background: linear-gradient(135deg, #fff0f2 0%, #ffe4e8 100%);
  box-shadow: 0 4px 16px rgba(255, 36, 66, 0.15);
}

.like-tab-btn.liked .btn-icon-wrapper {
  color: #ff2442;
}

.favorite-tab-btn.favorited {
  background: linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);
  box-shadow: 0 4px 16px rgba(245, 158, 11, 0.15);
}

.favorite-tab-btn.favorited .btn-icon-wrapper {
  color: #f59e0b;
}

.btn-label {
  font-size: 12px;
  font-weight: 600;
  color: #374151;
  letter-spacing: 0.2px;
}

.tab-btn.active .btn-label,
.like-tab-btn.liked .btn-label {
  color: #ff2442;
}

.favorite-tab-btn.favorited .btn-label {
  color: #f59e0b;
}

.content-switcher {
  flex: 1;
  overflow-y: auto;
  border-radius: 12px;
  background: #fafafa;
  min-height: 0;
}

.content-switcher:has(.panel-comments) {
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.content-switcher::-webkit-scrollbar {
  width: 5px;
}

.content-switcher::-webkit-scrollbar-track {
  background: transparent;
}

.content-switcher::-webkit-scrollbar-thumb {
  background: #d0d0d0;
  border-radius: 3px;
}

.panel-content {
  padding: 24px;
  min-height: 300px;
}

.panel-comments {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  padding: 16px 16px 0 16px;
  overflow: hidden;
}

.post-body {
  font-size: 14px;
  line-height: 1.8;
  color: #333;
  white-space: pre-wrap;
  word-break: break-word;
}

.post-body :deep(p) {
  margin: 0 0 14px;
}

.post-body :deep(img) {
  max-width: 100%;
  border-radius: 8px;
  margin: 12px 0;
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(12px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-12px);
}

.report-tab-btn .btn-icon-wrapper {
  color: #9ca3af;
}

.report-tab-btn:hover .btn-icon-wrapper {
  color: #f59e0b;
}

.report-tab-btn:hover {
  background: linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);
}

.report-form {
  padding: 4px 0;
}

.report-hint {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0 0 12px 0;
}

.report-reasons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.report-reason-tag {
  cursor: pointer;
  transition: all 0.2s;
}

.report-reason-tag:hover {
  transform: translateY(-1px);
}

.disabled-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 160px);
  background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf1 100%);
  animation: pageEntry 0.5s cubic-bezier(0.23, 1, 0.32, 1);
}

.disabled-banner-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 60px 40px;
  background: white;
  border-radius: 20px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
  text-align: center;
  max-width: 400px;
}

.disabled-title {
  font-size: 20px;
  font-weight: 700;
  color: #ff4d4f;
  margin: 0;
}

.disabled-desc {
  font-size: 14px;
  color: #909399;
  margin: 0;
  line-height: 1.6;
}

.share-form {
  min-height: 200px;
}

.share-user-list {
  max-height: 280px;
  overflow-y: auto;
  margin-top: 12px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}

.share-user-list::-webkit-scrollbar {
  width: 4px;
}

.share-user-list::-webkit-scrollbar-thumb {
  background: #d0d0d0;
  border-radius: 2px;
}

.no-result {
  padding: 40px 20px;
  text-align: center;
  color: #909399;
  font-size: 14px;
}

.share-user-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  cursor: pointer;
  transition: all 0.2s;
  border-bottom: 1px solid #f5f5f5;
}

.share-user-item:last-child {
  border-bottom: none;
}

.share-user-item:hover {
  background: #fafafa;
}

.share-user-item.selected {
  background: #fff0f2;
}

.share-user-item .user-info {
  flex: 1;
  min-width: 0;
}

.share-user-item .user-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}

.share-user-item .user-bio {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.selected-mark {
  flex-shrink: 0;
}

.share-preview {
  margin-top: 12px;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
}

.share-preview .preview-label {
  color: #909399;
}

.share-preview .preview-name {
  color: #ff2442;
  font-weight: 600;
}

.share-tab-btn .btn-icon-wrapper {
  color: #9ca3af;
}

.share-tab-btn:hover .btn-icon-wrapper {
  color: #10b981;
}

.share-tab-btn:hover {
  background: linear-gradient(135deg, #ecfdf5 0%, #d1fae5 100%);
}
</style>

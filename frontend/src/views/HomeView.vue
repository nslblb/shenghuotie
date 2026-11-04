<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
defineOptions({ name: 'home' })
import { getPostList, getTopics } from '@/api/post'
import { getPublicConfigs, type PublicConfigs } from '@/api/user'
import request from '@/api/index'
import type { PostVO, PageData, Result, UserVO } from '@/types'
import PostCard from '@/components/PostCard.vue'
import { ElMessage } from 'element-plus'
import { Document, User, ArrowRight, ArrowDown, ArrowUp, TrendCharts, Clock, Message, Service } from '@element-plus/icons-vue'
import { usePostStore } from '@/stores/post'

const route = useRoute()
const router = useRouter()
const postStore = usePostStore()

const posts = ref<PostVO[]>([])
const users = ref<UserVO[]>([])
const loading = ref(false)
const isFirstLoad = ref(true)
const currentPage = ref(0)
const totalPages = ref(0)
const currentTopic = ref<string | undefined>()
const currentSort = ref<'latest' | 'hot'>('latest')
const searchKeyword = ref<string | undefined>()
const searchCategory = ref<'posts' | 'users'>('posts')
const useCache = ref(true)
const topicScrollRef = ref<HTMLElement | null>(null)
const announcement = ref('')
const siteDescription = ref('')
const contactEmail = ref('')
const contactWechat = ref('')
const showPopup = ref(false)

const topics = ref<string[]>(['全部'])
const isSearching = computed(() => !!searchKeyword.value)

async function loadTopics() {
  try {
    const res = await getTopics()
    topics.value = ['全部', ...res.data.data.filter(t => t.enabled).map(t => t.name)]
  } catch {
    topics.value = ['全部', '美食', '旅行', '穿搭', '健身', '学习', '生活']
  }
}

async function loadPosts(page = 0, forceRefresh = false) {
  loading.value = true
  try {
    let result
    if (searchKeyword.value) {
      const cacheKey = `search:${searchCategory.value}:${searchKeyword.value}:${currentTopic.value || 'all'}:${page}`
      const cached = postStore.cacheMap.get(cacheKey)
      if (cached && useCache.value && !forceRefresh) {
        if (page === 0) {
          if (searchCategory.value === 'users') {
            users.value = cached.users || []
          } else {
            posts.value = cached.posts
          }
        } else {
          if (searchCategory.value === 'users') {
            users.value.push(...(cached.users || []))
          } else {
            posts.value.push(...cached.posts)
          }
        }
        currentPage.value = cached.currentPage
        totalPages.value = cached.totalPages
        return
      }
      
      let apiUrl = '/posts/search'
      if (searchCategory.value === 'users') {
        apiUrl = '/users/search'
      }
      
      const res = await request.get<Result<PageData<PostVO | UserVO>>>(apiUrl, {
        params: { keyword: searchKeyword.value, page, size: 12, topic: currentTopic.value || undefined },
      })
      const data: PageData<PostVO | UserVO> = res.data.data
      
      if (searchCategory.value === 'users') {
        postStore.cacheMap.set(cacheKey, { users: data.content as UserVO[], currentPage: data.number, totalPages: data.totalPages })
        users.value = page === 0 ? data.content as UserVO[] : [...users.value, ...(data.content as UserVO[])]
      } else {
        postStore.cacheMap.set(cacheKey, { posts: data.content as PostVO[], currentPage: data.number, totalPages: data.totalPages })
        posts.value = page === 0 ? data.content as PostVO[] : [...posts.value, ...(data.content as PostVO[])]
      }
      
      result = data
    } else {
      const cacheKey = `topic:${currentTopic.value || 'all'}:${currentSort.value}:${page}`
      const cached = postStore.cacheMap.get(cacheKey)
      if (cached && useCache.value && !forceRefresh) {
        if (page === 0) {
          posts.value = cached.posts
        } else {
          posts.value.push(...cached.posts)
        }
        currentPage.value = cached.currentPage
        totalPages.value = cached.totalPages
        return
      }
      const res = await getPostList({
        page,
        size: 12,
        topic: currentTopic.value,
        sort: currentSort.value === 'hot' ? 'hot' : undefined,
      })
      const data: PageData<PostVO> = res.data.data
      postStore.cacheMap.set(cacheKey, { posts: data.content, currentPage: data.number, totalPages: data.totalPages })
      result = data
      posts.value = page === 0 ? data.content : [...posts.value, ...data.content]
    }
    currentPage.value = result.number
    totalPages.value = result.totalPages
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
    isFirstLoad.value = false
  }
}

function handleTopicChange(topic: string) {
  searchKeyword.value = undefined
  currentTopic.value = topic === '全部' ? undefined : topic
  loadPosts(0)
}

function handleSortChange(sort: 'latest' | 'hot') {
  currentSort.value = sort
  loadPosts(0)
}

function handleSearchCategoryChange(category: 'posts' | 'users') {
  searchCategory.value = category
  loadPosts(0, true)
}

function handleBackToHome() {
  searchKeyword.value = undefined
  searchCategory.value = 'all'
  router.push({ path: '/' })
}

function loadMore() {
  if (currentPage.value + 1 < totalPages.value) {
    loadPosts(currentPage.value + 1)
  }
}

watch(
  () => route.query.keyword,
  (newKeyword) => {
    if (newKeyword) {
      searchKeyword.value = newKeyword as string
      currentTopic.value = undefined
      
      if (route.query.category) {
        searchCategory.value = route.query.category as 'posts' | 'users' | 'all'
      }
      
      loadPosts(0)
    } else if (searchKeyword.value) {
      searchKeyword.value = undefined
      loadPosts(0)
    }
  },
  { immediate: true }
)

onMounted(() => {
  loadTopics()
  if (!route.query.keyword) {
    loadPosts()
  }
  getPublicConfigs().then(res => {
    const data = res.data.data
    announcement.value = data.announcement || ''
    siteDescription.value = data.siteDescription || ''
    contactEmail.value = data.contactEmail || ''
    contactWechat.value = data.contactWechat || ''
  }).catch(() => {})
})
</script>

<template>
  <div class="home">
    <div class="topic-bar">
      <template v-if="isSearching">
        <div class="category-tabs">
          <button
            :class="['category-tab', { active: searchCategory === 'posts' }]"
            @click="handleSearchCategoryChange('posts')"
          >
            <el-icon><Document /></el-icon>
            帖子
          </button>
          <button
            :class="['category-tab', { active: searchCategory === 'users' }]"
            @click="handleSearchCategoryChange('users')"
          >
            <el-icon><User /></el-icon>
            用户
          </button>
        </div>
      </template>

      <template v-else>
        <div class="topic-scroll" ref="topicScrollRef">
          <button
            v-for="topic in topics"
            :key="topic"
            :class="['topic-item', { active: currentTopic === (topic === '全部' ? undefined : topic) }]"
            @click="handleTopicChange(topic)"
          >
            {{ topic }}
          </button>
        </div>
        <div class="sort-tabs">
          <button
            :class="['sort-tab', { active: currentSort === 'latest' }]"
            @click="handleSortChange('latest')"
          >
            <el-icon><Clock /></el-icon>
            最新
          </button>
          <button
            :class="['sort-tab', { active: currentSort === 'hot' }]"
            @click="handleSortChange('hot')"
          >
            <el-icon><TrendCharts /></el-icon>
            热门
          </button>
        </div>
      </template>
    </div>

    <div class="post-grid">
      <template v-if="searchCategory === 'users'">
        <div v-if="isFirstLoad && users.length === 0" class="user-skeleton-list">
          <div v-for="i in 6" :key="i" class="skeleton-user-card">
            <div class="skeleton-avatar" />
            <div class="skeleton-user-info">
              <div class="skeleton-name" />
              <div class="skeleton-bio" />
            </div>
          </div>
        </div>
        <template v-else>
          <div v-for="user in users" :key="user.id" class="user-card" @click="router.push(`/profile/${user.id}`)">
            <el-avatar :size="56" :src="user.avatarUrl || undefined" class="user-avatar">
              {{ user.nickname?.charAt(0) || '?' }}
            </el-avatar>
            <div class="user-info">
              <h3 class="user-nickname">{{ user.nickname }}</h3>
              <p class="user-bio">{{ user.bio || '这个人很懒，什么都没写' }}</p>
            </div>
            <el-icon class="user-arrow"><ArrowRight /></el-icon>
          </div>
        </template>
      </template>
      <template v-else>
        <template v-if="isFirstLoad && posts.length === 0">
          <div v-for="i in 12" :key="i" class="skeleton-card">
            <div class="skeleton-image" />
            <div class="skeleton-body">
              <div class="skeleton-title" />
              <div class="skeleton-meta" />
              <div class="skeleton-author" />
            </div>
          </div>
        </template>
        <template v-else>
          <PostCard v-for="post in posts" :key="post.id" :post="post" :search-keyword="searchKeyword" />
        </template>
      </template>
    </div>

    <div v-if="loading && (posts.length > 0 || users.length > 0)" class="loading-indicator">
      <span class="loading-dots"><i></i><i></i><i></i></span>
      <span>加载中...</span>
    </div>

    <div v-if="currentPage + 1 < totalPages" class="load-more">
      <button class="load-more-btn" @click="loadMore" :disabled="loading">
        <span>加载更多</span>
        <el-icon><ArrowDown /></el-icon>
      </button>
    </div>

    <div v-if="!isFirstLoad && !loading && (searchCategory === 'users' ? users.length === 0 : posts.length === 0)" class="empty-state">
      <div class="empty-illustration">
        <svg viewBox="0 0 200 160" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="40" y="30" width="120" height="100" rx="12" fill="#f0f2f5" stroke="#e4e7ed" stroke-width="2"/>
          <rect x="55" y="48" width="90" height="8" rx="4" fill="#dcdfe6"/>
          <rect x="55" y="64" width="60" height="6" rx="3" fill="#e4e7ed"/>
          <circle cx="75" cy="95" r="14" fill="#e8eaef"/>
          <path d="M70 95l3 3 7-7" stroke="#b0b5c0" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <circle cx="130" cy="50" r="16" fill="#fff3f0" stroke="#ffccc7" stroke-width="1.5"/>
          <path d="M125 50l3 3 6-6" stroke="#ff7875" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
      <p class="empty-text">{{ searchKeyword ? (searchCategory === 'users' ? '未找到相关用户' : '未找到相关帖子，试试其他关键词') : '暂无帖子' }}</p>
    </div>

    <div class="floating-ball" @click="showPopup = true">
      <el-icon :size="18"><TrendCharts /></el-icon>
    </div>

    <Teleport to="body">
      <div class="popup-overlay" v-if="showPopup" @click.self="showPopup = false">
        <div class="popup-content">
          <div v-if="siteDescription" class="popup-section">
            <h4>站点描述</h4>
            <p>{{ siteDescription }}</p>
          </div>
          <div v-if="announcement" class="popup-section">
            <h4>公告</h4>
            <p>{{ announcement }}</p>
          </div>
          <div v-if="contactEmail || contactWechat" class="popup-section">
            <h4>联系方式</h4>
            <div class="contact-items">
              <span v-if="contactEmail"><el-icon><Message /></el-icon> {{ contactEmail }}</span>
              <span v-if="contactWechat"><el-icon><Service /></el-icon> {{ contactWechat }}</span>
            </div>
          </div>
          <div class="popup-close" @click="showPopup = false">
            <el-icon :size="20"><ArrowDown /></el-icon>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.home {
  padding: 0 16px;
  max-width: 100%;
  margin: 0 auto;
  animation: fadeInUp 0.4s ease;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.floating-ball {
  position: fixed;
  bottom: 30px;
  right: 30px;
  width: 35px;
  height: 35px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff2442, #ff5773);
  box-shadow: 0 4px 16px rgba(255, 36, 66, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  cursor: pointer;
  z-index: 1000;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.floating-ball:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 20px rgba(255, 36, 66, 0.45);
}

.popup-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1001;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.popup-content {
  background: #fff;
  border-radius: var(--radius-lg);
  padding: 24px;
  max-width: 500px;
  width: 90%;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  animation: slideUp 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.popup-section {
  margin-bottom: 20px;
}

.popup-section:last-child {
  margin-bottom: 0;
}

.popup-section h4 {
  margin: 0 0 10px 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.popup-section p {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
}

.popup-close {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.popup-close:hover {
  background: #e8e8e8;
  color: var(--primary);
}

.contact-items {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.contact-items span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--text-secondary);
}

.topic-bar {
  position: sticky;
  top: 80px;
  z-index: 90;
  margin-bottom: 24px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 8px 10px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  gap: 12px;
}

.sort-tabs {
  display: flex;
  gap: 4px;
  margin-left: auto;
  flex-shrink: 0;
  border-left: 1px solid rgba(0, 0, 0, 0.08);
  padding-left: 12px;
}

.sort-tab {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.sort-tab:hover {
  background: var(--bg-card-hover);
  color: var(--text-primary);
}

.sort-tab.active {
  background: var(--primary-bg);
  color: var(--primary);
  font-weight: 600;
}

.category-tabs {
  display: flex;
  gap: 4px;
}

.category-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 20px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.category-tab:hover {
  background: var(--bg-card-hover);
  color: var(--text-primary);
}

.category-tab.active {
  background: var(--primary-bg);
  color: var(--primary);
  font-weight: 600;
}

.topic-scroll {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 4px 2px;
  flex: 1;
  min-width: 0;
  scrollbar-width: thin;
  -ms-overflow-style: auto;
}

.topic-scroll::-webkit-scrollbar {
  height: 4px;
}

.topic-scroll::-webkit-scrollbar-thumb {
  background: var(--text-muted);
  border-radius: 2px;
}

.topic-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.topic-item {
  flex-shrink: 0;
  padding: 8px 20px;
  border: none;
  border-radius: var(--radius-full);
  background: var(--bg-page);
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  position: relative;
}

.topic-item:hover {
  background: var(--bg-card-hover);
  color: var(--text-primary);
}

.topic-item.active {
  background: var(--primary);
  color: #fff;
  font-weight: 600;
}

.post-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
  min-height: 200px;
}

.skeleton-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.skeleton-image {
  width: 100%;
  height: 200px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e8e8e8 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.skeleton-body {
  padding: 12px 14px 14px;
}

.skeleton-title {
  height: 18px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e8e8e8 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 4px;
  margin-bottom: 10px;
}

.skeleton-meta {
  height: 14px;
  width: 60%;
  background: linear-gradient(90deg, #f0f0f0 25%, #e8e8e8 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 4px;
  margin-bottom: 8px;
}

.skeleton-author {
  height: 24px;
  width: 30%;
  background: linear-gradient(90deg, #f0f0f0 25%, #e8e8e8 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 4px;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.loading-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 24px;
  color: var(--text-muted);
  font-size: 14px;
}

.loading-dots {
  display: inline-flex;
  gap: 5px;
  align-items: center;
}

.loading-dots i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--primary);
  animation: dotBounce 1.4s infinite ease-in-out both;
}

.loading-dots i:nth-child(1) { animation-delay: -0.32s; }
.loading-dots i:nth-child(2) { animation-delay: -0.16s; }

@keyframes dotBounce {
  0%, 80%, 100% { transform: scale(0.5); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

.load-more {
  text-align: center;
  margin-top: 32px;
  padding-bottom: 24px;
}

.load-more-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 12px 36px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-full);
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.load-more-btn:hover:not(:disabled) {
  border-color: var(--primary);
  color: var(--primary);
  background: var(--bg-card);
}

.load-more-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
}

.empty-illustration {
  width: 180px;
  height: 144px;
  margin-bottom: 20px;
  opacity: 0.85;
}

.empty-text {
  font-size: 15px;
  color: var(--text-muted);
  margin: 0;
  font-weight: 500;
}

.user-skeleton-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.skeleton-user-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.skeleton-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(90deg, #f0f0f0 25%, #e8e8e8 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  flex-shrink: 0;
}

.skeleton-user-info {
  flex: 1;
}

.skeleton-name {
  height: 18px;
  width: 40%;
  background: linear-gradient(90deg, #f0f0f0 25%, #e8e8e8 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 4px;
  margin-bottom: 10px;
}

.skeleton-bio {
  height: 14px;
  width: 70%;
  background: linear-gradient(90deg, #f0f0f0 25%, #e8e8e8 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 4px;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
}

.user-card:hover {
  box-shadow: var(--shadow-md);
}

.user-avatar {
  flex-shrink: 0;
  border: 2px solid #fef0f0;
}

.user-card:hover .user-avatar {
  border-color: #ffcdd2;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-nickname {
  margin: 0 0 4px 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-username {
  margin: 0 0 6px 0;
  font-size: 13px;
  color: var(--text-muted);
}

.user-bio {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-arrow {
  color: var(--text-muted);
  flex-shrink: 0;
  transition: all 0.2s ease;
}

.user-card:hover .user-arrow {
  color: var(--primary);
}
</style>

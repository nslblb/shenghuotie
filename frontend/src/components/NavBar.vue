<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getUnreadCount } from '@/api/notification'
import { getMessageUnreadCount } from '@/api/message'
import { getPublicConfigs } from '@/api/user'
import { Search, Promotion, EditPen, ArrowLeft, Close, Bell, ChatDotRound } from '@element-plus/icons-vue'
import { getSearchHistory, saveSearchHistory, clearSearchHistory, debounce, sanitizeSearchInput } from '@/utils/search'
import { ElMessage } from 'element-plus'
import type { PostVO, PageData, Result, UserVO } from '@/types'
import request from '@/api/index'
import { connectWebSocket, disconnectWebSocket, subscribe, unsubscribe, isConnected } from '@/utils/websocket'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const isLoggedIn = computed(() => userStore.isLoggedIn)
const unreadCount = ref(0)
const messageUnreadCount = ref(0)
const searchKeyword = ref('')
const showDropdown = ref(false)
const searchHistory = ref<string[]>([])
const searchSuggestions = ref<string[]>([])
const searchLoading = ref(false)
const searchInputRef = ref<any>(null)
const siteName = ref('生活贴')

const searchCategory = ref<'posts' | 'users' | 'all'>('all')
const isSearching = computed(() => !!route.query.keyword)

const popularSearches = ['美食', '旅行', '穿搭', '健身', '学习', '生活', '科技', '摄影']

const searchCache = new Map<string, string[]>()
let abortController: AbortController | null = null

async function loadUnreadCount() {
  if (!isLoggedIn.value) return
  try {
    const res = await getUnreadCount()
    unreadCount.value = res.data.data
  } catch {
    // ignore
  }
}

async function loadMessageUnreadCount() {
  if (!isLoggedIn.value) return
  try {
    const res = await getMessageUnreadCount()
    messageUnreadCount.value = res.data.data
  } catch {
    // ignore
  }
}

async function fetchSearchSuggestions(keyword: string) {
  const trimmedKeyword = keyword.trim()
  if (!trimmedKeyword || trimmedKeyword.length < 2) {
    searchSuggestions.value = []
    return
  }

  const cacheKey = `${trimmedKeyword.toLowerCase()}_${searchCategory.value}`
  if (searchCache.has(cacheKey)) {
    searchSuggestions.value = searchCache.get(cacheKey)!
    return
  }

  if (abortController) {
    abortController.abort()
  }

  abortController = new AbortController()

  searchLoading.value = true
  try {
    let apiUrl = '/posts/search'
    if (searchCategory.value === 'users') {
      apiUrl = '/users/search'
    }

    const res = await request.get<Result<PageData<PostVO | UserVO>>>(apiUrl, {
      params: { keyword: trimmedKeyword, page: 0, size: 5 },
      signal: abortController.signal
    })

    let suggestions: string[] = []
    if (searchCategory.value === 'users') {
      const users = res.data.data.content as UserVO[]
      suggestions = users.map(user => user.nickname).slice(0, 5)
    } else {
      const posts = res.data.data.content as PostVO[]
      suggestions = posts.map(post => post.title).slice(0, 5)
    }

    searchSuggestions.value = suggestions
    searchCache.set(cacheKey, suggestions)

    if (searchCache.size > 50) {
      const firstKey = searchCache.keys().next().value as string
      searchCache.delete(firstKey)
    }
  } catch (error: any) {
    if (error.name !== 'AbortError' && error.code !== 'ERR_CANCELED') {
      console.error('搜索建议获取失败:', error)
      searchSuggestions.value = []
    }
  } finally {
    searchLoading.value = false
    abortController = null
  }
}

const debouncedFetchSuggestions = debounce(fetchSearchSuggestions, 300)

function handleInputChange(value: string) {
  const sanitizedValue = sanitizeSearchInput(value)
  searchKeyword.value = sanitizedValue
  if (sanitizedValue.trim() && sanitizedValue.trim().length >= 2) {
    showDropdown.value = true
    if (!searchHistory.value || searchHistory.value.length === 0) {
      showSearchHistory()
    }
    debouncedFetchSuggestions(sanitizedValue)
  } else {
    showSearchHistory()
  }
}

function showSearchHistory() {
  searchHistory.value = getSearchHistory()
  searchSuggestions.value = []
  showDropdown.value = true
}

function handleSearchClick() {
  handleSearch()
}

function handleClear() {
  searchKeyword.value = ''
  showDropdown.value = false
  searchInputRef.value?.focus()
}

function handleSearch(keyword?: string | Event) {
  let kw: string

  if (typeof keyword === 'string') {
    kw = keyword.trim()
  } else if (keyword instanceof Event) {
    kw = String(searchKeyword.value || '').trim()
  } else {
    kw = String(keyword || searchKeyword.value || '').trim()
  }

  if (!kw) return

  try {
    saveSearchHistory(kw)
    router.push({
      path: '/',
      query: {
        keyword: kw,
        category: searchCategory.value !== 'all' ? searchCategory.value : undefined
      }
    })
    showDropdown.value = false
    searchKeyword.value = kw
  } catch (error) {
    console.error('搜索失败:', error)
    ElMessage.error('搜索失败，请重试')
  }
}

function handleBackToHome() {
  searchKeyword.value = ''
  searchCategory.value = 'all'
  router.back()
}

function handleChangeCategory(category: 'posts' | 'users' | 'all') {
  searchCategory.value = category
  if (searchKeyword.value.trim()) {
    fetchSearchSuggestions(searchKeyword.value)
  }
}

let hideTimer: number | null = null

function handleMouseEnter() {
  if (hideTimer) {
    clearTimeout(hideTimer)
    hideTimer = null
  }
  showSearchHistory()
}

function handleMouseLeave() {
  hideTimer = window.setTimeout(() => {
    showDropdown.value = false
    hideTimer = null
  }, 200)
}

function clearHistory() {
  clearSearchHistory()
  searchHistory.value = []
}

function removeHistoryItem(index: number) {
  const newHistory = searchHistory.value.filter((_, i) => i !== index)
  searchHistory.value = newHistory
  localStorage.setItem('shenghuotie_search_history', JSON.stringify(newHistory))
}

let notificationSubscription = false
let messageSubscription = false

function startWebSocket() {
  if (!isLoggedIn.value) return
  connectWebSocket()
  subscribe('navbar-notifications', '/user/topic/notifications', (message) => {
    try {
      const data = JSON.parse(message.body)
      if (typeof data.unreadCount === 'number') {
        unreadCount.value = data.unreadCount
      }
    } catch {
      loadUnreadCount()
    }
  })
  notificationSubscription = true
  subscribe('navbar-messages', '/user/topic/messages', () => {
    loadMessageUnreadCount()
  })
  messageSubscription = true
}

function stopWebSocket() {
  if (notificationSubscription) {
    unsubscribe('navbar-notifications')
    notificationSubscription = false
  }
  if (messageSubscription) {
    unsubscribe('navbar-messages')
    messageSubscription = false
  }
  disconnectWebSocket()
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') {
    // 切回来时刷新未读数，并确保 WebSocket 连接正常
    loadUnreadCount()
    loadMessageUnreadCount()
    if (!isConnected()) {
      startWebSocket()
    }
  }
  // 切后台时不断开连接，保持在线状态和实时消息接收
}

watch(() => route.query.keyword, (newKeyword) => {
  if (newKeyword) {
    searchKeyword.value = newKeyword as string
  }
}, { immediate: true })

watch(() => route.query.category, (newCategory) => {
  if (newCategory) {
    searchCategory.value = newCategory as 'posts' | 'users' | 'all'
  }
}, { immediate: true })

onMounted(() => {
  loadUnreadCount()
  loadMessageUnreadCount()
  startWebSocket()
  document.addEventListener('visibilitychange', handleVisibilityChange)
  window.addEventListener('notifications-refreshed', loadUnreadCount)
  window.addEventListener('messages-refreshed', loadMessageUnreadCount)
  
  getPublicConfigs().then(res => {
    const data = res.data.data
    if (data.siteName) {
      siteName.value = data.siteName
    }
  }).catch(() => {})
})

onBeforeUnmount(() => {
  stopWebSocket()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  window.removeEventListener('notifications-refreshed', loadUnreadCount)
  window.removeEventListener('messages-refreshed', loadMessageUnreadCount)
})
</script>

<template>
  <header class="navbar">
    <div class="navbar-inner">
      <div class="logo-section">
        <template v-if="isSearching">
          <el-button circle class="back-button" @click="handleBackToHome">
            <el-icon><ArrowLeft /></el-icon>
          </el-button>
          <span class="search-mode-label">搜索结果</span>
        </template>
        <router-link v-else to="/" class="logo">
          <span class="logo-badge">
            <el-icon :size="18"><Promotion /></el-icon>
          </span>
          <span class="logo-name">{{ siteName }}</span>
        </router-link>
      </div>

      <div class="search-bar">
        <div class="search-wrapper" @mouseenter="handleMouseEnter" @mouseleave="handleMouseLeave">
          <el-input
            ref="searchInputRef"
            v-model="searchKeyword"
            placeholder="搜索帖子、用户..."
            @keyup.enter="handleSearch"
            @input="handleInputChange"
          >
            <template #suffix>
              <button v-if="searchKeyword.trim()" class="clear-button-inside" @click="handleClear" title="清除">
                <el-icon><Close /></el-icon>
              </button>
              <button v-if="searchKeyword.trim()" class="search-button-inside" @click="handleSearchClick" title="搜索">
                <el-icon><Search /></el-icon>
              </button>
            </template>
          </el-input>

          <div v-if="showDropdown" class="search-dropdown" @mouseenter="handleMouseEnter" @mouseleave="handleMouseLeave">
            <div class="dropdown-section">
              <div class="section-header">
                <span class="section-title">搜索历史</span>
                <el-button text size="small" @click.stop="clearHistory">清空</el-button>
              </div>
              <div class="history-tags">
                <div
                  v-for="(history, index) in searchHistory"
                  :key="index"
                  class="history-tag-wrapper"
                >
                  <el-tag
                    size="default"
                    class="history-tag"
                    @click="handleSearch(history)"
                  >
                    {{ history }}
                  </el-tag>
                  <button class="history-delete-btn" @click.stop="removeHistoryItem(index)" title="删除此记录">
                    <el-icon><Close /></el-icon>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="navbar-actions">
        <template v-if="isLoggedIn">
          <div class="nav-icon-btn" @click="router.push('/notifications')" title="通知">
            <el-icon :size="20"><Bell /></el-icon>
            <span v-if="unreadCount > 0" class="nav-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
          </div>
          <div class="nav-icon-btn" @click="router.push('/messages')" title="私信">
            <el-icon :size="20"><ChatDotRound /></el-icon>
            <span v-if="messageUnreadCount > 0" class="nav-badge">{{ messageUnreadCount > 99 ? '99+' : messageUnreadCount }}</span>
          </div>
          <el-button type="primary" @click="router.push('/create')">
            <el-icon><EditPen /></el-icon>
            发布帖子
          </el-button>

          <el-avatar
            :size="34"
            :src="userStore.userInfo?.avatarUrl || undefined"
            class="nav-avatar"
            @click="router.push(`/profile/${userStore.userInfo?.id}`)"
          >
            {{ userStore.userInfo?.nickname?.charAt(0) || 'U' }}
          </el-avatar>
        </template>
        <template v-else>
          <el-button @click="router.push('/login')">登录</el-button>
          <el-button type="primary" @click="router.push('/register')">注册</el-button>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped>
.navbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 64px;
  background: var(--navbar-bg);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  box-shadow: var(--navbar-shadow);
  border-bottom: 1px solid var(--border-color);
  z-index: 100;
  transition: box-shadow 0.3s ease;
}

.navbar-inner {
  max-width: 1200px;
  margin: 0 auto;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 120px;
}

.back-button {
  border: none;
  background: var(--bg-page);
}

.back-button:hover {
  background: #eaecf0;
}

.search-mode-label {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
}

.logo-badge {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: 0 3px 8px rgba(255, 36, 66, 0.3);
}

.logo-name {
  font-size: 19px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: 0.5px;
}

.search-bar {
  flex: 1;
  display: flex;
  justify-content: center;
  max-width: 600px;
}

.search-bar :deep(.el-input__wrapper) {
  height: 40px;
  border-radius: var(--radius-full);
  background: var(--input-fill);
  box-shadow: none;
  padding: 0 16px;
  transition: background 0.2s ease, box-shadow 0.2s ease;
}

.search-bar :deep(.el-input__wrapper):hover {
  background: var(--input-fill-hover);
}

.search-bar :deep(.el-input__wrapper.is-focus) {
  background: #fff;
  box-shadow: 0 0 0 1.5px var(--primary) inset;
}

.search-wrapper {
  position: relative;
  width: 100%;
  max-width: 420px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.clear-button-inside {
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  margin-right: 2px;
  transition: all 0.2s;
}

.clear-button-inside:hover {
  color: var(--text-secondary);
  transform: scale(1.1);
}

.clear-button-inside:active {
  transform: scale(0.95);
}

.search-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  right: 0;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  padding: 12px;
  z-index: 998;
}

.dropdown-section {
  margin-bottom: 0;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.section-title {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.history-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.history-tag {
  cursor: pointer;
  transition: all 0.2s;
}

.history-tag:hover {
  color: var(--primary);
  border-color: var(--primary);
}

.history-tag-wrapper {
  display: flex;
  align-items: center;
  gap: 4px;
}

.history-delete-btn {
  border: none;
  background: transparent;
  color: #c0c4cc;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2px;
  font-size: 12px;
  transition: all 0.2s;
  border-radius: 50%;
}

.history-delete-btn:hover {
  color: #f56c6c;
  background: #fef0f0;
}

.search-button-inside {
  border: none;
  background: transparent;
  color: var(--primary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  transition: all 0.2s;
}

.search-button-inside:hover {
  color: #e61e3c;
  transform: scale(1.1);
}

.search-button-inside:active {
  transform: scale(0.95);
}

.navbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: auto;
  justify-content: flex-end;
}

.navbar-actions :deep(.el-button) {
  border-radius: var(--radius-full);
  font-weight: 600;
}

.nav-avatar {
  cursor: pointer;
  transition: transform 0.2s ease;
}

.nav-avatar:hover {
  transform: scale(1.08);
}

.nav-icon-btn {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  cursor: pointer;
  color: var(--text-secondary);
  transition: all 0.2s ease;
}

.nav-icon-btn:hover {
  background: var(--bg-card-hover);
  color: var(--primary);
}

.nav-badge {
  position: absolute;
  top: -2px;
  right: -2px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: #f56c6c;
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
  text-align: center;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .search-wrapper {
    max-width: none;
    width: calc(100% - 80px);
  }

  .navbar-actions {
    gap: 8px;
  }

  .logo-section {
    min-width: auto;
  }
}
</style>

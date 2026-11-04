<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { likePost, unlikePost } from '@/api/like'
import { useUserStore } from '@/stores/user'
import { highlightKeyword } from '@/utils/search'
import type { PostVO } from '@/types'
import { formatRelativeTime } from '@/utils/date'

const props = defineProps<{
  post: PostVO
  searchKeyword?: string
}>()

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const showAnimation = ref(false)
const imageLoaded = ref(false)
const imageError = ref(false)

const imageHeight = computed(() => {
  if (!props.post.coverImage) return 0
  const hash = Math.abs(props.post.id * 2654435761 % 256)
  if (hash < 60) return 280
  if (hash < 130) return 200
  if (hash < 200) return 240
  return 220
})

const highlightedTitle = computed(() => {
  return highlightKeyword(props.post.title, props.searchKeyword || '')
})

function goDetail(id: number) {
  router.push(`/post/${id}`)
}

async function handleLike(e: Event) {
  e.stopPropagation()

  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }

  if (loading.value) return

  const previousLiked = props.post.liked
  const previousLikeCount = props.post.likeCount

  props.post.liked = !props.post.liked
  props.post.likeCount += props.post.liked ? 1 : -1

  loading.value = true

  if (props.post.liked) {
    showAnimation.value = true
    setTimeout(() => {
      showAnimation.value = false
    }, 600)
  }

  try {
    if (props.post.liked) {
      await likePost(props.post.id)
    } else {
      await unlikePost(props.post.id)
    }
  } catch {
    props.post.liked = previousLiked
    props.post.likeCount = previousLikeCount
  } finally {
    loading.value = false
  }
}

function onImageLoad() {
  imageLoaded.value = true
  imageError.value = false
}

function onImageError() {
  imageLoaded.value = true
  imageError.value = true
}
</script>

<template>
  <div class="post-card" :class="{ 'no-image': !post.coverImage }" @click="goDetail(post.id)">
    <!-- 有图片：显示封面 -->
    <div v-if="post.coverImage" class="card-cover" :style="{ height: imageHeight + 'px' }">
      <div v-if="!imageLoaded" class="img-skeleton" />
      <img
        v-if="!imageError"
        :src="post.coverImage"
        :alt="post.title"
        class="cover-img"
        :class="{ 'img-visible': imageLoaded }"
        loading="lazy"
        @load="onImageLoad"
        @error="onImageError"
      />
      <div v-if="imageError" class="cover-fallback">
        <svg viewBox="0 0 48 48" width="48" height="48" fill="none">
          <rect width="48" height="48" rx="8" fill="#f0f2f5"/>
          <path d="M18 28l6-6 4 4 6-6 4 4v6a2 2 0 01-2 2H20a2 2 0 01-2-2v-2z" fill="#dcdfe6"/>
          <circle cx="17" cy="17" r="3" fill="#dcdfe6"/>
        </svg>
      </div>

      <div class="img-gradient" />

      <button
        class="like-overlay"
        :class="{ liked: post.liked, animating: showAnimation }"
        @click.stop="handleLike"
      >
        <svg viewBox="0 0 24 24" class="heart-icon" :class="{ filled: post.liked }">
          <path
            d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5
               2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09
               C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5
               c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"
          />
        </svg>
        <span class="like-count">{{ post.likeCount || '' }}</span>
      </button>
    </div>

    <!-- 无图片：内容区域占封面位置 -->
    <div v-else class="card-content-area">
      <p class="content-text">{{ post.content?.slice(0, 120) }}</p>
      <button
        class="like-overlay"
        :class="{ liked: post.liked, animating: showAnimation }"
        @click.stop="handleLike"
      >
        <svg viewBox="0 0 24 24" class="heart-icon" :class="{ filled: post.liked }">
          <path
            d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5
               2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09
               C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5
               c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"
          />
        </svg>
        <span class="like-count">{{ post.likeCount || '' }}</span>
      </button>
    </div>

    <div class="card-body">
      <h3 class="title" v-html="highlightedTitle" />
      <div class="card-footer">
        <div class="author">
          <el-avatar :size="20" :src="post.authorAvatar || undefined">
            {{ post.authorName?.charAt(0) || '?' }}
          </el-avatar>
          <span class="author-name">{{ post.authorName }}</span>
          <el-tag v-if="post.topic" size="small" class="topic-tag">{{ post.topic }}</el-tag>
        </div>
        <span class="comment-count">
          <svg viewBox="0 0 16 16" width="14" height="14" fill="none">
            <path d="M2 3h12v7H5l-3 3V4a1 1 0 011-1z" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/>
          </svg>
          {{ post.commentCount || 0 }}
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.post-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s ease,
              box-shadow 0.2s ease;
  position: relative;
  break-inside: avoid;
  margin-bottom: 16px;
  box-shadow: var(--shadow-sm);
  height: 320px;
  display: flex;
  flex-direction: column;
}

.post-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.post-card:active {
  transform: scale(0.99) translateY(-1px);
}

.post-card.no-image {
  background: var(--bg-card);
}

.card-content-area {
  position: relative;
  flex: 1;
  min-height: 0;
  padding: 16px 16px 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--primary-bg);
}

.content-text {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 15px;
  line-height: 1.9;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 5;
  -webkit-box-orient: vertical;
  text-align: center;
}

.card-cover {
  position: relative;
  width: 100%;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #f0f1f5;
}

.img-skeleton {
  position: absolute;
  inset: 0;
  background: linear-gradient(110deg, #f0f0f0 25%, #e8e8e8 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0;
  transition: opacity 0.4s ease, transform 0.2s ease;
}

.post-card:hover .cover-img {
  transform: scale(1.03);
}

.cover-img.img-visible {
  opacity: 1;
}

.cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f6f8;
}

.img-gradient {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 40%;
  background: linear-gradient(to top, rgba(0,0,0,0.2) 0%, transparent 100%);
  pointer-events: none;
}

.like-overlay {
  position: absolute;
  right: 8px;
  bottom: 8px;
  display: flex;
  align-items: center;
  gap: 3px;
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(4px);
  border: none;
  border-radius: 16px;
  padding: 4px 10px;
  cursor: pointer;
  transition: all 0.2s ease;
  z-index: 2;
}

.like-overlay:hover {
  background: rgba(0, 0, 0, 0.5);
  transform: scale(1.05);
}

.like-overlay.liked {
  background: rgba(255, 36, 66, 0.85);
}

.heart-icon {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: #fff;
  stroke-width: 1.8;
  transition: all 0.3s ease;
  flex-shrink: 0;
}

.heart-icon.filled {
  fill: #fff;
  stroke: #fff;
}

.like-overlay.animating .heart-icon {
  animation: heartBeat 0.6s ease;
}

@keyframes heartBeat {
  0% { transform: scale(1); }
  25% { transform: scale(1.3); }
  50% { transform: scale(0.95); }
  100% { transform: scale(1); }
}

.like-count {
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}

.card-body {
  padding: 10px 12px 14px;
  flex-shrink: 0;
}

.title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  word-break: break-all;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.author {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex: 1;
}

.author-name {
  font-size: 12px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topic-tag {
  flex-shrink: 0;
  font-size: 10px !important;
  padding: 0 6px !important;
  height: 18px !important;
  line-height: 18px !important;
  border-radius: 8px !important;
}

.comment-count {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  color: var(--text-muted);
  flex-shrink: 0;
  margin-left: 8px;
  transition: color 0.2s ease;
}

.comment-count:hover {
  color: var(--primary);
}

:deep(.keyword-highlight) {
  background: #ffe58f;
  padding: 0 2px;
  border-radius: 2px;
  color: inherit;
}
</style>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'

const props = defineProps<{
  liked: boolean
  likeCount: number
  commentCount: number
}>()

const emit = defineEmits<{ like: [] }>()

const loading = ref(false)
const showAnimation = ref(false)
const isLiked = computed(() => props.liked)

watch(() => props.liked, (newVal) => {
  if (newVal) {
    showAnimation.value = true
    setTimeout(() => {
      showAnimation.value = false
    }, 600)
  }
})

async function handleLike() {
  if (loading.value) return
  loading.value = true
  emit('like')

  setTimeout(() => {
    loading.value = false
  }, 300)
}
</script>

<template>
  <div class="interaction-actions">
    <button
      class="action-btn like-action"
      :class="{ liked }"
      :disabled="loading"
      @click="handleLike"
    >
      <div class="icon-wrapper">
        <svg viewBox="0 0 24 24" class="heart-icon" :class="{ animating: showAnimation }">
          <path
            d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5
               2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09
               C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5
               c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"
          />
        </svg>
      </div>
      <span class="action-label">{{ likeCount || '点赞' }}</span>
    </button>

    <div class="action-btn comment-action">
      <div class="icon-wrapper">
        <svg viewBox="0 0 24 24" class="comment-icon">
          <path d="M20 2H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h14l4 4V4c0-1.1-.9-2-2-2zm-2 12H6v-2h12v2zm0-3H6V9h12v2zm0-3H6V6h12v2z"/>
        </svg>
      </div>
      <span class="action-label">{{ commentCount || 0 }} 评论</span>
    </div>
  </div>
</template>

<style scoped>
.interaction-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 28px;
  border: none;
  border-radius: 16px;
  background: linear-gradient(135deg, #f8f9fa 0%, #f1f3f5 100%);
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
  position: relative;
  overflow: hidden;
}

.action-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, rgba(255, 36, 66, 0.08) 0%, rgba(255, 36, 66, 0.03) 100%);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.action-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.1);
}

.action-btn:hover::before {
  opacity: 1;
}

.like-action.liked {
  background: linear-gradient(135deg, #fff0f2 0%, #ffe4e8 100%);
  box-shadow: 0 4px 16px rgba(255, 36, 66, 0.15);
}

.like-action.liked .icon-wrapper {
  color: #ff2442;
}

.like-action.liked .heart-icon {
  fill: #ff2442;
  stroke: #ff2442;
}

.comment-action {
  cursor: default;
}

.icon-wrapper {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: white;
  transition: all 0.3s ease;
  color: #6b7280;
}

.action-btn:hover .icon-wrapper {
  transform: scale(1.08);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.heart-icon,
.comment-icon {
  width: 22px;
  height: 22px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  transition: all 0.3s ease;
}

.heart-icon.animating {
  animation: heartBeat 0.6s cubic-bezier(0.17, 0.89, 0.32, 1.49);
}

@keyframes heartBeat {
  0% { 
    transform: scale(1); 
  }
  25% { 
    transform: scale(1.4); 
  }
  40% { 
    transform: scale(0.95); 
  }
  60% { 
    transform: scale(1.15); 
  }
  100% { 
    transform: scale(1); 
  }
}

.action-label {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  letter-spacing: 0.2px;
}

.like-action.liked .action-label {
  color: #ff2442;
}
</style>

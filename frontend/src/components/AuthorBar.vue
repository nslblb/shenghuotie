<script setup lang="ts">
import { formatRelativeTime } from '@/utils/date'

defineProps<{
  authorName: string
  authorAvatar?: string
  userId: number
  isAuthor: boolean
  createdAt: string
}>()

const emit = defineEmits<{ delete: []; edit: [] }>()
</script>

<template>
  <div class="author-bar">
    <div class="author-profile" @click="$router.push(`/profile/${userId}`)">
      <el-avatar
        :size="48"
        :src="authorAvatar || undefined"
        class="author-avatar"
      >
        {{ authorName?.charAt(0) }}
      </el-avatar>
    </div>
    
    <div class="author-details">
      <h3 class="author-name" @click="$router.push(`/profile/${userId}`)">
        {{ authorName }}
      </h3>
      <time class="post-time">{{ formatRelativeTime(createdAt) }}</time>
    </div>

    <div v-if="isAuthor" class="author-actions">
      <button class="edit-action" @click="emit('edit')">
        <svg viewBox="0 0 24 24" class="action-icon">
          <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>
        </svg>
        <span>编辑</span>
      </button>
      <button class="delete-action" @click="emit('delete')">
        <svg viewBox="0 0 24 24" class="action-icon">
          <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
        </svg>
        <span>删除</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.author-bar {
  display: flex;
  align-items: center;
  gap: 14px;
}

.author-profile {
  cursor: pointer;
  position: relative;
}

.author-avatar {
  border: 3px solid #fff0f2;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 8px rgba(255, 36, 66, 0.12);
}

.author-profile:hover .author-avatar {
  transform: scale(1.08);
  border-color: #ff2442;
  box-shadow: 0 4px 16px rgba(255, 36, 66, 0.25);
}

.author-details {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.author-name {
  font-size: 17px;
  font-weight: 700;
  color: #1a1a1a;
  cursor: pointer;
  margin: 0;
  letter-spacing: -0.2px;
  transition: color 0.25s ease;
}

.author-name:hover {
  color: #ff2442;
}

.post-time {
  font-size: 13px;
  color: #9ca3af;
  font-weight: 500;
}

.author-actions {
  display: flex;
  gap: 8px;
}

.edit-action,
.delete-action {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 9px 18px;
  border: none;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s ease;
}

.edit-action {
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  color: #2563eb;
}

.edit-action:hover {
  background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.15);
  transform: translateY(-1px);
}

.delete-action {
  background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%);
  color: #dc2626;
}

.delete-action:hover {
  background: linear-gradient(135deg, #fee2e2 0%, #fecaca 100%);
  box-shadow: 0 4px 12px rgba(220, 38, 38, 0.15);
  transform: translateY(-1px);
}

.action-icon {
  width: 16px;
  height: 16px;
  fill: currentColor;
}
</style>

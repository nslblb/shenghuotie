<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAiStore } from '@/stores/ai'
import { ElMessageBox } from 'element-plus'
import { ChatDotRound } from '@element-plus/icons-vue'
import AiChatPanel from './AiChatPanel.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const aiStore = useAiStore()

// ==================== 拖拽 ====================
const ballRef = ref<HTMLElement | null>(null)
const isDragging = ref(false)
const pos = ref({ x: 0, y: 0 })
const DRAG_THRESHOLD = 5
let mouseDownInfo: { x: number; y: number; posX: number; posY: number } | null = null

onMounted(() => {
  // 默认右上角：right: 24px → x = window.innerWidth - 24 - 56
  pos.value = { x: window.innerWidth - 80, y: 80 }
  window.addEventListener('resize', clampPosition)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', clampPosition)
})

function clampPosition() {
  const w = window.innerWidth
  const h = window.innerHeight
  pos.value.x = Math.max(0, Math.min(pos.value.x, w - 56))
  pos.value.y = Math.max(0, Math.min(pos.value.y, h - 56))
}

function onMouseDown(e: MouseEvent) {
  if (e.button !== 0) return
  mouseDownInfo = {
    x: e.clientX,
    y: e.clientY,
    posX: pos.value.x,
    posY: pos.value.y,
  }
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
}

function onMouseMove(e: MouseEvent) {
  if (!mouseDownInfo) return
  const dx = e.clientX - mouseDownInfo.x
  const dy = e.clientY - mouseDownInfo.y
  if (!isDragging.value && (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD)) {
    isDragging.value = true
  }
  if (isDragging.value) {
    const w = window.innerWidth
    const h = window.innerHeight
    pos.value.x = Math.max(0, Math.min(mouseDownInfo.posX + dx, w - 56))
    pos.value.y = Math.max(0, Math.min(mouseDownInfo.posY + dy, h - 56))
  }
}

function onMouseUp() {
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
  const wasDragging = isDragging.value
  mouseDownInfo = null
  // 延迟一帧再重置 isDragging，避免触发 click
  if (wasDragging) {
    setTimeout(() => { isDragging.value = false }, 0)
  }
}

// ==================== 点击行为 ====================

async function handleBallClick() {
  if (isDragging.value) return
  if (!userStore.isLoggedIn) {
    try {
      await ElMessageBox.confirm(
        'AI 助手需要登录后使用，是否现在去登录？',
        '需要登录',
        { confirmButtonText: '去登录', cancelButtonText: '取消', type: 'info' },
      )
      router.push({ name: 'login', query: { redirect: route.fullPath } })
    } catch {
      // 用户取消
    }
    return
  }
  // 面板打开时，球的初始位置基于球当前位置
  aiStore.openPanel(pos.value.x, pos.value.y)
}
</script>

<template>
  <!-- 悬浮球 -->
  <Transition name="ball-fade">
    <div
      v-show="!aiStore.panelVisible"
      ref="ballRef"
      class="ai-floating-ball"
      :style="{ left: pos.x + 'px', top: pos.y + 'px' }"
      @mousedown.left="onMouseDown"
      @click="handleBallClick"
    >
      <el-icon :size="24" color="#fff"><ChatDotRound /></el-icon>
    </div>
  </Transition>

  <!-- 聊天面板 -->
  <AiChatPanel v-if="aiStore.panelVisible" />
</template>

<style scoped>
.ai-floating-ball {
  position: fixed;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  z-index: 10000;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.4);
  transition: box-shadow 0.3s, transform 0.3s;
  animation: pulse 2s ease-in-out infinite;
  user-select: none;
}

.ai-floating-ball:hover {
  transform: scale(1.08);
  box-shadow: 0 6px 24px rgba(64, 158, 255, 0.5);
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.85; }
}

/* 悬浮球过渡 */
.ball-fade-enter-active,
.ball-fade-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.ball-fade-enter-from,
.ball-fade-leave-to {
  opacity: 0;
  transform: scale(0.6);
}
</style>

<script setup lang="ts">
import { ref, onMounted, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  visible: boolean
  inline?: boolean
}>()

const emit = defineEmits<{
  (e: 'success'): void
  (e: 'fail'): void
  (e: 'close'): void
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)

const bgWidth = 310
const bgHeight = 155
const pieceSize = 42

const sliderX = ref(0)
const targetY = ref(0)
const dragging = ref(false)
const startTime = ref(0)
const moveTrack = ref<{ x: number; y: number; t: number }[]>([])

let targetX = 0

// 随机配色方案：[主渐变色1, 主渐变色2, 主渐变色3, 拼图块边框高亮色]
const palettes = [
  ['#4facfe', '#00f2fe', '#43e97b', '#1a73e8'],
  ['#f093fb', '#f5576c', '#fda085', '#e91e63'],
  ['#43e97b', '#38f9d7', '#fa709a', '#2e7d32'],
  ['#667eea', '#764ba2', '#f77062', '#5c6bc0'],
  ['#ff9a9e', '#fecfef', '#fdfcfb', '#d32f2f'],
  ['#a18cd1', '#fbc2eb', '#f6d365', '#7b1fa2'],
  ['#89f7fe', '#66a6ff', '#c471f5', '#1565c0'],
  ['#fddb92', '#d1fdff', '#a1c4fd', '#ef6c00'],
]

let currentPalette: string[] = []

function pickPalette(): string[] {
  return palettes[Math.floor(Math.random() * palettes.length)]
}

function drawCaptcha() {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  targetX = pieceSize + 10 + Math.floor(Math.random() * (bgWidth - 2 * pieceSize - 20))
  targetY.value = 10 + Math.floor(Math.random() * (bgHeight - pieceSize - 20))
  currentPalette = pickPalette()

  ctx.clearRect(0, 0, bgWidth, bgHeight)

  // 背景渐变（随机配色）
  const gradient = ctx.createLinearGradient(0, 0, bgWidth, bgHeight)
  gradient.addColorStop(0, currentPalette[0])
  gradient.addColorStop(0.5, currentPalette[1])
  gradient.addColorStop(1, currentPalette[2])
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, bgWidth, bgHeight)

  // 装饰：随机大小的半透明圆形
  for (let i = 0; i < 20; i++) {
    ctx.fillStyle = `rgba(255,255,255,${(Math.random() * 0.25 + 0.05).toFixed(2)})`
    ctx.beginPath()
    ctx.arc(Math.random() * bgWidth, Math.random() * bgHeight, Math.random() * 12 + 3, 0, Math.PI * 2)
    ctx.fill()
  }

  // 装饰：随机矩形色块
  for (let i = 0; i < 6; i++) {
    const rw = Math.random() * 40 + 15
    const rh = Math.random() * 20 + 10
    ctx.fillStyle = `rgba(255,255,255,${(Math.random() * 0.15 + 0.03).toFixed(2)})`
    ctx.fillRect(Math.random() * bgWidth, Math.random() * bgHeight, rw, rh)
  }

  // 装饰：斜线纹理
  ctx.strokeStyle = `rgba(255,255,255,0.08)`
  ctx.lineWidth = 1
  for (let i = -bgHeight; i < bgWidth + bgHeight; i += 18) {
    ctx.beginPath()
    ctx.moveTo(i, 0)
    ctx.lineTo(i + bgHeight, bgHeight)
    ctx.stroke()
  }

  // 缺口阴影（深色凹陷）
  ctx.save()
  ctx.shadowColor = 'rgba(0,0,0,0.5)'
  ctx.shadowBlur = 8
  ctx.shadowOffsetX = 2
  ctx.shadowOffsetY = 2
  drawPieceShape(ctx, targetX, targetY.value)
  ctx.fillStyle = 'rgba(0,0,0,0.6)'
  ctx.fill()
  ctx.restore()

  // 缺口内部加一层深色边缘，让缺口更明显
  drawPieceShape(ctx, targetX, targetY.value)
  ctx.strokeStyle = 'rgba(0,0,0,0.3)'
  ctx.lineWidth = 1
  ctx.stroke()

  // 左侧起始位置的拼图块（视觉高亮，让用户一眼看到）
  drawPiece(ctx, pieceSize, targetY.value)
}

function drawPieceShape(ctx: CanvasRenderingContext2D, x: number, y: number) {
  const r = pieceSize / 4
  ctx.beginPath()
  ctx.moveTo(x, y)
  ctx.lineTo(x + pieceSize, y)
  ctx.arc(x + pieceSize, y + pieceSize / 2, r, -Math.PI / 2, Math.PI / 2, false)
  ctx.lineTo(x, y + pieceSize)
  ctx.arc(x, y + pieceSize / 2, r, Math.PI / 2, -Math.PI / 2, false)
  ctx.closePath()
}

function drawPiece(ctx: CanvasRenderingContext2D, x: number, y: number) {
  ctx.save()
  drawPieceShape(ctx, x, y)
  ctx.clip()

  // 拼图块使用当前配色方案（与背景一致，模拟"截图"效果）
  const gradient = ctx.createLinearGradient(x, y, x + pieceSize, y + pieceSize)
  gradient.addColorStop(0, currentPalette[0])
  gradient.addColorStop(0.5, currentPalette[1])
  gradient.addColorStop(1, currentPalette[2])
  ctx.fillStyle = gradient
  ctx.fillRect(x, y, pieceSize, pieceSize)

  // 复制背景上对应的装饰纹理（小圆点）
  for (let i = 0; i < 8; i++) {
    ctx.fillStyle = `rgba(255,255,255,${(Math.random() * 0.25 + 0.05).toFixed(2)})`
    ctx.beginPath()
    ctx.arc(x + Math.random() * pieceSize, y + Math.random() * pieceSize, Math.random() * 5 + 1, 0, Math.PI * 2)
    ctx.fill()
  }

  ctx.restore()

  // 明显的白色边框 + 发光阴影，让拼图块从背景中凸显出来
  ctx.save()
  ctx.shadowColor = 'rgba(255,255,255,0.9)'
  ctx.shadowBlur = 10
  ctx.strokeStyle = 'rgba(255,255,255,0.9)'
  ctx.lineWidth = 2
  drawPieceShape(ctx, x, y)
  ctx.stroke()
  ctx.restore()

  // 外层深色描边，双层边框更醒目
  ctx.strokeStyle = currentPalette[3]
  ctx.lineWidth = 1.5
  drawPieceShape(ctx, x, y)
  ctx.stroke()
}

function refreshCaptcha() {
  sliderX.value = 0
  startTime.value = 0
  moveTrack.value = []
  nextTick(() => drawCaptcha())
}

function getMaxX() {
  return bgWidth - pieceSize - 10
}

function onMouseDown(e: MouseEvent) {
  e.preventDefault()
  dragging.value = true
  startTime.value = Date.now()
  moveTrack.value = [{ x: 0, y: 0, t: 0 }]
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}

function onMouseMove(e: MouseEvent) {
  if (!dragging.value) return
  const rect = (e.target as HTMLElement)?.closest('.canvas-wrapper')?.getBoundingClientRect()
    || canvasRef.value?.getBoundingClientRect()
  if (!rect) return
  let x = e.clientX - rect.left
  x = Math.max(0, Math.min(x, getMaxX()))
  sliderX.value = x
  moveTrack.value.push({ x: Math.round(x), y: 0, t: Date.now() - startTime.value })
}

function onMouseUp() {
  if (!dragging.value) return
  dragging.value = false
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', onMouseUp)
  doVerify()
}

function onTouchStart(e: TouchEvent) {
  e.preventDefault()
  dragging.value = true
  startTime.value = Date.now()
  moveTrack.value = [{ x: 0, y: 0, t: 0 }]
  document.addEventListener('touchmove', onTouchMove, { passive: false })
  document.addEventListener('touchend', onTouchEnd)
}

function onTouchMove(e: TouchEvent) {
  if (!dragging.value) return
  e.preventDefault()
  const rect = canvasRef.value?.getBoundingClientRect()
  if (!rect) return
  const touch = e.touches[0]
  let x = touch.clientX - rect.left
  x = Math.max(0, Math.min(x, getMaxX()))
  sliderX.value = x
  moveTrack.value.push({ x: Math.round(x), y: 0, t: Date.now() - startTime.value })
}

function onTouchEnd() {
  if (!dragging.value) return
  dragging.value = false
  document.removeEventListener('touchmove', onTouchMove)
  document.removeEventListener('touchend', onTouchEnd)
  doVerify()
}

function doVerify() {
  const durationMs = Date.now() - startTime.value
  if (durationMs < 300) {
    emit('fail')
    refreshCaptcha()
    return
  }
  if (Math.abs(sliderX.value - targetX) <= 5) {
    emit('success')
  } else {
    emit('fail')
    refreshCaptcha()
  }
}

watch(() => props.visible, (val) => {
  if (val) {
    nextTick(() => refreshCaptcha())
  }
})

onMounted(() => {
  if (props.visible) {
    nextTick(() => drawCaptcha())
  }
})
</script>

<template>
  <div class="slider-captcha-root">
    <!-- inline 模式：只显示滑块内容（无对话框壳） -->
    <template v-if="inline">
      <div class="slider-captcha-container">
        <div class="canvas-wrapper">
          <canvas ref="canvasRef" :width="bgWidth" :height="bgHeight" />
          <div
            class="piece-overlay"
            :style="{
              width: pieceSize + 'px',
              height: pieceSize + 'px',
              left: sliderX + 'px',
              top: targetY + 'px',
              background: dragging ? 'rgba(79,172,254,0.5)' : 'transparent',
              boxShadow: dragging ? '0 0 8px rgba(79,172,254,0.6)' : 'none',
              borderRadius: '4px',
              transition: dragging ? 'none' : 'left 0.1s',
            }"
          />
        </div>
        <div class="slider-track">
          <div
            class="slider-btn"
            :style="{ left: sliderX + 'px' }"
            @mousedown="onMouseDown"
            @touchstart="onTouchStart"
          >
            <span class="slider-icon">→</span>
          </div>
          <span class="slider-hint" v-if="sliderX === 0">拖动滑块完成验证</span>
        </div>
      </div>
    </template>

    <!-- dialog 模式：全屏弹窗（用于操作速度检测触发） -->
    <template v-else>
      <el-dialog
        :model-value="visible"
        title="请完成滑块验证"
        width="340px"
        :close-on-click-modal="false"
        :close-on-press-escape="false"
        :show-close="false"
        @update:model-value="emit('close')"
      >
        <div class="slider-captcha-container">
          <div class="canvas-wrapper">
            <canvas ref="canvasRef" :width="bgWidth" :height="bgHeight" />
            <div
              class="piece-overlay"
              :style="{
                width: pieceSize + 'px',
                height: pieceSize + 'px',
                left: sliderX + 'px',
                top: targetY + 'px',
                background: dragging ? 'rgba(79,172,254,0.5)' : 'transparent',
                boxShadow: dragging ? '0 0 8px rgba(79,172,254,0.6)' : 'none',
                borderRadius: '4px',
                transition: dragging ? 'none' : 'left 0.1s',
              }"
            />
          </div>
          <div class="slider-track">
            <div
              class="slider-btn"
              :style="{ left: sliderX + 'px' }"
              @mousedown="onMouseDown"
              @touchstart="onTouchStart"
            >
              <span class="slider-icon">→</span>
            </div>
            <span class="slider-hint" v-if="sliderX === 0">请拖动滑块完成验证</span>
          </div>
        </div>
        <template #footer>
          <el-button size="small" @click="refreshCaptcha">换一张</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<style scoped>
.slider-captcha-root {
  display: contents;
}

.slider-captcha-container {
  user-select: none;
}

.canvas-wrapper {
  position: relative;
  width: 310px;
  height: 155px;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}

.canvas-wrapper canvas {
  display: block;
}

.piece-overlay {
  position: absolute;
  pointer-events: none;
  border: 2px solid rgba(255, 255, 255, 0.8);
}

.slider-track {
  position: relative;
  width: 310px;
  height: 40px;
  background: #e8e8e8;
  border-radius: 20px;
  overflow: hidden;
}

.slider-btn {
  position: absolute;
  top: 2px;
  left: 0;
  width: 36px;
  height: 36px;
  background: #fff;
  border-radius: 50%;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.2);
  cursor: grab;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
}

.slider-btn:active {
  cursor: grabbing;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.3);
}

.slider-icon {
  font-size: 18px;
  color: #4facfe;
  font-weight: bold;
}

.slider-hint {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  color: #999;
  font-size: 13px;
  pointer-events: none;
  white-space: nowrap;
}
</style>

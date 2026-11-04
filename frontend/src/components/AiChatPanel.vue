<script setup lang="ts">
import { ref, watch, onMounted, nextTick } from 'vue'
import { useAiStore } from '@/stores/ai'
import { ChatDotRound, Close, Loading, Delete, CopyDocument, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'
import { Marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'

const md = new Marked(
  { gfm: true, breaks: true },
  markedHighlight({
    emptyLangClass: 'hljs',
    langPrefix: 'hljs language-',
    highlight(code, lang) {
      const language = hljs.getLanguage(lang) ? lang : 'plaintext'
      return hljs.highlight(code, { language }).value
    }
  })
)

const aiStore = useAiStore()

const inputText = ref('')
const messagesContainer = ref<HTMLElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)
const showScrollBtn = ref(false)

// 面板尺寸（固定）
const PANEL_WIDTH = 460
const PANEL_HEIGHT = 600

// 面板位置：页面居中
const panelPos = ref({ x: 0, y: 0 })

function centerPanel() {
  const w = window.innerWidth
  const h = window.innerHeight
  panelPos.value = {
    x: Math.max(12, (w - PANEL_WIDTH) / 2),
    y: Math.max(12, (h - PANEL_HEIGHT) / 2),
  }
}


// ==================== 拖拽 ====================
const isDragging = ref(false)
const DRAG_THRESHOLD = 5
let mouseDownInfo: { x: number; y: number; posX: number; posY: number } | null = null

function onHeaderMouseDown(e: MouseEvent) {
  if (e.button !== 0) return
  // 不拦截按钮点击
  if ((e.target as HTMLElement).closest('.header-actions')) return
  mouseDownInfo = {
    x: e.clientX,
    y: e.clientY,
    posX: panelPos.value.x,
    posY: panelPos.value.y,
  }
  window.addEventListener('mousemove', onHeaderMouseMove)
  window.addEventListener('mouseup', onHeaderMouseUp)
}

function onHeaderMouseMove(e: MouseEvent) {
  if (!mouseDownInfo) return
  const dx = e.clientX - mouseDownInfo.x
  const dy = e.clientY - mouseDownInfo.y
  if (!isDragging.value && (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD)) {
    isDragging.value = true
    document.body.style.userSelect = 'none'
  }
  if (isDragging.value) {
    const pw = panelRef.value?.offsetWidth || PANEL_WIDTH
    const ph = panelRef.value?.offsetHeight || PANEL_HEIGHT
    const w = window.innerWidth
    const h = window.innerHeight
    panelPos.value = {
      x: Math.max(0, Math.min(mouseDownInfo.posX + dx, w - pw)),
      y: Math.max(0, Math.min(mouseDownInfo.posY + dy, h - ph)),
    }
  }
}

function onHeaderMouseUp() {
  window.removeEventListener('mousemove', onHeaderMouseMove)
  window.removeEventListener('mouseup', onHeaderMouseUp)
  document.body.style.userSelect = ''
  if (isDragging.value) {
    setTimeout(() => { isDragging.value = false }, 0)
  }
  mouseDownInfo = null
}

// ==================== 关闭 ====================

onMounted(async () => {
  // 初始化面板位置：页面居中
  centerPanel()
  // 加载消息
  await aiStore.loadMessages()
  prevMsgCount = aiStore.messages.length
})

// ==================== 消息操作 ====================

// 监听消息变化自动滚动到底部（仅新增消息时滚动，删除和加载更多时不滚动）
let prevMsgCount = 0
let isLoadingMore = false

function handleLoadMore() {
  isLoadingMore = true
  aiStore.loadMoreMessages().then(() => {
    isLoadingMore = false
  }).catch(() => { isLoadingMore = false })
}

watch(() => aiStore.messages.length, (newLen) => {
  if (newLen > prevMsgCount && !isLoadingMore) {
    aiStore.scrollToBottom()
  }
  prevMsgCount = newLen
})

function handleSend() {
  const text = inputText.value.trim()
  if (!text || aiStore.streaming) return
  inputText.value = ''
  aiStore.sendMessage(text)
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

async function handleDelete(msg: { id: number }) {
  await aiStore.removeMessage(msg.id)
  ElMessage.success('已删除')
}

async function handleCopy(content: string) {
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success('已复制')
  } catch {
    const ta = document.createElement('textarea')
    ta.value = content
    ta.style.position = 'fixed'
    ta.style.left = '-9999px'
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('已复制')
  }
}

function isUser(msg: { role: string }) {
  return msg.role === 'USER'
}

function safeContent(content: string, streaming = false) {
  let text = content || ''
  // 流式输出时，补全未闭合的代码块，避免闪烁
  if (streaming) {
    const fenceCount = (text.match(/^```/gm) || []).length
    if (fenceCount % 2 !== 0) {
      text += '\n```'
    }
  }
  const html = md.parse(text) as string
  return DOMPurify.sanitize(html)
}

// ==================== 代码块增强（语言标签 + 复制按钮）====================

function enhanceCodeBlocks(el: HTMLElement | null) {
  if (!el) return
  el.querySelectorAll('pre code.hljs').forEach((block) => {
    if (block.parentElement?.querySelector('.code-toolbar')) return

    const langClass = [...block.classList].find(c => c.startsWith('language-'))
    const lang = langClass ? langClass.replace('language-', '') : 'code'

    const toolbar = document.createElement('div')
    toolbar.className = 'code-toolbar'

    const langLabel = document.createElement('span')
    langLabel.className = 'code-lang'
    langLabel.textContent = lang

    const copyBtn = document.createElement('button')
    copyBtn.className = 'code-copy-btn'
    copyBtn.textContent = '复制'
    copyBtn.onclick = () => {
      navigator.clipboard.writeText(block.textContent || '').then(() => {
        copyBtn.textContent = '已复制'
        setTimeout(() => { copyBtn.textContent = '复制' }, 2000)
      }).catch(() => {
        const ta = document.createElement('textarea')
        ta.value = block.textContent || ''
        ta.style.position = 'fixed'
        ta.style.left = '-9999px'
        document.body.appendChild(ta)
        ta.select()
        document.execCommand('copy')
        document.body.removeChild(ta)
        copyBtn.textContent = '已复制'
        setTimeout(() => { copyBtn.textContent = '复制' }, 2000)
      })
    }

    toolbar.appendChild(langLabel)
    toolbar.appendChild(copyBtn)
    block.parentElement?.insertBefore(toolbar, block.parentElement.firstChild)
  })
}

// 流式输出时持续增强代码块
watch(() => aiStore.streamingContent, () => {
  if (aiStore.streaming) {
    nextTick(() => {
      const bubbles = document.querySelectorAll('.ai-row .ai-bubble')
      const lastBubble = bubbles[bubbles.length - 1] as HTMLElement
      if (lastBubble) enhanceCodeBlocks(lastBubble)
    })
  }
})

// 流式结束时重新增强（streaming 从 true → false，状态变 DONE 后 v-html 重新渲染会丢失 toolbar）
watch(() => aiStore.streaming, (newVal, oldVal) => {
  if (oldVal && !newVal) {
    nextTick(() => {
      const bubbles = document.querySelectorAll('.ai-row .ai-bubble')
      const lastBubble = bubbles[bubbles.length - 1] as HTMLElement
      if (lastBubble) enhanceCodeBlocks(lastBubble)
    })
  }
})

// 历史消息加载完成后增强代码块（loading 从 true → false 表示加载完成，DOM 已渲染）
watch(() => aiStore.loading, (newVal, oldVal) => {
  if (oldVal && !newVal) {
    nextTick(() => {
      const messagesEl = messagesContainer.value
      if (messagesEl) enhanceCodeBlocks(messagesEl)
    })
  }
})

function handleScroll(e: Event) {
  const el = e.target as HTMLElement
  const distFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  showScrollBtn.value = distFromBottom > 200
  if (el.scrollTop === 0 && !aiStore.loading && !aiStore.streaming) {
    handleLoadMore()
  }
}

function handleScrollToBottom() {
  aiStore.scrollToBottom()
}

function handleClose() {
  aiStore.closePanel()
}
</script>

<template>
  <div
    ref="panelRef"
    class="ai-panel"
    :style="{
      left: panelPos.x + 'px',
      top: panelPos.y + 'px',
      width: PANEL_WIDTH + 'px',
      height: PANEL_HEIGHT + 'px',
    }"
  >
    <!-- 标题栏（可拖拽） -->
    <div class="panel-header" @mousedown.left="onHeaderMouseDown">
      <div class="header-left">
        <el-icon :size="18" color="#409eff"><ChatDotRound /></el-icon>
        <span class="title">生活帖助手</span>
      </div>
      <div class="header-actions">
        <el-icon class="action-icon close-icon" :size="28" @click.stop="handleClose" title="关闭">
          <Close />
        </el-icon>
      </div>
    </div>

      <!-- 消息列表 -->
      <div
        ref="messagesContainer"
        class="ai-chat-messages"
        @scroll="handleScroll"
      >
        <div v-if="aiStore.loading" class="loading-hint">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>加载中...</span>
        </div>

        <div v-if="aiStore.messages.length === 0 && !aiStore.loading" class="empty-hint">
          <el-icon :size="40" color="#dcdfe6"><ChatDotRound /></el-icon>
          <p>问我任何关于生活帖平台的问题</p>
          <div class="quick-questions">
            <span class="quick-q" @click="inputText = '平台有哪些功能？'">平台功能介绍</span>
            <span class="quick-q" @click="inputText = '怎么发帖？'">如何发帖</span>
            <span class="quick-q" @click="inputText = '推荐一些好用的功能'">功能推荐</span>
          </div>
        </div>

        <template v-for="msg in aiStore.messages" :key="msg.id">
          <!-- 用户消息 -->
          <div v-if="isUser(msg)" class="message-row user-row">
            <div class="user-msg-wrapper">
              <div class="message-bubble user-bubble">
                <div class="message-text">{{ msg.content }}</div>
              </div>
              <div class="msg-actions msg-actions--hidden">
                <el-icon class="msg-action-btn" @click="handleCopy(msg.content)" title="复制"><CopyDocument /></el-icon>
                <el-icon class="msg-action-btn delete-btn" @click="handleDelete(msg)" title="删除"><Delete /></el-icon>
              </div>
            </div>
          </div>

          <!-- AI 消息 -->
          <div v-else class="message-row ai-row">
            <div class="message-bubble ai-bubble" :class="{ 'status-failed': msg.status === 'FAILED', 'streaming-bubble': msg.status === 'STREAMING' }">
              <!-- 兜底横幅 -->
              <div v-if="msg.status === 'DONE' && !msg.fromKnowledge" class="fallback-banner">
                <el-icon :size="14"><Warning /></el-icon>
                <span>以下为 AI 根据自身知识生成，与平台知识库无关</span>
              </div>

              <div class="message-text" v-html="safeContent(msg.status === 'STREAMING' ? aiStore.streamingContent : msg.content, msg.status === 'STREAMING')"></div>
              <div v-if="msg.status === 'STREAMING'" class="streaming-cursor"></div>

              <!-- 引用来源 -->
              <div v-if="msg.sources?.length" class="sources">
                <span class="sources-label">引用:</span>
                <span
                  v-for="s in msg.sources"
                  :key="s.knowledgeId || s.title"
                  class="source-tag"
                >
                  {{ s.title }}
                </span>
              </div>

              <!-- 失败状态 -->
              <div v-if="msg.status === 'FAILED'" class="failed-hint">
                {{ msg.content || '生成失败，请重试' }}
              </div>

              <div v-if="msg.status !== 'STREAMING'" class="msg-actions">
                <el-icon v-if="msg.status === 'DONE'" class="msg-action-btn" @click="handleCopy(msg.content)" title="复制"><CopyDocument /></el-icon>
                <el-icon class="msg-action-btn delete-btn" @click="handleDelete(msg)" title="删除"><Delete /></el-icon>
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- 回到底部按钮 -->
      <transition name="fade">
        <div v-show="showScrollBtn" class="scroll-bottom-btn" @click="handleScrollToBottom">
          <el-icon :size="18"><ArrowDown /></el-icon>
        </div>
      </transition>

      <!-- 输入区域 -->
      <div class="panel-input">
        <el-input
          v-model="inputText"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 3 }"
          :placeholder="aiStore.streaming ? 'AI 思考中...' : '输入你的问题...'"
          :disabled="aiStore.streaming"
          @keydown="handleKeydown"
          resize="none"
        />
        <el-button
          type="primary"
          :icon="ChatDotRound"
          circle
          :disabled="aiStore.streaming || !inputText.trim()"
          :loading="aiStore.streaming"
          @click="handleSend"
          class="send-btn"
        />
      </div>
    </div>
</template>

<style scoped>
.ai-panel {
  position: fixed;
  z-index: 10000;
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: box-shadow 0.3s;
  border: 1px solid var(--border-color);
}

/* 标题栏 */
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-card);
  cursor: grab;
  user-select: none;
  flex-shrink: 0;
}

.panel-header:active {
  cursor: grabbing;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-icon {
  cursor: pointer;
  color: var(--text-muted);
  transition: color 0.2s;
  padding: 6px;
  border-radius: 6px;
}

.action-icon:hover {
  color: var(--text-primary);
  background: rgba(0, 0, 0, 0.06);
}

.close-icon:hover {
  color: #f56c6c;
}

/* 消息列表 */
.ai-chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.loading-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px;
  color: var(--text-muted);
  font-size: 13px;
}

.empty-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px 16px;
  color: var(--text-muted);
  font-size: 13px;
}

.quick-questions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
  margin-top: 8px;
}

.quick-q {
  padding: 4px 12px;
  background: var(--primary-bg);
  color: var(--primary);
  border-radius: var(--radius-full);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.quick-q:hover {
  background: var(--primary);
  color: #fff;
}

/* 消息行 */
.message-row {
  display: flex;
}

.user-row {
  justify-content: flex-end;
}

.ai-row {
  justify-content: flex-start;
}

/* 消息气泡 */
.message-bubble {
  max-width: 85%;
  padding: 10px 14px;
  border-radius: var(--radius-md);
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.user-bubble {
  background: var(--primary);
  color: #fff;
  border-bottom-right-radius: 4px;
  max-width: 100%;
}

.ai-bubble {
  background: var(--bg-card-hover);
  color: var(--text-primary);
  border-bottom-left-radius: 4px;
  border: 1px solid var(--border-color);
}

.ai-bubble.status-failed {
  opacity: 0.7;
}

.message-text {
  white-space: pre-wrap;
}

/* AI 气泡内 Markdown 样式 */
.ai-bubble .message-text {
  white-space: normal;
  line-height: 1.75;
}
.ai-bubble .message-text :deep(p) {
  margin: 0 0 10px;
}
.ai-bubble .message-text :deep(p:last-child) {
  margin-bottom: 0;
}
.ai-bubble .message-text :deep(strong) {
  font-weight: 600;
}
.ai-bubble .message-text :deep(pre) {
  border-radius: 8px;
  padding: 0;
  margin: 10px 0;
  overflow: hidden;
}
.ai-bubble .message-text :deep(pre code) {
  display: block;
  padding: 14px;
  overflow-x: auto;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
}
.ai-bubble .message-text :deep(:not(pre) > code) {
  background: rgba(0, 0, 0, 0.08);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 0.9em;
}

/* 代码块工具栏 */
.ai-bubble .message-text :deep(.code-toolbar) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 14px;
  background: #161b22;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  font-size: 12px;
}
.ai-bubble .message-text :deep(.code-lang) {
  color: #8b949e;
}
.ai-bubble .message-text :deep(.code-copy-btn) {
  background: none;
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: #8b949e;
  padding: 2px 10px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.ai-bubble .message-text :deep(.code-copy-btn:hover) {
  color: #e6edf3;
  border-color: rgba(255, 255, 255, 0.3);
}
.ai-bubble .message-text :deep(ul),
.ai-bubble .message-text :deep(ol) {
  padding-left: 22px;
  margin: 6px 0;
}
.ai-bubble .message-text :deep(li) {
  margin: 3px 0;
}
.ai-bubble .message-text :deep(li > ul),
.ai-bubble .message-text :deep(li > ol) {
  margin: 2px 0;
}
.ai-bubble .message-text :deep(blockquote) {
  border-left: 3px solid var(--primary);
  padding-left: 12px;
  margin: 10px 0;
  color: var(--text-muted);
}
.ai-bubble .message-text :deep(h1),
.ai-bubble .message-text :deep(h2),
.ai-bubble .message-text :deep(h3) {
  margin: 12px 0 6px;
  font-size: 15px;
  font-weight: 600;
}
.ai-bubble .message-text :deep(h4),
.ai-bubble .message-text :deep(h5),
.ai-bubble .message-text :deep(h6) {
  margin: 10px 0 4px;
  font-size: 14px;
  font-weight: 600;
}
.ai-bubble .message-text :deep(hr) {
  border: none;
  border-top: 1px solid var(--border-color);
  margin: 12px 0;
}
.ai-bubble .message-text :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 10px 0;
  font-size: 13px;
}
.ai-bubble .message-text :deep(th),
.ai-bubble .message-text :deep(td) {
  border: 1px solid var(--border-color);
  padding: 6px 10px;
  text-align: left;
}
.ai-bubble .message-text :deep(th) {
  background: rgba(0, 0, 0, 0.04);
  font-weight: 600;
}
.ai-bubble .message-text :deep(a) {
  color: var(--primary);
  text-decoration: none;
}
.ai-bubble .message-text :deep(a:hover) {
  text-decoration: underline;
}
.ai-bubble .message-text :deep(img) {
  max-width: 100%;
  border-radius: 6px;
}

/* 消息操作按钮 */
.msg-actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 6px;
}

/* 用户消息：hover 才显示 */
.user-msg-wrapper {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  max-width: 85%;
}

.msg-actions--hidden {
  visibility: hidden;
  opacity: 0;
  transition: opacity 0.2s, visibility 0.2s;
}

.user-msg-wrapper:hover .msg-actions--hidden {
  visibility: visible;
  opacity: 1;
}

.msg-action-btn {
  cursor: pointer;
  color: var(--text-muted);
  font-size: 18px;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.15s;
}

.msg-action-btn:hover {
  color: var(--text-primary);
  background: rgba(0, 0, 0, 0.06);
}

.msg-action-btn.delete-btn:hover {
  color: #f56c6c;
  background: #fef0f0;
}

/* 兜底横幅 */
.fallback-banner {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  margin-bottom: 8px;
  background: #fffbe6;
  border-radius: var(--radius-sm);
  color: #ad8b00;
  font-size: 12px;
}

/* 引用来源 */
.sources {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--border-color);
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.sources-label {
  font-size: 12px;
  color: var(--text-muted);
}

.source-tag {
  padding: 2px 8px;
  background: #ecf5ff;
  color: #409eff;
  border-radius: var(--radius-full);
  font-size: 11px;
  cursor: pointer;
}

.source-tag:hover {
  background: #409eff;
  color: #fff;
}

.failed-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #f56c6c;
}

/* 流式动画 */
.streaming-bubble {
  padding: 12px 14px;
}

.streaming-cursor::after {
  content: '\2588';
  animation: blink 1s step-end infinite;
  color: var(--primary);
}

@keyframes blink {
  50% { opacity: 0; }
}

/* 回到底部按钮 */
.scroll-bottom-btn {
  position: absolute;
  bottom: 70px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 10;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--primary);
  color: #fff;
  border-radius: 50%;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  transition: transform 0.2s, box-shadow 0.2s;
}

.scroll-bottom-btn:hover {
  transform: translateX(-50%) scale(1.1);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 输入区域 */
.panel-input {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--border-color);
  background: var(--bg-card);
  flex-shrink: 0;
}

.panel-input :deep(.el-textarea__inner) {
  border-radius: var(--radius-md);
  font-size: 14px;
  padding: 8px 12px;
  max-height: 80px;
}

.send-btn {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
}
</style>

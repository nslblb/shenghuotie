import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AiMessageVO } from '@/api/ai'
import { getMessages, deleteMessage, streamChat } from '@/api/ai'

// 压缩参数（设计文档 §6.3）
const WINDOW_SIZE = 8
const WORK_WATERMARK = 8000
const HARD_LIMIT = 30000

function estimateTokens(text: string): number {
  return Math.ceil(text.length / 1.5)
}

export const useAiStore = defineStore('ai', () => {
  const messages = ref<AiMessageVO[]>([])
  const loading = ref(false)
  const streaming = ref(false)
  const streamingContent = ref('')
  const panelVisible = ref(false)
  const panelExpanded = ref(false)
  const ballX = ref(0)
  const ballY = ref(0)

  let currentPage = 0
  let hasMore = true
  let abortController: AbortController | null = null

  // ==================== 消息加载 ====================

  async function loadMessages() {
    loading.value = true
    try {
      const res = await getMessages({ page: 0, size: 20 })
      const data = res.data.data
      // 将历史中 STREAMING 状态的消息重置为 FAILED（上次异常中断留下的）
      messages.value = data.content.reverse().map((msg) => {
        if (msg.status === 'STREAMING' && msg.role === 'ASSISTANT') {
          return { ...msg, status: 'FAILED', content: msg.content || '回答未能完整获取，请重试' }
        }
        return msg
      })
      currentPage = 0
      hasMore = data.totalPages > 1
    } catch {
      // 错误由 Axios 拦截器处理
    } finally {
      loading.value = false
    }
  }

  async function loadMoreMessages() {
    if (!hasMore || loading.value) return
    loading.value = true
    try {
      currentPage++
      const res = await getMessages({ page: currentPage, size: 20 })
      const data = res.data.data
      const older = data.content.reverse()
      messages.value = [...older, ...messages.value]
      hasMore = data.totalPages > currentPage + 1
    } catch {
      // 错误由 Axios 拦截器处理
    } finally {
      loading.value = false
    }
  }

  // ==================== 发送消息（SSE 流式） ====================

  async function sendMessage(content: string) {
    if (streaming.value || !content.trim()) return

    // 添加用户消息到列表
    const userMsg: AiMessageVO = {
      id: Date.now(), // 临时 ID，完成后会被替换
      role: 'USER',
      content: content.trim(),
      sources: [],
      fromKnowledge: true,
      status: 'DONE',
      createdAt: new Date().toISOString(),
    }
    messages.value.push(userMsg)

    // 创建占位 AI 消息
    const placeholderId = -(Date.now())
    const aiMsg: AiMessageVO = {
      id: placeholderId,
      role: 'ASSISTANT',
      content: '',
      sources: [],
      fromKnowledge: true,
      status: 'STREAMING',
      createdAt: new Date().toISOString(),
    }
    messages.value.push(aiMsg)

    streaming.value = true
    streamingContent.value = ''
    abortController = new AbortController()

    let doneCalled = false

    // 150 秒超时保护：如果 AI 还没回答完，强制退出 streaming 状态
    const timeoutId = setTimeout(() => {
      if (!doneCalled) {
        doneCalled = true
        aiMsg.content = streamingContent.value || 'AI 响应超时，请重试'
        aiMsg.status = 'FAILED'
        streaming.value = false
        streamingContent.value = ''
        abortController?.abort()
        abortController = null
      }
    }, 150000)

    await streamChat(
      content.trim(),
      // onToken
      (token: string) => {
        streamingContent.value += token
        aiMsg.content = streamingContent.value
        // 自动滚动到底部
        scrollToBottom()
      },
      // onDone
      (messageId: number) => {
        doneCalled = true
        clearTimeout(timeoutId)
        aiMsg.id = messageId
        aiMsg.status = 'DONE'
        streaming.value = false
        streamingContent.value = ''
        abortController = null
      },
      // onError
      (error: string) => {
        doneCalled = true
        clearTimeout(timeoutId)
        aiMsg.content = streamingContent.value || error || '生成失败，请重试'
        aiMsg.status = 'FAILED'
        streaming.value = false
        streamingContent.value = ''
        abortController = null
      },
      abortController.signal,
    )

    // 流结束但 onDone/onError 都没调用（后端异常断开等），兜底重置状态
    if (!doneCalled) {
      clearTimeout(timeoutId)
      aiMsg.content = streamingContent.value || aiMsg.content || '回答未能完整获取，请重试'
      aiMsg.status = 'FAILED'
      streaming.value = false
      streamingContent.value = ''
      abortController = null
    }
  }

  // ==================== 删除消息 ====================

  async function removeMessage(id: number) {
    const index = messages.value.findIndex((m) => m.id === id)
    if (index === -1) return

    const removed = messages.value.splice(index, 1)[0]

    // 乐观删除，API 失败时回滚
    try {
      await deleteMessage(id)
    } catch {
      messages.value.splice(index, 0, removed)
    }
  }

  // ==================== 面板控制 ====================

  function openPanel() {
    panelVisible.value = true
  }

  function closePanel() {
    panelVisible.value = false
    abortController?.abort()
    abortController = null
    streaming.value = false
    streamingContent.value = ''
  }

  function toggleExpanded() {
    panelExpanded.value = !panelExpanded.value
  }

  // ==================== 工具 ====================

  function scrollToBottom() {
    setTimeout(() => {
      const el = document.querySelector('.ai-chat-messages')
      if (el) {
        el.scrollTop = el.scrollHeight
      }
    }, 50)
  }

  return {
    messages,
    loading,
    streaming,
    streamingContent,
    panelVisible,
    panelExpanded,
    loadMessages,
    loadMoreMessages,
    sendMessage,
    removeMessage,
    openPanel,
    closePanel,
    toggleExpanded,
    scrollToBottom,
    WINDOW_SIZE,
    WORK_WATERMARK,
    HARD_LIMIT,
  }
})

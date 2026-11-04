import { getToken } from '@/utils/auth'

// ==================== AI 类型 ====================

export interface AiMessageVO {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  sources: MessageSource[]
  fromKnowledge: boolean
  status: 'STREAMING' | 'DONE' | 'FAILED'
  createdAt: string
}

export interface MessageSource {
  knowledgeId: number | null
  title: string
}

export interface ConversationVO {
  id: number
  title: string
  createdAt: string
  updatedAt: string
}

export interface SseDoneEvent {
  messageId: number
}

export interface SseErrorEvent {
  message: string
}

// ==================== SSE 流式问答 ====================

/**
 * 流式聊天：使用原生 fetch + ReadableStream 解析 SSE，绕过 Axios。
 *
 * @param message   用户消息
 * @param onToken   每收到一个 token 回调
 * @param onDone    流结束时回调（messageId）
 * @param onError   错误回调
 */
export async function streamChat(
  message: string,
  onToken: (token: string) => void,
  onDone: (messageId: number) => void,
  onError: (message: string) => void,
  signal?: AbortSignal,
) {
  const token = getToken()
  if (!token) {
    onError('未登录，请先登录')
    return
  }

  let res: Response
  try {
    res = await fetch('/api/ai/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ message }),
      signal,
    })
  } catch {
    onError('网络连接失败，请检查网络后重试')
    return
  }

  if (res.status === 401) {
    onError('登录已过期，请重新登录')
    return
  }
  if (!res.ok) {
    // 尝试解析后端返回的实际错误消息
    try {
      const body = await res.json()
      if (body.message) {
        onError(body.message)
        return
      }
      if (body.data?.message) {
        onError(body.data.message)
        return
      }
    } catch {
      // 解析失败则用默认消息
    }
    onError('AI 服务暂时不可用，请稍后重试')
    return
  }
  if (!res.body) {
    onError('无法获取流式响应')
    return
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  function processEvent(data: string, type: string) {
    if (!data) return

    if (type === 'done') {
      try {
        const parsed: SseDoneEvent = JSON.parse(data)
        onDone(parsed.messageId)
      } catch {
        // ignore
      }
    } else if (type === 'error') {
      try {
        const parsed: SseErrorEvent = JSON.parse(data)
        onError(parsed.message)
      } catch {
        onError('AI 服务暂时不可用')
      }
    } else {
      // 普通 token
      onToken(data)
    }
  }

  // typeRef 和 dataLines 提到循环外部，保证跨 chunk 时状态不丢失
  const typeRef = { type: 'message' }
  const dataLines: string[] = []

  while (true) {
    const { done, value } = await reader.read()

    // 每次读取后都处理 buffer 中完整的行
    if (value) {
      buffer += decoder.decode(value, { stream: true })
    }
    const lines = buffer.split('\n')
    // 最后一段可能不完整，留到下轮；但若流已结束，全部处理
    buffer = done ? '' : (lines.pop() || '')

    for (const line of lines) {
      if (line.startsWith('event:')) {
        typeRef.type = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        // SSE 规范：data: 后最多去掉一个空格，保留内容中的空白
        dataLines.push(line.startsWith('data: ') ? line.slice(6) : line.slice(5))
      } else if (line === '') {
        // 空行 = 事件边界，派发当前事件
        if (dataLines.length > 0) {
          processEvent(dataLines.join('\n'), typeRef.type)
          dataLines.length = 0
          typeRef.type = 'message'
        }
      }
    }

    if (done) {
      // 流结束，处理剩余数据
      if (dataLines.length > 0) {
        processEvent(dataLines.join('\n'), typeRef.type)
      }
      break
    }
  }
}

// ==================== 对话/消息 API ====================

import request from './index'
import type { Result, PageData } from '@/types'

export function getConversation() {
  return request.get<Result<ConversationVO>>('/ai/conversation')
}

export function getMessages(params: { page?: number; size?: number }) {
  return request.get<Result<PageData<AiMessageVO>>>('/ai/messages', { params })
}

export function deleteMessage(id: number) {
  return request.delete<Result<void>>(`/ai/messages/${id}`)
}

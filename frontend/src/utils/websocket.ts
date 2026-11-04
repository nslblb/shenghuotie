import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { getToken } from './auth'

type MessageHandler = (message: IMessage) => void

// key → { destination, handler } 用于 unsubscribe 定位
const keyMap = new Map<string, { destination: string; handler: MessageHandler }>()
// destination → 该目的地下所有 handler 的集合
const destHandlers = new Map<string, Set<MessageHandler>>()
// destination → 实际的 STOMP 订阅
const stompSubs = new Map<string, StompSubscription>()

let stompClient: Client | null = null
let reconnectAttempts = 0
const MAX_RECONNECT_ATTEMPTS = 5
const BASE_RECONNECT_DELAY = 1000

function ensureSubscription(destination: string): void {
  if (stompSubs.has(destination)) return
  if (!stompClient?.connected) return

  const handlers = destHandlers.get(destination)
  if (!handlers || handlers.size === 0) return

  const sub = stompClient.subscribe(destination, (message: IMessage) => {
    const currentHandlers = destHandlers.get(destination)
    if (!currentHandlers) return
    for (const handler of currentHandlers) {
      handler(message)
    }
  })
  stompSubs.set(destination, sub)
}

function teardownSubscription(destination: string): void {
  const sub = stompSubs.get(destination)
  if (sub) {
    sub.unsubscribe()
    stompSubs.delete(destination)
  }
}

export function connectWebSocket(): void {
  const token = getToken()
  if (!token) return

  if (stompClient?.active) return

  stompClient = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    connectHeaders: {
      Authorization: `Bearer ${token}`,
    },
    reconnectDelay: 0,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      reconnectAttempts = 0
      // 连接成功，为所有有 handler 的 destination 建立 STOMP 订阅
      for (const [destination, handlers] of destHandlers) {
        if (handlers.size > 0) {
          ensureSubscription(destination)
        }
      }
    },
    onStompError: (frame) => {
      console.error('STOMP error:', frame.headers['message'])
    },
    onWebSocketClose: () => {
      // 连接断开，清理 STOMP 订阅对象（destHandlers 和 keyMap 保留）
      stompSubs.clear()
      if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
        const delay = BASE_RECONNECT_DELAY * Math.pow(2, reconnectAttempts)
        reconnectAttempts++
        setTimeout(() => {
          const currentToken = getToken()
          if (currentToken) {
            connectWebSocket()
          }
        }, delay)
      }
    },
  })

  stompClient.activate()
}

export function disconnectWebSocket(): void {
  if (stompClient?.active) {
    stompClient.deactivate()
  }
  stompClient = null
  stompSubs.clear()
  reconnectAttempts = MAX_RECONNECT_ATTEMPTS
  // 注意：保留 destHandlers 和 keyMap，重连后可自动恢复订阅
}

/**
 * 订阅消息。同一 destination 共享一个 STOMP 订阅，消息扇出给所有注册的 handler。
 * @param key 唯一标识（如 'navbar-messages'、'chat-messages'），用于后续 unsubscribe
 * @param destination STOMP 目的地
 * @param handler 消息处理函数
 */
export function subscribe(key: string, destination: string, handler: MessageHandler): void {
  // 先清理同 key 的旧订阅
  unsubscribe(key)

  // 注册到 keyMap
  keyMap.set(key, { destination, handler })

  // 添加到 destination 的 handler 集合
  let handlers = destHandlers.get(destination)
  if (!handlers) {
    handlers = new Set()
    destHandlers.set(destination, handlers)
  }
  handlers.add(handler)

  // 如果已连接且该 destination 尚无 STOMP 订阅，创建之
  if (stompClient?.connected) {
    ensureSubscription(destination)
  }
}

/**
 * 取消订阅。
 * @param key 之前 subscribe 时使用的 key
 */
export function unsubscribe(key: string): void {
  const info = keyMap.get(key)
  if (!info) return

  const { destination, handler } = info
  keyMap.delete(key)

  const handlers = destHandlers.get(destination)
  if (handlers) {
    handlers.delete(handler)
    // 如果该 destination 下没有 handler 了，清理 STOMP 订阅
    if (handlers.size === 0) {
      destHandlers.delete(destination)
      teardownSubscription(destination)
    }
  }
}

export function isConnected(): boolean {
  return stompClient?.active ?? false
}

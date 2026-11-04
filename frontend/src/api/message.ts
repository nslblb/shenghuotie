import request from './index'
import type { Result, PageData } from '@/types'

export interface MessageVO {
  id: number
  senderId: number
  receiverId: number
  content: string
  messageType: string
  isRead: boolean
  isRecalled: boolean
  createdAt: string
}

export interface ConversationVO {
  partnerId: number
  partnerName: string
  partnerAvatar: string | null
  lastMessage: string
  lastMessageType: string
  lastMessageRecalled: boolean
  lastMessageTime: string
  unreadCount: number
}

export function sendMessage(receiverId: number, content: string, messageType: string = 'TEXT') {
  return request.post<Result<MessageVO>>('/messages', { receiverId, content, messageType })
}

export function getConversation(partnerId: number, page: number, size: number = 20) {
  return request.get<Result<PageData<MessageVO>>>(`/messages/conversation/${partnerId}`, { params: { page, size } })
}

export function getConversations() {
  return request.get<Result<ConversationVO[]>>('/messages/conversations')
}

export function markConversationRead(partnerId: number) {
  return request.put<Result<void>>(`/messages/conversation/${partnerId}/read`)
}

export function getMessageUnreadCount() {
  return request.get<Result<number>>('/messages/unread-count')
}

export function checkUserOnline(userId: number) {
  return request.get<Result<boolean>>(`/messages/online/${userId}`)
}

export function deleteConversation(partnerId: number) {
  return request.delete<Result<void>>(`/messages/conversation/${partnerId}`)
}

export function deleteMessages(ids: number[]) {
  return request.delete<Result<void>>('/messages/batch', { data: { ids } })
}

export function recallMessage(id: number) {
  return request.put<Result<void>>(`/messages/${id}/recall`)
}

import request from './index'
import type { Result, NotificationVO, PageData } from '@/types'

export function getNotifications(params: { page?: number; size?: number; type?: string }) {
  return request.get<Result<PageData<NotificationVO>>>('/notifications', { params })
}

export function markAllRead() {
  return request.put<Result<void>>('/notifications/read-all')
}

export function markAsRead(id: number) {
  return request.put<Result<void>>(`/notifications/${id}/read`)
}

export function getUnreadCount() {
  return request.get<Result<number>>('/notifications/unread-count')
}

export function getUnreadCountByType() {
  return request.get<Result<Record<string, number>>>('/notifications/unread-count-by-type')
}

export function deleteNotifications(ids: number[]) {
  return request.delete<Result<void>>('/notifications/batch', { data: { ids } })
}

export function deleteAllNotifications() {
  return request.delete<Result<void>>('/notifications/all')
}

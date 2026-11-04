import request from './index'
import type { Result, CommentVO } from '@/types'

export interface CommentPageData {
  comments: CommentVO[]
  totalPages: number
  totalElements: number
  currentPage: number
}

export function getComments(postId: number, page = 0, size = 20) {
  return request.get<Result<CommentPageData>>(`/posts/${postId}/comments`, {
    params: { page, size }
  })
}

export function createComment(postId: number, data: { content: string; parentId?: number; replyToUserId?: number }) {
  return request.post<Result<CommentVO>>(`/posts/${postId}/comments`, data)
}

export function deleteComment(postId: number, commentId: number) {
  return request.delete<Result<void>>(`/posts/${postId}/comments/${commentId}`)
}

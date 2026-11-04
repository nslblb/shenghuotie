import request from './index'
import type { Result } from '@/types'

export function favoritePost(postId: number) {
  return request.post<Result<void>>(`/posts/${postId}/favorite`)
}

export function unfavoritePost(postId: number) {
  return request.delete<Result<void>>(`/posts/${postId}/favorite`)
}

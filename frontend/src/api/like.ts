import request from './index'
import type { Result } from '@/types'

export function likePost(postId: number) {
  return request.post<Result<void>>(`/posts/${postId}/like`)
}

export function unlikePost(postId: number) {
  return request.delete<Result<void>>(`/posts/${postId}/like`)
}

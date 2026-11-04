import request from './index'
import type { Result, PostVO, PostDetailVO, PageData } from '@/types'

export interface TopicVO {
  id: number
  name: string
  description: string | null
  enabled: boolean
  sortOrder: number
}

export function getTopics() {
  return request.get<Result<TopicVO[]>>('/posts/topics')
}

export function getPostList(params: { page?: number; size?: number; topic?: string; sort?: string }) {
  return request.get<Result<PageData<PostVO>>>('/posts', { params })
}

export function getPostDetail(postId: number) {
  return request.get<Result<PostDetailVO>>(`/posts/${postId}`)
}

export function getUserPosts(userId: number, params: { page?: number; size?: number }) {
  return request.get<Result<PageData<PostVO>>>(`/posts/user/${userId}`, { params })
}

export function createPost(data: { title: string; content: string; topic?: string; imageUrls?: string[] }) {
  return request.post<Result<PostDetailVO>>('/posts', data)
}

export function updatePost(postId: number, data: { title: string; content: string; topic?: string; imageUrls?: string[] }) {
  return request.put<Result<PostDetailVO>>(`/posts/${postId}`, data)
}

export function deletePost(postId: number) {
  return request.delete<Result<void>>(`/posts/${postId}`)
}

export function batchDeletePosts(postIds: number[]) {
  return request.delete<Result<void>>('/posts/batch', { data: postIds })
}

export function appealPost(postId: number, reason?: string) {
  return request.put<Result<void>>(`/posts/${postId}/appeal`, { reason })
}

export function saveDraft(data: { title?: string; content?: string; topic?: string; imageUrls?: string[] }) {
  return request.post<Result<PostDetailVO>>('/posts/draft', data)
}

export function updateDraft(draftId: number, data: { title?: string; content?: string; topic?: string; imageUrls?: string[] }) {
  return request.put<Result<PostDetailVO>>(`/posts/draft/${draftId}`, data)
}

export function getUserDrafts(params: { page?: number; size?: number }) {
  return request.get<Result<PageData<PostVO>>>('/posts/drafts', { params })
}

export function getUserFavorites(params: { page?: number; size?: number }) {
  return request.get<Result<PageData<PostVO>>>('/posts/favorites', { params })
}

export function getUserLikes(params: { page?: number; size?: number }) {
  return request.get<Result<PageData<PostVO>>>('/posts/likes', { params })
}

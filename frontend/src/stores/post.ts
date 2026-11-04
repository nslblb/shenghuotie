import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { PostVO, PageData, Result } from '@/types'
import { getPostList } from '@/api/post'
import request from '@/api/index'

interface CacheEntry {
  posts: PostVO[]
  currentPage: number
  totalPages: number
}

export const usePostStore = defineStore('post', () => {
  const cacheMap = ref<Map<string, CacheEntry>>(new Map())

  function getCacheKey(topic?: string, keyword?: string, page?: number): string {
    if (keyword) return `search:${keyword}:${page}`
    return `topic:${topic || 'all'}:${page}`
  }

  async function fetchPosts(
    topic?: string,
    keyword?: string,
    page = 0
  ): Promise<{ content: PostVO[]; number: number; totalPages: number; fromCache: boolean }> {
    const cacheKey = getCacheKey(topic, keyword, page)
    const cached = cacheMap.value.get(cacheKey)
    if (cached) {
      return { ...cached, fromCache: true }
    }

    let res
    if (keyword) {
      res = await request.get<Result<PageData<PostVO>>>('/posts/search', {
        params: { keyword, page, size: 12 },
      })
    } else {
      res = await getPostList({ page, size: 12, topic })
    }
    const data: PageData<PostVO> = res.data.data
    const entry: CacheEntry = {
      posts: data.content,
      currentPage: data.number,
      totalPages: data.totalPages,
    }
    cacheMap.value.set(cacheKey, entry)
    return { ...entry, fromCache: false }
  }

  function invalidateCache(topic?: string, keyword?: string) {
    if (keyword) {
      for (const key of cacheMap.value.keys()) {
        if (key.startsWith(`search:${keyword}`)) cacheMap.value.delete(key)
      }
    } else {
      for (const key of cacheMap.value.keys()) {
        if (key.startsWith(`topic:${topic || 'all'}`)) cacheMap.value.delete(key)
      }
    }
  }

  function clearAllCache() {
    cacheMap.value.clear()
  }

  return { cacheMap, fetchPosts, invalidateCache, clearAllCache }
})

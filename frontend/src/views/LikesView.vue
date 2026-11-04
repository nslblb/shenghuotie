<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getUserLikes } from '@/api/post'
import type { PostVO } from '@/types'
import PostCard from '@/components/PostCard.vue'

defineOptions({ name: 'likes' })

const posts = ref<PostVO[]>([])
const loading = ref(false)
const currentPage = ref(0)
const totalPages = ref(0)

async function loadPosts(page = 0) {
  loading.value = true
  try {
    const res = await getUserLikes({ page, size: 20 })
    const data = res.data.data
    if (page === 0) {
      posts.value = data.content
    } else {
      posts.value.push(...data.content)
    }
    currentPage.value = data.number
    totalPages.value = data.totalPages
  } finally {
    loading.value = false
  }
}

function loadMore() {
  if (currentPage.value + 1 < totalPages.value) {
    loadPosts(currentPage.value + 1)
  }
}

onMounted(() => loadPosts())
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">我的点赞</h2>
    <div class="post-grid" v-loading="loading">
      <PostCard v-for="post in posts" :key="post.id" :post="post" />
      <el-empty v-if="!loading && posts.length === 0" description="还没有点赞过帖子" />
    </div>
    <div class="load-more" v-if="currentPage + 1 < totalPages">
      <el-button @click="loadMore" :loading="loading">加载更多</el-button>
    </div>
  </div>
</template>

<style scoped>
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.page-container {
  max-width: 1200px;
  margin: 0 auto;
  animation: fadeInUp 0.5s ease both;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 20px;
  color: #1a1a1a;
  position: relative;
  display: inline-block;
}

.page-title::after {
  content: '';
  position: absolute;
  bottom: -4px;
  left: 0;
  width: 100%;
  height: 3px;
  background: #ff6b81;
  border-radius: 2px;
}

.post-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
  min-height: 200px;
}

.post-grid :deep(.post-card) {
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  transition: all 0.3s ease;
}

.post-grid :deep(.post-card:hover) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12);
}

.load-more {
  text-align: center;
  margin-top: 24px;
}

.load-more :deep(.el-button) {
  border-radius: 20px;
}
</style>

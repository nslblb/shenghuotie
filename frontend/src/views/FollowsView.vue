<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { getFollowings } from '@/api/user'
import type { UserVO } from '@/types'
import { useRouter } from 'vue-router'

defineOptions({ name: 'follows' })

const router = useRouter()
const userStore = useUserStore()
const users = ref<UserVO[]>([])
const loading = ref(false)
const currentPage = ref(0)
const hasMore = ref(true)

async function loadFollowings(page = 0) {
  if (!userStore.userInfo?.id) return
  loading.value = true
  try {
    const res = await getFollowings(userStore.userInfo.id, page)
    const data = res.data.data
    if (page === 0) {
      users.value = data.content
    } else {
      users.value = [...users.value, ...data.content]
    }
    currentPage.value = data.number
    hasMore.value = data.number < data.totalPages - 1
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loading.value || !hasMore.value) return
  await loadFollowings(currentPage.value + 1)
}

function goToProfile(userId: number) {
  router.push(`/profile/${userId}`)
}

onMounted(() => loadFollowings())
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">我的关注</h2>
    <div class="user-grid" v-loading="loading">
      <div
        v-for="user in users"
        :key="user.id"
        class="user-card"
        @click="goToProfile(user.id)"
      >
        <el-avatar :size="56" :src="user.avatarUrl || undefined">
          {{ user.nickname?.charAt(0) }}
        </el-avatar>
        <div class="user-info">
          <span class="user-name">{{ user.nickname }}</span>
          <span class="user-bio">{{ user.bio || '这个人很懒，什么都没写' }}</span>
        </div>
      </div>
      <el-empty v-if="!loading && users.length === 0" description="还没有关注任何人">
        <template #image>
          <el-icon :size="60" color="#c0c4cc"><User /></el-icon>
        </template>
      </el-empty>
    </div>

    <div v-if="users.length > 0 && hasMore" class="load-more">
      <el-button text :loading="loading" @click="loadMore">
        加载更多
      </el-button>
    </div>
  </div>
</template>

<script lang="ts">
import { User } from '@element-plus/icons-vue'
export default { components: { User } }
</script>

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

.user-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
  min-height: 200px;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: all 0.3s ease;
}

.user-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12);
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  display: block;
  font-weight: 600;
  font-size: 15px;
  color: #1a1a1a;
}

.user-bio {
  display: block;
  font-size: 13px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 4px;
}

.load-more {
  display: flex;
  justify-content: center;
  padding: 20px 0 10px;
}
</style>

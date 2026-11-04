<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getUserDrafts, deletePost } from '@/api/post'
import { useUserStore } from '@/stores/user'
import type { PostVO, PageData } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Delete } from '@element-plus/icons-vue'
import { formatRelativeTime } from '@/utils/date'

const router = useRouter()
const userStore = useUserStore()

const drafts = ref<PostVO[]>([])
const loading = ref(true)
const currentPage = ref(0)
const totalPages = ref(0)

async function loadDrafts(page = 0) {
  loading.value = true
  try {
    const res = await getUserDrafts({ page, size: 12 })
    const data: PageData<PostVO> = res.data.data
    if (page === 0) {
      drafts.value = data.content
    } else {
      drafts.value.push(...data.content)
    }
    currentPage.value = data.number
    totalPages.value = data.totalPages
  } catch {
    ElMessage.error('加载草稿失败')
  } finally {
    loading.value = false
  }
}

function editDraft(draft: PostVO) {
  router.push(`/post/${draft.id}/edit`)
}

async function deleteDraft(draft: PostVO) {
  await ElMessageBox.confirm('确定删除这篇草稿吗？', '提示', { type: 'warning' })
  try {
    await deletePost(draft.id)
    localStorage.removeItem(`draft_autosave_draft_${draft.id}`)
    localStorage.removeItem(`draft_autosave_${draft.id}`)
    drafts.value = drafts.value.filter(d => d.id !== draft.id)
    ElMessage.success('草稿已删除')
  } catch {}
}

onMounted(() => {
  loadDrafts()
})
</script>

<template>
  <div class="drafts-page">
    <h2 class="page-title">草稿箱</h2>

    <div v-loading="loading" class="drafts-content">
      <div v-if="drafts.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无草稿">
          <el-button type="primary" @click="router.push('/create')">写帖子</el-button>
        </el-empty>
      </div>

      <div v-else class="drafts-grid">
        <div v-for="draft in drafts" :key="draft.id" class="draft-card">
          <div class="draft-cover" v-if="draft.coverImage">
            <img :src="draft.coverImage" :alt="draft.title" />
          </div>
          <div class="draft-body">
            <h3 class="draft-title">{{ draft.title || '无标题' }}</h3>
            <p class="draft-excerpt">{{ draft.content ? draft.content.slice(0, 100) : '无内容' }}</p>
            <div class="draft-meta">
              <span class="draft-time">{{ formatRelativeTime(draft.createdAt) }}</span>
              <el-tag v-if="draft.topic" size="small" type="info">{{ draft.topic }}</el-tag>
            </div>
          </div>
          <div class="draft-actions">
            <el-button type="primary" size="small" @click="editDraft(draft)">
              <el-icon><Edit /></el-icon>
              继续编辑
            </el-button>
            <el-button type="danger" size="small" plain @click="deleteDraft(draft)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </div>
        </div>
      </div>

      <div v-if="currentPage + 1 < totalPages" class="load-more">
        <el-button @click="loadDrafts(currentPage + 1)">加载更多</el-button>
      </div>
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

.drafts-page {
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

.drafts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
  min-height: 200px;
}

.draft-card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  transition: all 0.3s ease;
}

.draft-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12);
}

.draft-cover {
  width: 100%;
  height: 160px;
  overflow: hidden;
}

.draft-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.draft-body {
  padding: 14px 16px;
}

.draft-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.draft-excerpt {
  margin: 0 0 8px;
  font-size: 13px;
  color: #666;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.draft-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.draft-time {
  font-size: 12px;
  color: #999;
}

.draft-actions {
  display: flex;
  gap: 8px;
  padding: 0 16px 14px;
}

.draft-actions :deep(.el-button) {
  border-radius: 10px;
  flex: 1;
}

.load-more {
  text-align: center;
  margin-top: 24px;
}

.load-more :deep(.el-button) {
  border-radius: 20px;
}
</style>

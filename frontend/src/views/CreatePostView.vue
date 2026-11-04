<script setup lang="ts">
import { reactive, ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createPost, updatePost, getPostDetail, saveDraft, updateDraft, getTopics, type TopicVO } from '@/api/post'
import { uploadFile, deleteFile } from '@/api/file'
import { ElMessage, type FormInstance, type FormRules, type UploadFile } from 'element-plus'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const fileList = ref<UploadFile[]>([])

const isEditMode = computed(() => route.name === 'edit-post')
const isDraftMode = ref(false)
const draftId = ref<number | null>(null)
const postId = computed(() => Number(route.params.id) || null)
const newlyUploadedUrls = new Set<string>()
const published = ref(false)

const pageMode = computed(() => {
  if (isEditMode.value) return '编辑帖子'
  return '发布帖子'
})

const form = reactive({
  title: '',
  content: '',
  topic: '',
  imageUrls: [] as string[],
})

const topics = ref<string[]>([])

async function loadTopics() {
  try {
    const res = await getTopics()
    topics.value = res.data.data.map(t => t.name)
  } catch {
    topics.value = ['美食', '旅行', '穿搭', '健身', '学习', '生活', '科技']
  }
}

const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }],
  topic: [{ required: true, message: '请选择话题', trigger: 'change' }],
}

function stripHtml(html: string): string {
  return html.replace(/<[^>]*>/g, '')
}

function onInputTitle() {
  form.title = stripHtml(form.title)
}

function onInputContent() {
  form.content = stripHtml(form.content)
}

function beforeUpload(file: File) {
  const isImage = file.type.startsWith('image/')
  const isLt5M = file.size / 1024 / 1024 < 5
  if (!isImage) {
    ElMessage.error('只能上传图片文件')
    return false
  }
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过5MB')
    return false
  }
  return true
}

async function handleUpload(options: { file: File; onSuccess: (url: string) => void; onError: () => void }) {
  try {
    const res = await uploadFile(options.file)
    const url = res.data.data
    form.imageUrls.push(url)
    newlyUploadedUrls.add(url)
    options.onSuccess(url)
  } catch {
    ElMessage.error('上传失败')
    options.onError()
  }
}

function handleRemove(file: UploadFile) {
  const url = file.response || file.url
  form.imageUrls = form.imageUrls.filter((u) => u !== url)
  if (url && typeof url === 'string') {
    deleteFile(url).catch(() => {})
    newlyUploadedUrls.delete(url)
  }
}

async function loadPostForEdit() {
  if (!postId.value) return
  try {
    const res = await getPostDetail(postId.value)
    const post = res.data.data
    form.title = post.title
    form.content = post.content
    form.topic = post.topic || ''
    form.imageUrls = post.images || []
    isDraftMode.value = post.status === 'DRAFT'
    draftId.value = post.status === 'DRAFT' ? post.id : null

    fileList.value = form.imageUrls.map((url, index) => ({
      name: `image-${index}`,
      url: url,
      status: 'success' as const,
    }))
  } catch {
    ElMessage.error('加载帖子失败')
    router.push('/')
  }
}

async function handleSubmit() {
  await formRef.value?.validate()
  loading.value = true
  try {
    if (isEditMode.value && !isDraftMode.value) {
      const res = await updatePost(postId.value!, form)
      clearAutoSave()
      published.value = true
      ElMessage.success('更新成功')
      router.push(`/post/${res.data.data.id}`)
    } else if (isDraftMode.value && draftId.value) {
      const res = await updatePost(draftId.value, form)
      clearAutoSave()
      published.value = true
      ElMessage.success('发布成功')
      router.push(`/post/${res.data.data.id}`)
    } else {
      const res = await createPost(form)
      clearAutoSave()
      published.value = true
      ElMessage.success('发布成功')
      router.push(`/post/${res.data.data.id}`)
    }
  } catch {
  } finally {
    loading.value = false
  }
}

async function handleSaveDraft() {
  loading.value = true
  try {
    if (isDraftMode.value && draftId.value) {
      const res = await updateDraft(draftId.value, {
        title: form.title || '',
        content: form.content || '',
        topic: form.topic,
        imageUrls: form.imageUrls,
      })
      published.value = true
      ElMessage.success('草稿已更新')
    } else {
      localStorage.removeItem('draft_autosave_new')
      const res = await saveDraft({
        title: form.title || '',
        content: form.content || '',
        topic: form.topic,
        imageUrls: form.imageUrls,
      })
      draftId.value = res.data.data.id
      isDraftMode.value = true
      published.value = true
      ElMessage.success('草稿已保存')
    }
  } catch {
    ElMessage.error('保存草稿失败')
  } finally {
    loading.value = false
  }
}

let autoSaveTimer: ReturnType<typeof setTimeout> | null = null

function getAutoSaveKey() {
  if (isEditMode.value && postId.value) return `draft_autosave_${postId.value}`
  if (isDraftMode.value && draftId.value) return `draft_autosave_draft_${draftId.value}`
  return 'draft_autosave_new'
}

function startAutoSave() {
  stopAutoSave()
  autoSaveTimer = setInterval(() => {
    if (form.title || form.content || form.imageUrls.length > 0) {
      const key = getAutoSaveKey()
      localStorage.setItem(key, JSON.stringify({
        title: form.title,
        content: form.content,
        topic: form.topic,
        imageUrls: form.imageUrls,
      }))
    }
  }, 30000)
}

function stopAutoSave() {
  if (autoSaveTimer) {
    clearInterval(autoSaveTimer)
    autoSaveTimer = null
  }
}

function loadAutoSave() {
  const key = getAutoSaveKey()
  const saved = localStorage.getItem(key)
  if (saved) {
    try {
      const data = JSON.parse(saved)
      if (!isEditMode.value) {
        if (data.title) form.title = data.title
        if (data.content) form.content = data.content
        if (data.topic) form.topic = data.topic
        if (data.imageUrls?.length) {
          form.imageUrls = data.imageUrls
          fileList.value = data.imageUrls.map((url: string, index: number) => ({
            name: `image-${index}`,
            url,
            status: 'success' as const,
          }))
        }
        ElMessage.info('已恢复上次编辑内容')
      }
    } catch {}
  }
}

function clearAutoSave() {
  const key = getAutoSaveKey()
  localStorage.removeItem(key)
}

watch(() => route.params.id, () => {
  if (isEditMode.value && postId.value) {
    loadPostForEdit()
  }
})

onMounted(() => {
  loadTopics()
  if (isEditMode.value && postId.value) {
    loadPostForEdit()
  } else {
    loadAutoSave()
  }
  startAutoSave()
})

onUnmounted(() => {
  stopAutoSave()
  if (!published.value && newlyUploadedUrls.size > 0) {
    for (const url of newlyUploadedUrls) {
      deleteFile(url).catch(() => {})
    }
  }
})
</script>

<template>
  <div class="create-page">
    <div class="page-header">
      <el-button text @click="router.back()">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
    </div>
    <div class="create-card">
      <h2>{{ pageMode }}</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="给你的帖子起个标题" maxlength="50" show-word-limit @input="onInputTitle" />
        </el-form-item>

        <el-form-item label="话题" prop="topic">
          <el-select v-model="form.topic" placeholder="请选择话题">
            <el-option v-for="t in topics" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>

        <el-form-item label="图片">
          <el-upload
            v-model:file-list="fileList"
            list-type="picture-card"
            :before-upload="beforeUpload"
            :http-request="(options: any) => handleUpload(options)"
            :on-remove="handleRemove"
            accept="image/*"
            :limit="9"
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
          <div class="upload-tip">最多上传9张图片，每张不超过5MB</div>
        </el-form-item>

        <el-form-item label="内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            placeholder="分享你的生活..."
            maxlength="1000"
            show-word-limit
            @input="onInputContent"
          />
        </el-form-item>

        <div class="submit-row">
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            @click="handleSubmit"
          >
            {{ isEditMode && !isDraftMode ? '更新' : '发布' }}
          </el-button>
          <el-button
            size="large"
            :loading="loading"
            @click="handleSaveDraft"
          >
            保存草稿
          </el-button>
        </div>
      </el-form>
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

.create-page {
  max-width: 700px;
  margin: 0 auto;
  animation: fadeInUp 0.4s ease-out;
}

.page-header {
  margin-bottom: 16px;
}

.page-header .el-button {
  font-size: 15px;
  color: #666;
  padding: 8px 12px;
  border-radius: 10px;
  transition: all 0.2s;
}

.page-header .el-button:hover {
  color: #333;
  background: #f5f5f5;
}

.create-card {
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
  padding: 32px;
}

.create-card h2 {
  font-size: 24px;
  font-weight: 700;
  color: #1a1a1a;
  margin-bottom: 24px;
}

.create-card :deep(.el-select) {
  width: 100%;
}

.upload-tip {
  font-size: 12px;
  color: #bbb;
}

.submit-row {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.submit-row .el-button {
  height: 44px;
  border-radius: 12px;
  font-size: 15px;
}

.submit-row .el-button--primary {
  min-width: 120px;
}
</style>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getSystemConfigs, updateSystemConfigs, getCacheStatus, clearCache, type SystemConfigAll, type CacheStatusVO } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const activeTab = ref('settings')

const configForm = ref<SystemConfigAll>({
  siteName: '',
  siteDescription: '',
  announcement: '',
  maxUploadSize: 10,
  maxPostImages: 9,
  enableRegister: true,
  enableComment: true,
  enablePost: true,
  contactEmail: '',
  contactWechat: ''
})
const configLoading = ref(false)
const configSaving = ref(false)

const cacheStatus = ref<CacheStatusVO | null>(null)
const cacheLoading = ref(false)
const clearPattern = ref('')

async function loadConfigs() {
  configLoading.value = true
  try {
    const res = await getSystemConfigs()
    configForm.value = res.data.data
  } catch {
    ElMessage.error('加载配置失败')
  } finally {
    configLoading.value = false
  }
}

async function saveConfigs() {
  configSaving.value = true
  try {
    const configs: Record<string, string> = {
      siteName: configForm.value.siteName,
      siteDescription: configForm.value.siteDescription,
      announcement: configForm.value.announcement,
      maxUploadSize: String(configForm.value.maxUploadSize),
      maxPostImages: String(configForm.value.maxPostImages),
      enableRegister: String(configForm.value.enableRegister),
      enableComment: String(configForm.value.enableComment),
      enablePost: String(configForm.value.enablePost),
      contactEmail: configForm.value.contactEmail,
      contactWechat: configForm.value.contactWechat
    }
    await updateSystemConfigs(configs)
    ElMessage.success('保存成功')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    configSaving.value = false
  }
}

async function loadCacheStatus() {
  cacheLoading.value = true
  try {
    const res = await getCacheStatus()
    cacheStatus.value = res.data.data
  } catch {
    ElMessage.error('获取缓存状态失败')
  } finally {
    cacheLoading.value = false
  }
}

async function handleClearCache() {
  try {
    await ElMessageBox.confirm(
      clearPattern.value ? `确定要清除匹配 "${clearPattern.value}" 的缓存吗？` : '确定要清除所有缓存吗？',
      '确认清除'
    )
    await clearCache(clearPattern.value || undefined)
    ElMessage.success('缓存已清除')
    loadCacheStatus()
  } catch {}
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(2) + ' MB'
  return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

function formatUptime(seconds: number) {
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const mins = Math.floor((seconds % 3600) / 60)
  let result = ''
  if (days > 0) result += days + '天 '
  if (hours > 0) result += hours + '小时 '
  result += mins + '分钟'
  return result
}

onMounted(() => {
  loadConfigs()
  loadCacheStatus()
})
</script>

<template>
  <div class="settings-view">
    <div class="page-header">
      <h2>系统设置</h2>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="基本设置" name="settings">
        <el-form :model="configForm" label-width="120px" v-loading="configLoading" style="max-width: 600px">
          <el-form-item label="站点名称">
            <el-input v-model="configForm.siteName" placeholder="请输入站点名称" />
          </el-form-item>
          <el-form-item label="站点描述">
            <el-input v-model="configForm.siteDescription" type="textarea" :rows="2" placeholder="请输入站点描述" />
          </el-form-item>
          <el-form-item label="公告内容">
            <el-input v-model="configForm.announcement" type="textarea" :rows="3" placeholder="请输入公告内容" />
          </el-form-item>
          <el-divider>功能开关</el-divider>
          <el-form-item label="开放注册">
            <el-switch v-model="configForm.enableRegister" />
          </el-form-item>
          <el-form-item label="允许发帖">
            <el-switch v-model="configForm.enablePost" />
          </el-form-item>
          <el-form-item label="允许评论">
            <el-switch v-model="configForm.enableComment" />
          </el-form-item>
          <el-divider>上传设置</el-divider>
          <el-form-item label="最大上传大小">
            <el-input-number v-model="configForm.maxUploadSize" :min="1" :max="100" />
            <span style="margin-left: 8px; color: #909399">MB</span>
          </el-form-item>
          <el-form-item label="帖子最大图片数">
            <el-input-number v-model="configForm.maxPostImages" :min="1" :max="20" />
          </el-form-item>
          <el-divider>联系方式</el-divider>
          <el-form-item label="联系邮箱">
            <el-input v-model="configForm.contactEmail" placeholder="请输入联系邮箱" />
          </el-form-item>
          <el-form-item label="联系微信">
            <el-input v-model="configForm.contactWechat" placeholder="请输入微信号" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="saveConfigs" :loading="configSaving">保存设置</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="缓存管理" name="cache">
        <div v-loading="cacheLoading">
          <el-card shadow="never" style="margin-bottom: 20px">
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center">
                <span>Redis 状态</span>
                <el-tag :type="cacheStatus?.connected ? 'success' : 'danger'" size="small">
                  {{ cacheStatus?.connected ? '已连接' : '未连接' }}
                </el-tag>
              </div>
            </template>
            <el-row :gutter="20" v-if="cacheStatus">
              <el-col :span="6">
                <el-statistic title="内存使用" :value="formatBytes(cacheStatus.usedMemory)" />
              </el-col>
              <el-col :span="6">
                <el-statistic title="Key 总数" :value="cacheStatus.totalKeys" />
              </el-col>
              <el-col :span="6">
                <el-statistic title="缓存命中" :value="cacheStatus.hits" />
              </el-col>
              <el-col :span="6">
                <el-statistic title="命中率" :value="cacheStatus.hitRate.toFixed(2)" suffix="%" />
              </el-col>
            </el-row>
            <el-row :gutter="20" style="margin-top: 20px" v-if="cacheStatus">
              <el-col :span="6">
                <el-statistic title="运行时间" :value="formatUptime(cacheStatus.uptime)" />
              </el-col>
              <el-col :span="6">
                <el-statistic title="连接客户端" :value="cacheStatus.connectedClients" />
              </el-col>
              <el-col :span="6">
                <el-statistic title="未命中" :value="cacheStatus.misses" />
              </el-col>
            </el-row>
          </el-card>

          <el-card shadow="never">
            <template #header>缓存操作</template>
            <div style="display: flex; gap: 12px; align-items: center">
              <el-input v-model="clearPattern" placeholder="缓存Key模式（如 shenghuotie:*）" style="width: 300px" />
              <el-button type="danger" @click="handleClearCache">清除缓存</el-button>
              <el-button @click="loadCacheStatus">刷新状态</el-button>
            </div>
            <div style="margin-top: 12px; color: #909399; font-size: 13px">
              提示：留空则清除所有缓存。常用模式：shenghuotie:cache:* 清除业务缓存
            </div>
          </el-card>

          <el-card shadow="never" style="margin-top: 20px" v-if="cacheStatus?.keys?.length">
            <template #header>缓存 Key 列表（前100个）</template>
            <el-table :data="cacheStatus.keys" size="small" max-height="400">
              <el-table-column prop="key" label="Key" show-overflow-tooltip />
              <el-table-column prop="type" label="类型" width="80" />
              <el-table-column label="TTL" width="100">
                <template #default="{ row }">
                  {{ row.ttl > 0 ? row.ttl + 's' : (row.ttl === -1 ? '永久' : '已过期') }}
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.settings-view {
  padding: 0 16px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}
</style>

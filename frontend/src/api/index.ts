import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, removeToken } from '@/utils/auth'
import { checkWriteOp } from '@/utils/speedDetector'
import type { Result } from '@/types'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// 防止同一错误短时间内重复弹提示
let lastErrorMsg = ''
let lastErrorTime = 0
function showError(msg: string) {
  const now = Date.now()
  if (msg === lastErrorMsg && now - lastErrorTime < 3000) return
  lastErrorMsg = msg
  lastErrorTime = now
  ElMessage.error(msg)
}

// 请求拦截器：自动携带 token + 写操作频率检测
request.interceptors.request.use(async (config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // 写操作（POST/PUT/DELETE）检查频率，超限时等待人机验证
  if (config.method && ['post', 'put', 'delete'].includes(config.method)) {
    await checkWriteOp()
  }
  return config
})

// 响应拦截器：统一错误处理
request.interceptors.response.use(
  (response) => {
    const res: Result<unknown> = response.data
    if (res.code !== 200) {
      showError(res.message || '请求失败')
      if (res.code === 401) {
        removeToken()
        window.location.href = '/login'
      }
      return Promise.reject(new Error(res.message))
    }
    return response
  },
  (error) => {
    if (error.response?.status === 401) {
      removeToken()
      window.location.href = '/login'
    } else {
      // 优先使用后端返回的中文消息，否则将 Axios 英文错误翻译为中文
      const msg = error.response?.data?.message || translateAxiosError(error)
      showError(msg)
    }
    return Promise.reject(error)
  }
)

/** 将 Axios 原生英文错误翻译为中文 */
function translateAxiosError(error: unknown): string {
  if (!(error instanceof Error)) return '网络异常'
  const msg = error.message || ''
  if (msg.includes('timeout')) return '请求超时，请检查网络后重试'
  if (msg.includes('Network Error')) return '网络连接失败，请检查网络'
  if (msg.includes('429')) return '操作过于频繁，请稍后再试'
  if (msg.includes('403')) return '没有权限执行此操作'
  if (msg.includes('404')) return '请求的资源不存在'
  if (msg.includes('500')) return '服务器错误，请稍后重试'
  return '网络异常，请稍后重试'
}

export default request

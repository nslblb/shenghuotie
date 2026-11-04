import { ElMessage, ElMessageBox } from 'element-plus'

export interface AppError extends Error {
  response?: {
    status: number
    data?: {
      message?: string
    }
  }
}

export function handleError(error: AppError, context: string) {
  console.error(`[${context}]`, error)
  
  if (error.response) {
    const { status, data } = error.response
    const message = data?.message
    
    switch (status) {
      case 400:
        ElMessage.error(message || '请求参数错误')
        break
      case 401:
        ElMessage.warning('登录已过期，请重新登录')
        break
      case 403:
        ElMessage.error('没有权限执行此操作')
        break
      case 404:
        ElMessage.error(message || '请求的资源不存在')
        break
      case 429:
        ElMessage.warning('操作过于频繁，请稍后再试')
        break
      case 500:
        ElMessage.error('服务器错误，请稍后重试')
        break
      default:
        ElMessage.error(message || '系统异常，请稍后重试')
    }
  } else if (error.message.includes('timeout')) {
    ElMessage.error('请求超时，请检查网络后重试')
  } else if (error.message.includes('Network Error')) {
    ElMessage.error('网络连接失败，请检查网络后重试')
  } else {
    ElMessage.error(error.message || '系统异常，请稍后重试')
  }
}

export async function handleRetryableError(
  error: AppError,
  retryFn: () => Promise<void>,
  context: string
): Promise<boolean> {
  handleError(error, context)
  
  try {
    await ElMessageBox.confirm(
      '是否重试？',
      '提示',
      {
        confirmButtonText: '重试',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    await retryFn()
    return true
  } catch {
    return false
  }
}

export default handleError

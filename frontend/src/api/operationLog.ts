import request from './index'
import type { Result, PageData } from '@/types'

export interface OperationLogVO {
  id: number
  userId: number
  username: string
  operation: string
  method: string
  params: string | null
  url: string
  ip: string
  duration: number
  status: string
  errorMsg: string | null
  createdAt: string
}

export function getOperationLogs(params: { page?: number; size?: number; keyword?: string }) {
  return request.get<Result<PageData<OperationLogVO>>>('/admin/logs', { params })
}

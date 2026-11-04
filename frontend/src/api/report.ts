import request from './index'
import type { Result } from '@/types'

export function createReport(data: { targetId: number; targetType: string; reason: string }) {
  return request.post<Result<void>>('/reports', data)
}


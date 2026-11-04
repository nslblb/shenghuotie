import request from './index'
import type { Result } from '@/types'

export function uploadFile(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<Result<string>>('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function deleteFile(url: string) {
  return request.delete<Result<void>>('/files/delete', {
    params: { url },
  })
}

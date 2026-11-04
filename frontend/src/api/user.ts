import request from './index'
import type { Result, LoginVO, UserVO, UserProfileVO, PageData } from '@/types'

export function register(data: { phone: string; password: string; nickname: string }) {
  return request.post<Result<LoginVO>>('/auth/register', data)
}

export function login(data: { phone: string; password: string }) {
  return request.post<Result<LoginVO>>('/auth/login', data)
}

export function logout() {
  return request.post<Result<void>>('/auth/logout')
}

export function getUserProfile(userId: number) {
  return request.get<Result<UserProfileVO>>(`/users/${userId}`)
}

export function updateUser(userId: number, data: { nickname?: string; avatarUrl?: string; bio?: string; messagePrivacy?: string }) {
  return request.put<Result<UserVO>>(`/users/${userId}`, data)
}

export function changePassword(userId: number, oldPassword: string, newPassword: string) {
  return request.put<Result<void>>(`/users/${userId}/password`, { oldPassword, newPassword })
}

export function uploadFile(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<Result<string>>('/files/upload', formData)
}

export function follow(userId: number) {
  return request.post<Result<void>>(`/users/${userId}/follow`)
}

export function unfollow(userId: number) {
  return request.delete<Result<void>>(`/users/${userId}/follow`)
}

export function getFollowers(userId: number, page = 0, size = 10) {
  return request.get<Result<PageData<UserVO>>>(`/users/${userId}/followers`, {
    params: { page, size }
  })
}

export function getFollowings(userId: number, page = 0, size = 10) {
  return request.get<Result<PageData<UserVO>>>(`/users/${userId}/followings`, {
    params: { page, size }
  })
}

export function sendResetCode(phone: string) {
  return request.post<Result<void>>('/users/reset-password/code', null, {
    params: { phone }
  })
}

export function resetPassword(phone: string, code: string, newPassword: string) {
  return request.post<Result<void>>('/users/reset-password', { phone, code, newPassword })
}

export function deleteAccount(data: { code: string }) {
  return request.delete<Result<void>>('/users/account', { data })
}

export function sendDeleteCode() {
  return request.post<Result<void>>('/users/delete-account/send-code')
}

export interface PublicConfigs {
  siteName: string
  siteDescription: string
  announcement: string
  maxUploadSize: number
  maxPostImages: number
  enableRegister: boolean
  enableComment: boolean
  enablePost: boolean
  contactEmail: string
  contactWechat: string
}

export function getPublicConfigs() {
  return request.get<Result<PublicConfigs>>('/auth/public-configs')
}

export function searchUsers(keyword: string, page: number = 0, size: number = 10) {
  return request.get<Result<{ content: UserVO[]; totalElements: number }>>('/users/search', {
    params: { keyword, page, size }
  })
}

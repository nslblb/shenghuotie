import request from './index'
import type { Result, UserVO, PageData, DashboardVO } from '@/types'

export interface TrendDataVO {
  userTrends: DateCountVO[]
  postTrends: DateCountVO[]
}

export interface DateCountVO {
  date: string
  count: number
}

export interface HotTopicVO {
  topic: string
  count: number
}

export interface DashboardStatsVO {
  todayComments: number
  todayLikes: number
  pendingReports: number
  pendingAppeals: number
  onlineUsers: number
}

export function getDashboard() {
  return request.get<Result<DashboardVO>>('/admin/dashboard')
}

export function getTrends(days: number = 7) {
  return request.get<Result<TrendDataVO>>('/admin/dashboard/trends', { params: { days } })
}

export function getHotTopics() {
  return request.get<Result<HotTopicVO[]>>('/admin/dashboard/hot-topics')
}

export function getDashboardStats() {
  return request.get<Result<DashboardStatsVO>>('/admin/dashboard/stats')
}

export function getAdminUsers(params: {
  page?: number
  size?: number
  keyword?: string
  role?: string
  status?: string
  startTime?: string
  endTime?: string
}) {
  return request.get<Result<PageData<UserVO>>>('/admin/users', { params })
}

export interface UserDetailVO {
  id: number
  phone: string
  nickname: string
  avatarUrl: string
  bio: string
  role: string
  status: string
  banReason: string
  bannedAt: string
  createdAt: string
  updatedAt: string
  postCount: number
  commentCount: number
  followerCount: number
  followingCount: number
  reportCount: number
}

export function getAdminUserDetail(id: number) {
  return request.get<Result<UserDetailVO>>(`/admin/users/${id}`)
}

export function updateUserStatus(userId: number, status: string, banReason?: string) {
  return request.put<Result<void>>(`/admin/users/${userId}/status`, null, {
    params: { status, banReason }
  })
}

export function resetUserPassword(userId: number) {
  return request.put<Result<void>>(`/admin/users/${userId}/reset-password`)
}

export function updateUserRole(userId: number, role: string) {
  return request.put<Result<void>>(`/admin/users/${userId}/role`, null, { params: { role } })
}

export function batchUpdateUserStatus(ids: number[], status: string, banReason?: string) {
  return request.put<Result<void>>('/admin/users/batch/status', { ids, status, banReason })
}

export function getAdminPosts(params: {
  page?: number
  size?: number
  keyword?: string
  status?: string
  topic?: string
}) {
  return request.get<Result<PageData<any>>>('/admin/posts', { params })
}

export function getAdminPostTopics() {
  return request.get<Result<string[]>>('/admin/posts/topics')
}

export function getAdminPostDetail(id: number) {
  return request.get<Result<any>>(`/admin/posts/${id}`)
}

export function adminDeletePost(postId: number) {
  return request.delete<Result<void>>(`/admin/posts/${postId}`)
}

export function adminBatchDeletePosts(ids: number[]) {
  return request.delete<Result<void>>('/admin/posts/batch', { data: ids })
}

export function adminBatchDisablePosts(ids: number[]) {
  return request.put<Result<void>>('/admin/posts/batch/disable', ids)
}

export function adminBatchEnablePosts(ids: number[]) {
  return request.put<Result<void>>('/admin/posts/batch/enable', ids)
}

export function adminDisablePost(postId: number) {
  return request.put<Result<void>>(`/admin/posts/${postId}/disable`)
}

export function adminEnablePost(postId: number) {
  return request.put<Result<void>>(`/admin/posts/${postId}/enable`)
}

export function getAppeals(params: { page?: number; size?: number; status?: string }) {
  return request.get<Result<PageData<AppealVO>>>('/admin/appeals', { params })
}

export function getAppealDetail(id: number) {
  return request.get<Result<AppealVO>>(`/admin/appeals/${id}`)
}

export function approveAppeal(id: number, handleNote?: string) {
  return request.put<Result<void>>(`/admin/appeals/${id}/approve`, null, {
    params: { handleNote }
  })
}

export function rejectAppeal(id: number, handleNote?: string) {
  return request.put<Result<void>>(`/admin/appeals/${id}/reject`, null, {
    params: { handleNote }
  })
}

export function deleteAppeal(id: number) {
  return request.delete<Result<void>>(`/admin/appeals/${id}`)
}

export function getAppealHistory(postId: number, params: { page?: number; size?: number }) {
  return request.get<Result<PageData<AppealVO>>>(`/admin/appeals/post/${postId}/history`, { params })
}

export interface AppealVO {
  id: number
  postId: number
  userId: number
  userName: string
  userAvatar: string | null
  postTitle: string
  postContent: string
  postCoverImage: string | null
  reason: string
  banReason: string | null
  status: string
  handlerId: number | null
  handlerName: string | null
  handleNote: string | null
  handledAt: string | null
  createdAt: string
  history: AppealHistoryItem[]
}

export interface AppealHistoryItem {
  id: number
  reason: string
  status: string
  handleNote: string | null
  handlerName: string | null
  createdAt: string
  handledAt: string | null
}

export interface CommentAdminVO {
  id: number
  postId: number
  postTitle: string
  userId: number
  userNickname: string
  userAvatar: string
  parentId: number
  parentContent: string
  content: string
  hasSensitiveWord: boolean
  createdAt: string
}

export function getAdminComments(params: {
  page?: number
  size?: number
  keyword?: string
  userId?: number
  postId?: number
  hasSensitiveWord?: boolean
}) {
  return request.get<Result<PageData<CommentAdminVO>>>('/admin/comments', { params })
}

export function getAdminCommentDetail(id: number) {
  return request.get<Result<CommentAdminVO>>(`/admin/comments/${id}`)
}

export function adminDeleteComment(id: number) {
  return request.delete<Result<void>>(`/admin/comments/${id}`)
}

export interface ReportDetailVO {
  id: number
  reporterId: number
  reporterName: string
  reporterAvatar: string | null
  targetId: number
  targetType: string
  reason: string
  status: string
  handlerId: number | null
  handlerName: string | null
  handleResult: string | null
  createdAt: string
  handledAt: string | null
  targetTitle: string | null
  targetContent: string | null
  targetAuthorName: string | null
  targetAuthorId: number | null
  targetStatus: string | null
  targetCoverImage: string | null
  reportCount: number
  reportHistory: ReportHistoryItem[]
}

export interface ReportHistoryItem {
  id: number
  reporterId: number
  reporterName: string
  reason: string
  createdAt: string
}

export interface ReportGroupVO {
  targetId: number
  targetType: string
  targetTitle: string | null
  targetContent: string | null
  targetAuthorName: string | null
  targetStatus: string | null
  reportCount: number
  latestReportTime: string
  earliestReportTime: string
  reasons: string[]
  reports: GroupReportItem[]
}

export interface GroupReportItem {
  id: number
  reporterId: number
  reporterName: string
  reporterAvatar: string | null
  reason: string
  status: string
  createdAt: string
}

export interface ReportReasonStatsVO {
  reason: string
  count: number
  percentage: number
}

export interface ReportStatsResponse {
  totalReports: number
  pendingReports: number
  resolvedReports: number
  rejectedReports: number
  reasonStats: ReportReasonStatsVO[]
}

export interface AutoHandleRuleVO {
  id: number
  targetType: string
  reportThreshold: number
  action: string
  timeWindowHours: number | null
  enabled: boolean
}

export function getAdminReports(params: {
  page?: number
  size?: number
  status?: string
}) {
  return request.get<Result<PageData<ReportDetailVO>>>('/admin/reports', { params })
}

export function getAdminReportDetail(id: number) {
  return request.get<Result<ReportDetailVO>>(`/admin/reports/${id}`)
}

export function adminHandleReport(id: number, status: string, handleResult?: string, action?: string) {
  return request.put<Result<void>>(`/admin/reports/${id}/handle`, null, {
    params: { status, handleResult, action }
  })
}

export function adminDeleteReport(id: number) {
  return request.delete<Result<void>>(`/admin/reports/${id}`)
}

export function getAdminGroupedReports(params: {
  page?: number
  size?: number
  status?: string
  targetType?: string
}) {
  return request.get<Result<PageData<ReportGroupVO>>>('/admin/reports/grouped', { params })
}

export function getAdminReportStats() {
  return request.get<Result<ReportStatsResponse>>('/admin/reports/stats')
}

export function getAdminAutoHandleRules() {
  return request.get<Result<AutoHandleRuleVO[]>>('/admin/reports/rules')
}

export function createAdminAutoHandleRule(data: {
  targetType: string
  reportThreshold: number
  action: string
  timeWindowHours?: number
  enabled?: boolean
}) {
  return request.post<Result<AutoHandleRuleVO>>('/admin/reports/rules', data)
}

export function updateAdminAutoHandleRule(id: number, data: {
  targetType: string
  reportThreshold: number
  action: string
  timeWindowHours?: number
  enabled?: boolean
}) {
  return request.put<Result<AutoHandleRuleVO>>(`/admin/reports/rules/${id}`, data)
}

export function deleteAdminAutoHandleRule(id: number) {
  return request.delete<Result<void>>(`/admin/reports/rules/${id}`)
}

export function executeAdminAutoHandleRules() {
  return request.post<Result<void>>('/admin/reports/rules/execute')
}

export interface AdminLoginLogVO {
  id: number
  userId: number
  username: string
  ip: string
  location: string | null
  userAgent: string | null
  browser: string | null
  os: string | null
  status: string
  failReason: string | null
  createdAt: string
}

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

export interface OperationLogStats {
  total: number
  successCount: number
  failCount: number
  avgDuration: number
  topOperations: { operation: string; count: number }[]
}

export interface SystemConfigAll {
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

export interface CacheStatusVO {
  connected: boolean
  usedMemory: number
  totalKeys: number
  hits: number
  misses: number
  hitRate: number
  uptime: number
  connectedClients: number
  keys: { key: string; type: string; ttl: number; size: number }[]
}

export function getAdminLoginLogs(params: {
  page?: number
  size?: number
  userId?: number
  status?: string
  startTime?: string
  endTime?: string
}) {
  return request.get<Result<PageData<AdminLoginLogVO>>>('/admin/logs/login', { params })
}

export function getOperationLogsEnhanced(params: {
  page?: number
  size?: number
  userId?: number
  operation?: string
  status?: string
  startTime?: string
  endTime?: string
}) {
  return request.get<Result<PageData<OperationLogVO>>>('/admin/logs/operations', { params })
}

export function getOperationLogStats() {
  return request.get<Result<OperationLogStats>>('/admin/logs/operations/stats')
}

export function getSystemConfigs() {
  return request.get<Result<SystemConfigAll>>('/admin/configs')
}

export function updateSystemConfigs(configs: Record<string, string>) {
  return request.put<Result<void>>('/admin/configs', configs)
}

export function updateSystemConfig(key: string, value: string) {
  return request.put<Result<void>>(`/admin/configs/${key}`, { value })
}

export function getCacheStatus() {
  return request.get<Result<CacheStatusVO>>('/admin/cache/status')
}

export function clearCache(pattern?: string) {
  return request.delete<Result<void>>('/admin/cache', { params: { pattern } })
}

export interface TopicVO {
  id: number
  name: string
  description: string | null
  enabled: boolean
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export function getAllTopics() {
  return request.get<Result<TopicVO[]>>('/admin/topics')
}

export function getEnabledTopics() {
  return request.get<Result<TopicVO[]>>('/admin/topics/enabled')
}

export function getTopicsPage(params: { page?: number; size?: number; enabled?: boolean }) {
  return request.get<Result<PageData<TopicVO>>>('/admin/topics/page', { params })
}

export function getTopic(id: number) {
  return request.get<Result<TopicVO>>(`/admin/topics/${id}`)
}

export function createTopic(data: { name: string; description?: string; sortOrder?: number }) {
  return request.post<Result<TopicVO>>('/admin/topics', null, { params: data })
}

export function updateTopic(id: number, data: { name?: string; description?: string; sortOrder?: number; enabled?: boolean }) {
  return request.put<Result<TopicVO>>(`/admin/topics/${id}`, null, { params: data })
}

export function deleteTopic(id: number) {
  return request.delete<Result<void>>(`/admin/topics/${id}`)
}

export interface SensitiveWordVO {
  id: number
  word: string
  category: string | null
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export function getSensitiveWords(params: {
  page?: number
  size?: number
  keyword?: string
  category?: string
  enabled?: boolean
}) {
  return request.get<Result<PageData<SensitiveWordVO>>>('/admin/sensitive-words', { params })
}

export function createSensitiveWord(data: { word: string; category?: string }) {
  return request.post<Result<SensitiveWordVO>>('/admin/sensitive-words', null, { params: data })
}

export function updateSensitiveWord(id: number, data: { word?: string; category?: string; enabled?: boolean }) {
  return request.put<Result<SensitiveWordVO>>(`/admin/sensitive-words/${id}`, null, { params: data })
}

export function deleteSensitiveWord(id: number) {
  return request.delete<Result<void>>(`/admin/sensitive-words/${id}`)
}

export function batchDeleteSensitiveWords(ids: number[]) {
  return request.delete<Result<void>>('/admin/sensitive-words/batch', { data: ids })
}

export function refreshSensitiveWordCache() {
  return request.post<Result<void>>('/admin/sensitive-words/refresh')
}

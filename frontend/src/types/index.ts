// 统一响应格式
export interface Result<T> {
  code: number
  message: string
  data: T
}

// 用户
export interface UserVO {
  id: number
  phone: string
  nickname: string
  avatarUrl: string | null
  bio: string | null
  role: string
  status: string
  messagePrivacy: string
  createdAt: string
}

export interface UserProfileVO {
  id: number
  phone: string
  nickname: string
  avatarUrl: string | null
  bio: string | null
  postCount: number
  followerCount: number
  followingCount: number
  followed: boolean
  messagePrivacy: string
  createdAt: string
}

export interface LoginVO {
  token: string
  user: UserVO
}

// 帖子
export interface PostVO {
  id: number
  userId: number
  authorName: string
  authorAvatar: string | null
  title: string
  content: string
  coverImage: string | null
  topic: string | null
  likeCount: number
  commentCount: number
  favoriteCount: number
  images: string[]
  liked: boolean
  favorited: boolean
  status: string
  createdAt: string
}

export interface PostDetailVO extends PostVO {}

// 评论
export interface CommentVO {
  id: number
  postId: number
  userId: number
  userName: string
  userAvatar: string | null
  parentId: number | null
  content: string
  replies: CommentVO[]
  createdAt: string
}

// 通知
export interface NotificationVO {
  id: number
  senderId: number
  senderName: string
  senderAvatar: string | null
  type: string
  postId: number | null
  content: string
  isRead: boolean
  createdAt: string
}

// 后台仪表盘
export interface DashboardVO {
  totalUsers: number
  totalPosts: number
  totalComments: number
  totalLikes: number
  todayNewUsers: number
  todayNewPosts: number
}

// 分页
export interface PageData<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserVO } from '@/types'
import { getToken, setToken, removeToken, checkAndClearExpiredToken } from '@/utils/auth'
import { login as loginApi, register as registerApi, updateUser as updateUserApi, logout as logoutApi } from '@/api/user'

const USER_INFO_KEY = 'user_info'

function getStoredUserInfo(): UserVO | null {
  try {
    const stored = localStorage.getItem(USER_INFO_KEY)
    return stored ? JSON.parse(stored) : null
  } catch {
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  // 初始化时检查 Token 是否过期，过期则自动清除
  checkAndClearExpiredToken()

  const token = ref(getToken() || '')
  const userInfo = ref<UserVO | null>(getStoredUserInfo())

  const isLoggedIn = computed(() => !!token.value)

  function setUserInfo(info: UserVO) {
    userInfo.value = info
    localStorage.setItem(USER_INFO_KEY, JSON.stringify(info))
  }

  async function login(phone: string, password: string) {
    const res = await loginApi({ phone, password })
    token.value = res.data.data.token
    setUserInfo(res.data.data.user)
    setToken(res.data.data.token)
  }

  async function register(phone: string, password: string, nickname: string) {
    const res = await registerApi({ phone, password, nickname })
    token.value = res.data.data.token
    setUserInfo(res.data.data.user)
    setToken(res.data.data.token)
  }

  async function updateProfile(userId: number, data: { nickname?: string; avatarUrl?: string; bio?: string }) {
    const res = await updateUserApi(userId, data)
    setUserInfo(res.data.data)
  }

  async function logout() {
    try {
      await logoutApi()
    } catch {
      // 忽略错误，仍清理本地状态
    } finally {
      token.value = ''
      userInfo.value = null
      removeToken()
      localStorage.removeItem(USER_INFO_KEY)
    }
  }

  return { token, userInfo, isLoggedIn, setUserInfo, login, register, updateProfile, logout }
})

import axios from 'axios'

const TOKEN_KEY = 'shenghuotie_token'
const TOKEN_TIME_KEY = 'shenghuotie_token_time'
const REFRESH_THRESHOLD = 3600000 // 1小时，单位毫秒

let isRefreshing = false
let refreshSubscribers: Array<(token: string) => void> = []

function onTokenRefreshed(token: string) {
  refreshSubscribers.forEach(callback => callback(token))
  refreshSubscribers = []
}

function addRefreshSubscriber(callback: (token: string) => void) {
  refreshSubscribers.push(callback)
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(TOKEN_TIME_KEY, Date.now().toString())
}

export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(TOKEN_TIME_KEY)
}

/** 解码 JWT payload，获取 exp 过期时间 */
function decodeTokenExp(token: string): number | null {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    const payload = JSON.parse(atob(parts[1]))
    return payload.exp || null
  } catch {
    return null
  }
}

/** 检查 localStorage 中的 Token 是否已过期，过期则自动清除 */
export function checkAndClearExpiredToken(): boolean {
  const token = localStorage.getItem(TOKEN_KEY)
  if (!token) return false

  const exp = decodeTokenExp(token)
  if (exp && Date.now() >= exp * 1000) {
    removeToken()
    return true
  }
  return false
}

export function isTokenExpiringSoon(): boolean {
  const tokenTime = localStorage.getItem(TOKEN_TIME_KEY)
  if (!tokenTime) return false
  const elapsed = Date.now() - parseInt(tokenTime, 10)
  return elapsed > (7 * 24 * 3600000 - REFRESH_THRESHOLD)
}

export async function refreshToken(): Promise<string | null> {
  const token = getToken()
  if (!token) return null

  try {
    const res = await axios.post('/api/auth/refresh', null, {
      headers: { Authorization: `Bearer ${token}` }
    })
    const newToken = res.data.data.token
    setToken(newToken)
    return newToken
  } catch {
    removeToken()
    return null
  }
}

export async function ensureValidToken(): Promise<string | null> {
  const token = getToken()
  if (!token) return null

  if (isTokenExpiringSoon()) {
    if (isRefreshing) {
      return new Promise(resolve => {
        addRefreshSubscriber(resolve)
      })
    }

    isRefreshing = true
    try {
      const newToken = await refreshToken()
      isRefreshing = false
      if (newToken) {
        onTokenRefreshed(newToken)
      }
      return newToken
    } catch {
      isRefreshing = false
      return null
    }
  }

  return token
}

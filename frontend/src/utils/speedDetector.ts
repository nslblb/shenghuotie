/**
 * 操作频率检测器
 * 只监控写操作（POST/PUT/DELETE）的频率
 * 正常用户浏览/阅读完全不受影响
 */

/** 时间窗口（毫秒） */
const WINDOW_MS = 3000
/** 窗口内允许的最大写操作次数 */
const MAX_WRITE_OPS = 4

let writeTimestamps: number[] = []
let blocked = false
let pendingResolve: (() => void) | null = null
let onCaptchaRequired: (() => void) | null = null

/**
 * 检查写操作频率，超过阈值则触发人机验证
 * @returns Promise - 验证通过后 resolve，失败则 reject
 */
export function checkWriteOp(): Promise<void> {
  const now = Date.now()
  writeTimestamps.push(now)
  writeTimestamps = writeTimestamps.filter(t => now - t < WINDOW_MS)

  // 未超过阈值，直接通过
  if (writeTimestamps.length <= MAX_WRITE_OPS) {
    return Promise.resolve()
  }

  // 已被拦截，排队等待
  if (blocked) {
    return new Promise((resolve) => {
      pendingResolve = resolve
    })
  }

  // 触发人机验证
  blocked = true
  writeTimestamps = []
  if (onCaptchaRequired) {
    onCaptchaRequired()
  }

  return new Promise((resolve) => {
    pendingResolve = resolve
  })
}

export function captchaPassed() {
  blocked = false
  writeTimestamps = []
  if (pendingResolve) {
    pendingResolve()
    pendingResolve = null
  }
}

export function captchaFailed() {
  // 保持 blocked，用户需要重试
}

export function isBlocked(): boolean {
  return blocked
}

export function initSpeedDetector(callback: () => void) {
  onCaptchaRequired = callback
}

export function destroySpeedDetector() {
  onCaptchaRequired = null
  pendingResolve = null
}

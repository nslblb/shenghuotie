const HISTORY_KEY = 'shenghuotie_search_history'
const MAX_HISTORY = 10
import DOMPurify from 'dompurify'

export function getSearchHistory(): string[] {
  try {
    const history = localStorage.getItem(HISTORY_KEY)
    return history ? JSON.parse(history) : []
  } catch {
    return []
  }
}

export function saveSearchHistory(keyword: string) {
  const sanitizedKeyword = DOMPurify.sanitize(keyword.trim())
  if (!sanitizedKeyword) return
  
  const history = getSearchHistory()
  const filtered = history.filter(k => k !== sanitizedKeyword)
  filtered.unshift(sanitizedKeyword)
  
  if (filtered.length > MAX_HISTORY) {
    filtered.length = MAX_HISTORY
  }
  
  localStorage.setItem(HISTORY_KEY, JSON.stringify(filtered))
}

export function clearSearchHistory() {
  localStorage.removeItem(HISTORY_KEY)
}

export function debounce<T extends (...args: any[]) => any>(fn: T, delay: number): (...args: Parameters<T>) => void {
  let timer: ReturnType<typeof setTimeout> | null = null
  
  return function (this: any, ...args: Parameters<T>) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      fn.apply(this, args)
    }, delay)
  }
}

export function highlightKeyword(text: string, keyword: string): string {
  if (!text || !keyword) return text
  
  const sanitizedKeyword = DOMPurify.sanitize(keyword)
  const escapedKeyword = sanitizedKeyword
    .replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  
  const regex = new RegExp(`(${escapedKeyword})`, 'gi')
  const highlighted = text.replace(regex, '<mark class="keyword-highlight">$1</mark>')
  
  return DOMPurify.sanitize(highlighted, { 
    ALLOWED_TAGS: ['mark'],
    ALLOWED_ATTR: ['class']
  })
}

export function sanitizeSearchInput(input: string): string {
  return DOMPurify.sanitize(input.trim(), { ALLOWED_TAGS: [] })
}

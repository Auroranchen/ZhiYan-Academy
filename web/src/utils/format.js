/**
 * 统一格式化工具
 *
 * formatDate 之类的函数原先散落在 25 个以上的视图里，各自实现、行为略有出入。
 * 新代码一律从这里引入。
 */

const pad = (n) => String(n).padStart(2, '0')

/**
 * 把后端返回的时间值解析成 Date。
 * 后端统一输出 'yyyy-MM-dd HH:mm:ss'，Safari 不接受空格分隔，这里替换成 T。
 * @param {string | number | Date | null | undefined} value
 * @returns {Date | null}
 */
export const toDate = (value) => {
  if (!value) return null
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value
  if (typeof value === 'number') return new Date(value)
  const date = new Date(String(value).replace(/-/g, '/').replace('T', ' '))
  return Number.isNaN(date.getTime()) ? null : date
}

/**
 * @param {*} value
 * @param {'date' | 'datetime' | 'time' | 'minute'} [pattern]
 * @param {string} [fallback]
 */
export const formatDate = (value, pattern = 'date', fallback = '—') => {
  const d = toDate(value)
  if (!d) return fallback

  const ymd = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`

  switch (pattern) {
    case 'datetime':
      return `${ymd} ${hm}:${pad(d.getSeconds())}`
    case 'minute':
      return `${ymd} ${hm}`
    case 'time':
      return hm
    default:
      return ymd
  }
}

/**
 * 本地时区的 yyyy-MM-dd，用于按天分组。
 * 不能用 toISOString()，那会转成 UTC，东八区凌晨会落到前一天。
 */
export const dateKey = (value) => {
  const d = toDate(value)
  return d ? `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` : ''
}

/** 相对时间，用于消息与通知列表 */
export const timeAgo = (value, fallback = '—') => {
  const d = toDate(value)
  if (!d) return fallback

  const diff = Date.now() - d.getTime()
  if (diff < 0) return formatDate(d, 'minute')

  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < 7 * day) return `${Math.floor(diff / day)} 天前`
  return formatDate(d, 'date')
}

/**
 * 距离截止时间还剩多久，作业与考试列表使用。
 * @returns {{ expired: boolean, urgent: boolean, text: string }}
 */
export const countdown = (deadline) => {
  const d = toDate(deadline)
  if (!d) return { expired: false, urgent: false, text: '不限时' }

  const diff = d.getTime() - Date.now()
  if (diff <= 0) return { expired: true, urgent: false, text: '已截止' }

  const hour = 60 * 60 * 1000
  const day = 24 * hour
  const days = Math.floor(diff / day)
  const hours = Math.floor((diff % day) / hour)

  return {
    expired: false,
    urgent: diff < day,
    text: days > 0 ? `剩余 ${days} 天 ${hours} 小时` : `剩余 ${hours} 小时`
  }
}

/** 文件大小 */
export const formatFileSize = (bytes) => {
  if (bytes === null || bytes === undefined || Number.isNaN(Number(bytes))) return '—'
  const size = Number(bytes)
  if (size < 1024) return `${size} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let value = size / 1024
  let i = 0
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024
    i += 1
  }
  return `${value.toFixed(value >= 100 ? 0 : 1)} ${units[i]}`
}

/** 千分位数字 */
export const formatNumber = (value, fallback = '—') => {
  if (value === null || value === undefined || value === '') return fallback
  const n = Number(value)
  return Number.isNaN(n) ? fallback : n.toLocaleString('zh-CN')
}

/** 分数：保留一位小数并去掉多余的 .0 */
export const formatScore = (value, fallback = '—') => {
  if (value === null || value === undefined || value === '') return fallback
  const n = Number(value)
  if (Number.isNaN(n)) return fallback
  return Number.isInteger(n) ? String(n) : n.toFixed(1)
}

/** 百分比 */
export const formatPercent = (value, digits = 1, fallback = '—') => {
  if (value === null || value === undefined || value === '') return fallback
  const n = Number(value)
  return Number.isNaN(n) ? fallback : `${n.toFixed(digits)}%`
}

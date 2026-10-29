/**
 * 头像相关工具：无图时的文字头像配色，以及头像地址解析。
 */

/** 与设计令牌同源的一组可读性较好的底色 */
const PALETTE = [
  '#3b82f6',
  '#0ea5e9',
  '#14b8a6',
  '#10b981',
  '#f59e0b',
  '#f97316',
  '#ef4444',
  '#64748b',
  '#06b6d4',
  '#84cc16',
  '#ea580c',
  '#0284c7'
]

/** 无名字时的中性灰 */
const FALLBACK_COLOR = '#94a3b8'

/**
 * 根据姓名取一个稳定的底色，同一个人在任何页面颜色都一致。
 * @param {string} name
 * @returns {string} 十六进制颜色
 */
export const getAvatarColor = (name) => {
  const text = String(name ?? '').trim()
  if (!text) return FALLBACK_COLOR

  let hash = 0
  for (let i = 0; i < text.length; i += 1) {
    hash += text.charCodeAt(i)
  }
  return PALETTE[hash % PALETTE.length]
}

/**
 * 取姓名首字作为文字头像内容。
 * @param {string} name
 * @returns {string} 首字；无有效姓名时返回空串
 */
export const getFirstChar = (name) => {
  const text = String(name ?? '').trim()
  return text ? text.charAt(0) : ''
}

const API_PREFIX = '/ZhiYanAcademy'

/**
 * 解析头像地址。
 *
 * 数据库里的 avatar 字段历史上存过好几种形式：完整 URL、/upload/ 开头的
 * 静态路径、以及不带前缀的相对路径。静态路径在带 contextPath 的部署下会 404，
 * 所以统一改走受控的头像接口。
 *
 * @param {string} avatar 数据库中的 avatar 字段
 * @param {'admin' | 'teacher' | 'student'} role
 * @param {number | string} id 对应角色的主键
 * @returns {string} 可直接用于 img src 的地址；无法解析时返回空串
 */
export const resolveAvatarUrl = (avatar, role, id) => {
  const fallback = role && id ? `${API_PREFIX}/avatar/get/${role}/${id}` : ''
  if (!avatar) return fallback

  if (avatar.startsWith('http')) return avatar
  if (avatar.startsWith(API_PREFIX)) return avatar

  // 上传目录不一定对外可访问，走接口读取更可靠
  if (avatar.startsWith('/upload/')) return fallback || `${API_PREFIX}${avatar}`
  if (avatar.startsWith('/')) return `${API_PREFIX}${avatar}`

  return fallback
}

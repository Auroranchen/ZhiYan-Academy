/**
 * 站内消息实时推送客户端（单例）。
 * - 连接后端 /ws/notify?type=&uid=，收到新消息时：未读数 +1，并弹出通知。
 * - 提供初始未读数拉取、定时/聚焦重新同步（兜底，保证已读后红点能回落）。
 * - 断线自动重连（指数退避）。
 */
import { reactive } from 'vue'
import { ElNotification } from 'element-plus'
import { getUnreadCount } from '@/api/message'
import router from '@/router'

export const notifyState = reactive({ unread: 0 })

let ws = null
let closedByUser = false
let retryTimer = null
let retryDelay = 2000
let resyncTimer = null
let ctx = null // { type, uid, role }

const roleToType = (role) => (role === 'student' ? 1 : role === 'teacher' ? 2 : 0)

const buildUrl = () => {
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${proto}://${window.location.host}/ZhiYanAcademy/ws/notify?type=${ctx.type}&uid=${ctx.uid}`
}

const msgRoute = () => {
  if (!ctx) return '/'
  if (ctx.role === 'student') return '/student/message'
  if (ctx.role === 'teacher') return '/teacher/message'
  return '/admin/message'
}

const fetchUnread = async () => {
  if (!ctx || !ctx.uid) return
  try {
    const res = await getUnreadCount(ctx.type, ctx.uid)
    notifyState.unread = res.data || 0
  } catch (e) {
    /* 忽略 */
  }
}

const connect = () => {
  try {
    ws = new WebSocket(buildUrl())
  } catch (e) {
    scheduleRetry()
    return
  }

  ws.onopen = () => { retryDelay = 2000 }

  ws.onmessage = (evt) => {
    try {
      const data = JSON.parse(evt.data)
      if (data.type === 'message') {
        notifyState.unread++
        ElNotification({
          title: data.title || '新消息',
          message: data.content || '',
          type: 'info',
          duration: 5000,
          position: 'bottom-right',
          onClick: () => { router.push(msgRoute()) }
        })
      }
    } catch (e) {
      /* 忽略非法消息 */
    }
  }

  ws.onclose = () => { if (!closedByUser) scheduleRetry() }
  ws.onerror = () => { try { ws && ws.close() } catch (e) { /* ignore */ } }
}

const scheduleRetry = () => {
  if (closedByUser) return
  clearTimeout(retryTimer)
  retryTimer = setTimeout(connect, retryDelay)
  retryDelay = Math.min(retryDelay * 2, 30000)
}

const onFocus = () => { fetchUnread() }

/**
 * 启动实时通知（登录后在主框架挂载时调用）。
 */
export function startNotify(user) {
  if (!user) return
  const role = user.role
  const type = roleToType(role)
  const uid = role === 'student' ? user.studentid : role === 'teacher' ? user.teacherid : user.adminid
  if (!uid) return
  // 管理员在当前模型下不接收站内消息，无需建立连接
  if (type === 0) return
  // 同一用户已连接则跳过
  if (ws && ctx && ctx.type === type && ctx.uid === uid) return

  stopNotify()
  closedByUser = false
  ctx = { type, uid, role }
  fetchUnread()
  connect()

  clearInterval(resyncTimer)
  resyncTimer = setInterval(fetchUnread, 60000) // 60s 兜底重新同步
  window.addEventListener('focus', onFocus)
}

/**
 * 关闭连接（退出登录/离开主框架时调用）。
 */
export function stopNotify() {
  closedByUser = true
  clearTimeout(retryTimer)
  clearInterval(resyncTimer)
  window.removeEventListener('focus', onFocus)
  try { ws && ws.close() } catch (e) { /* ignore */ }
  ws = null
  ctx = null
}

/** 手动触发未读数重新同步（如在消息中心标记已读后调用）。 */
export function refreshUnread() { fetchUnread() }

/** 清零未读数（标记全部已读后即时反馈）。 */
export function clearUnread() { notifyState.unread = 0 }

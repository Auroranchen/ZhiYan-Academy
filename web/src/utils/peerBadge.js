/**
 * 作业互评待办徽章（学生端）
 * - 轮询 /peer/todo，统计待评阅数量，驱动顶部导航红点。
 */
import { reactive } from 'vue'
import { listPeerTodo } from '@/api/peerReview'

export const peerState = reactive({ todo: 0 })

/** 兜底轮询间隔 */
const REFRESH_INTERVAL = 60000

let timer = null
let active = false

export async function refreshPeerTodo() {
  try {
    const res = await listPeerTodo()
    peerState.todo = (res.data || []).length
  } catch (e) {
    // 静默：未登录或无权限时忽略
  }
}

export function startPeerBadge(user) {
  // 仅学生有互评待办
  if (!user || !user.studentid) return
  if (active) return
  active = true
  refreshPeerTodo()
  timer = setInterval(refreshPeerTodo, REFRESH_INTERVAL)
  window.addEventListener('focus', refreshPeerTodo)
}

export function stopPeerBadge() {
  active = false
  peerState.todo = 0
  if (timer) { clearInterval(timer); timer = null }
  window.removeEventListener('focus', refreshPeerTodo)
}

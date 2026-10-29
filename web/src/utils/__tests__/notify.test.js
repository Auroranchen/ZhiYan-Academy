// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 站内消息的 WebSocket 单例。它在主布局挂载时启动、退出登录时关闭，
 * 一旦重连或清理有疏漏，用户会遇到「红点不动」或「退出后还在弹通知」。
 */

const getUnreadCount = vi.fn()
vi.mock('@/api/message', () => ({ getUnreadCount: (...args) => getUnreadCount(...args) }))

const notification = vi.fn()
vi.mock('element-plus', () => ({ ElNotification: (...args) => notification(...args) }))

const router = { push: vi.fn() }
vi.mock('@/router', () => ({ default: router }))

/** 记录所有被创建的连接，便于断言重连行为 */
const sockets = []
class FakeWebSocket {
  constructor(url) {
    this.url = url
    this.closed = false
    sockets.push(this)
  }
  close() {
    this.closed = true
  }
}

const {
  notifyState,
  startNotify,
  stopNotify,
  refreshUnread,
  clearUnread
} = await import('@/utils/notify')

const student = { role: 'student', studentid: 11 }
const teacher = { role: 'teacher', teacherid: 22 }
const RESYNC_INTERVAL = 60000

/** 拿到最近一次建立的连接 */
const latest = () => sockets[sockets.length - 1]

/** 模拟连接握手成功；退避间隔只在这时才会重置 */
const open = () => latest().onopen()

beforeEach(() => {
  vi.useFakeTimers()
  sockets.length = 0
  getUnreadCount.mockReset()
  getUnreadCount.mockResolvedValue({ data: 0 })
  notification.mockReset()
  router.push.mockReset()
  global.WebSocket = FakeWebSocket
})

afterEach(() => {
  stopNotify()
  vi.useRealTimers()
})

describe('启动条件', () => {
  it('学生登录后建立连接并拉一次未读数', async () => {
    getUnreadCount.mockResolvedValue({ data: 5 })

    startNotify(student)

    expect(sockets).toHaveLength(1)
    await vi.waitFor(() => expect(notifyState.unread).toBe(5))
  })

  it('连接地址带上角色类型与用户 ID', () => {
    startNotify(student)

    expect(latest().url).toContain('type=1')
    expect(latest().url).toContain('uid=11')
  })

  it('教师的角色类型不同', () => {
    startNotify(teacher)

    expect(latest().url).toContain('type=2')
    expect(latest().url).toContain('uid=22')
  })

  it('管理员当前不接收站内消息，不建立连接', () => {
    startNotify({ role: 'admin', adminid: 1 })

    expect(sockets).toHaveLength(0)
  })

  it('缺少用户或 ID 时不建立连接', () => {
    startNotify(null)
    startNotify({ role: 'student' })

    expect(sockets).toHaveLength(0)
  })

  it('同一用户重复启动复用已有连接', () => {
    startNotify(student)
    startNotify(student)
    startNotify(student)

    expect(sockets).toHaveLength(1)
  })

  it('换用户时先断开旧连接', () => {
    startNotify(student)
    const first = latest()

    startNotify(teacher)

    expect(first.closed).toBe(true)
    expect(sockets).toHaveLength(2)
  })
})

describe('收到推送', () => {
  it('未读数加一并弹出通知', () => {
    startNotify(student)
    notifyState.unread = 0

    latest().onmessage({ data: JSON.stringify({ type: 'message', title: '新作业', content: '请查收' }) })

    expect(notifyState.unread).toBe(1)
    expect(notification).toHaveBeenCalledTimes(1)
  })

  it('点击通知跳到对应角色的消息页', () => {
    startNotify(student)
    latest().onmessage({ data: JSON.stringify({ type: 'message' }) })

    notification.mock.calls[0][0].onClick()

    expect(router.push).toHaveBeenCalledWith('/student/message')
  })

  it('教师跳到教师端消息页', () => {
    startNotify(teacher)
    latest().onmessage({ data: JSON.stringify({ type: 'message' }) })
    notification.mock.calls[0][0].onClick()

    expect(router.push).toHaveBeenCalledWith('/teacher/message')
  })

  it('非法 JSON 与非消息类型都被忽略，不影响连接', () => {
    startNotify(student)
    notifyState.unread = 0

    expect(() => latest().onmessage({ data: '这不是 JSON' })).not.toThrow()
    latest().onmessage({ data: JSON.stringify({ type: 'heartbeat' }) })

    expect(notifyState.unread).toBe(0)
    expect(notification).not.toHaveBeenCalled()
  })
})

describe('断线重连', () => {
  const BASE_DELAY = 2000

  it('非主动关闭时会重连', async () => {
    startNotify(student)
    open()

    latest().onclose()
    await vi.advanceTimersByTimeAsync(BASE_DELAY)

    expect(sockets).toHaveLength(2)
  })

  it('连续失败时退避加倍，避免连打服务端', async () => {
    startNotify(student)
    open()

    // 第一次断开：等基础间隔即可重连
    latest().onclose()
    await vi.advanceTimersByTimeAsync(BASE_DELAY)
    expect(sockets).toHaveLength(2)

    // 这次没有握手成功就又断了，间隔翻倍，原时长内不该有新连接
    latest().onclose()
    await vi.advanceTimersByTimeAsync(BASE_DELAY)
    expect(sockets).toHaveLength(2)

    await vi.advanceTimersByTimeAsync(BASE_DELAY)
    expect(sockets).toHaveLength(3)
  })

  it('重连成功后退避回到基础间隔', async () => {
    startNotify(student)
    open()

    latest().onclose()
    await vi.advanceTimersByTimeAsync(BASE_DELAY)
    open()

    latest().onclose()
    await vi.advanceTimersByTimeAsync(BASE_DELAY)

    expect(sockets).toHaveLength(3)
  })

  it('主动关闭后不再重连', async () => {
    startNotify(student)
    open()
    const socket = latest()

    stopNotify()
    socket.onclose()
    await vi.advanceTimersByTimeAsync(30000)

    expect(sockets).toHaveLength(1)
  })
})

describe('兜底同步与清理', () => {
  it('定时重新拉取未读数，保证已读后红点回落', async () => {
    startNotify(student)
    getUnreadCount.mockClear()

    await vi.advanceTimersByTimeAsync(RESYNC_INTERVAL * 2)

    expect(getUnreadCount).toHaveBeenCalledTimes(2)
  })

  it('停止后不再同步', async () => {
    startNotify(student)
    stopNotify()
    getUnreadCount.mockClear()

    await vi.advanceTimersByTimeAsync(RESYNC_INTERVAL * 2)

    expect(getUnreadCount).not.toHaveBeenCalled()
  })

  it('停止会断开连接', () => {
    startNotify(student)
    const socket = latest()

    stopNotify()

    expect(socket.closed).toBe(true)
  })

  it('未启动时手动刷新不会发请求', async () => {
    await refreshUnread()

    expect(getUnreadCount).not.toHaveBeenCalled()
  })

  it('标记全部已读后可立即清零，不用等下一次同步', () => {
    notifyState.unread = 9

    clearUnread()

    expect(notifyState.unread).toBe(0)
  })

  it('未启动就调用停止不会报错', () => {
    expect(() => stopNotify()).not.toThrow()
  })
})

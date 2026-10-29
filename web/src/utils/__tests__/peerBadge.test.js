// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 顶栏红点的轮询单例。它挂在全局布局上，一旦漏清理定时器或 focus 监听，
 * 退出登录后仍会持续请求，还会把上一个账号的待办数留在界面上。
 */

const listPeerTodo = vi.fn()
vi.mock('@/api/peerReview', () => ({ listPeerTodo: (...args) => listPeerTodo(...args) }))

const { peerState, refreshPeerTodo, startPeerBadge, stopPeerBadge } = await import(
  '@/utils/peerBadge'
)

const student = { studentid: 7 }
const REFRESH_INTERVAL = 60000

beforeEach(() => {
  vi.useFakeTimers()
  vi.restoreAllMocks()
  listPeerTodo.mockReset()
  listPeerTodo.mockResolvedValue({ data: [] })
  vi.spyOn(window, 'addEventListener')
  vi.spyOn(window, 'removeEventListener')
})

afterEach(() => {
  stopPeerBadge()
  vi.useRealTimers()
})

describe('启动条件', () => {
  it('学生进入后立即拉一次待办', async () => {
    listPeerTodo.mockResolvedValue({ data: [{ id: 1 }, { id: 2 }] })

    startPeerBadge(student)
    await vi.waitFor(() => expect(peerState.todo).toBe(2))
  })

  it('教师与未登录用户不启动轮询', () => {
    startPeerBadge({ teacherid: 3 })
    startPeerBadge(null)
    startPeerBadge(undefined)

    expect(listPeerTodo).not.toHaveBeenCalled()
  })

  it('重复启动不会叠加定时器', async () => {
    startPeerBadge(student)
    startPeerBadge(student)
    startPeerBadge(student)
    listPeerTodo.mockClear()

    await vi.advanceTimersByTimeAsync(REFRESH_INTERVAL)

    expect(listPeerTodo).toHaveBeenCalledTimes(1)
  })
})

describe('轮询与兜底', () => {
  it('按固定间隔持续刷新', async () => {
    startPeerBadge(student)
    listPeerTodo.mockClear()

    await vi.advanceTimersByTimeAsync(REFRESH_INTERVAL * 3)

    expect(listPeerTodo).toHaveBeenCalledTimes(3)
  })

  it('窗口重新聚焦时补一次刷新，避免红点滞留', () => {
    startPeerBadge(student)

    expect(window.addEventListener).toHaveBeenCalledWith('focus', refreshPeerTodo)
  })

  it('接口失败时保持原值，不把红点清成 0', async () => {
    listPeerTodo.mockResolvedValue({ data: [{ id: 1 }] })
    startPeerBadge(student)
    await vi.waitFor(() => expect(peerState.todo).toBe(1))

    listPeerTodo.mockRejectedValue(new Error('401'))
    await refreshPeerTodo()

    expect(peerState.todo).toBe(1)
  })

  it('返回空或缺字段时按 0 处理', async () => {
    listPeerTodo.mockResolvedValue({})
    await refreshPeerTodo()

    expect(peerState.todo).toBe(0)
  })
})

describe('停止', () => {
  it('停掉后不再轮询，红点归零', async () => {
    startPeerBadge(student)
    stopPeerBadge()
    listPeerTodo.mockClear()

    await vi.advanceTimersByTimeAsync(REFRESH_INTERVAL * 2)

    expect(listPeerTodo).not.toHaveBeenCalled()
    expect(peerState.todo).toBe(0)
  })

  it('摘掉 focus 监听，避免退出登录后仍在请求', () => {
    startPeerBadge(student)
    stopPeerBadge()

    expect(window.removeEventListener).toHaveBeenCalledWith('focus', refreshPeerTodo)
  })

  it('停止后可以重新启动，换账号时不残留', async () => {
    startPeerBadge(student)
    stopPeerBadge()

    listPeerTodo.mockResolvedValue({ data: [{ id: 9 }] })
    startPeerBadge(student)

    await vi.waitFor(() => expect(peerState.todo).toBe(1))
  })

  it('未启动就调用停止不会报错', () => {
    expect(() => stopPeerBadge()).not.toThrow()
  })
})

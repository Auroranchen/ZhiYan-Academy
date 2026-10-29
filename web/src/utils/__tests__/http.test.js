import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 所有接口请求都要过这两个拦截器，它们编码了几条产品决策：
 * 哪些接口放宽超时、什么时候弹提示、401 之后怎么收场。
 * 这里把拦截器函数抓出来单独调用，不依赖真实网络。
 */

const captured = { request: null, requestError: null, response: null, responseError: null }

vi.mock('axios', () => {
  const instance = {
    interceptors: {
      request: {
        use: (onFulfilled, onRejected) => {
          captured.request = onFulfilled
          captured.requestError = onRejected
        }
      },
      response: {
        use: (onFulfilled, onRejected) => {
          captured.response = onFulfilled
          captured.responseError = onRejected
        }
      }
    }
  }
  return {
    default: {
      create: () => instance,
      isCancel: (e) => e?.__cancel === true
    }
  }
})

const message = { error: vi.fn(), success: vi.fn(), warning: vi.fn() }
vi.mock('element-plus', () => ({ ElMessage: message }))

const router = { push: vi.fn() }
vi.mock('@/router', () => ({ default: router }))

const store = { dispatch: vi.fn() }
vi.mock('@/store', () => ({ default: store }))

await import('@/utils/http')

const onRequest = (config) => captured.request(config)
const onResponse = (response) => captured.response(response)
const onResponseError = (error) => captured.responseError(error)

/** 拦截器抛出的是 reject，用这个把错误取回来 */
const rejectionOf = async (promise) => {
  try {
    await promise
    throw new Error('预期该 Promise 会 reject')
  } catch (e) {
    return e
  }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('请求拦截器：AI 接口放宽超时', () => {
  const AI_TIMEOUT = 180000
  const DEFAULT_TIMEOUT = 30000

  it('大模型相关路径统一放宽到三分钟', () => {
    const aiPaths = [
      '/ai/explain/1',
      '/ai-insight/student-report',
      '/agent/diagnose',
      '/profile/chat',
      '/profile/evaluate',
      '/plagiarism/ai-detect',
      '/exam/ai-review',
      '/learnpath/generate'
    ]

    aiPaths.forEach((url) => {
      expect(onRequest({ url, timeout: DEFAULT_TIMEOUT }).timeout, url).toBe(AI_TIMEOUT)
    })
  })

  it('普通接口保持默认超时', () => {
    expect(onRequest({ url: '/homework/list', timeout: DEFAULT_TIMEOUT }).timeout).toBe(
      DEFAULT_TIMEOUT
    )
  })

  it('没写超时的 AI 接口也会被放宽', () => {
    expect(onRequest({ url: '/ai/parseQuestions' }).timeout).toBe(AI_TIMEOUT)
  })

  it('调用方显式指定的超时不被覆盖', () => {
    expect(onRequest({ url: '/ai/generateQuestions', timeout: 120000 }).timeout).toBe(120000)
  })

  it('url 缺失时不报错', () => {
    expect(() => onRequest({ timeout: DEFAULT_TIMEOUT })).not.toThrow()
  })
})

describe('响应拦截器：业务码处理', () => {
  it('成功时只把 data 交给调用方，剥掉 axios 外层', () => {
    const body = { code: 200, data: [1, 2], msg: 'ok' }

    expect(onResponse({ data: body, config: {} })).toBe(body)
  })

  it('下载类响应整体透传，不做业务码校验', () => {
    const response = { data: new Blob(), config: { responseType: 'blob' } }

    expect(onResponse(response)).toBe(response)
  })

  it('401 会清理登录态并跳回登录页', async () => {
    await rejectionOf(onResponse({ data: { code: 401, msg: '未授权' }, config: {} }))

    expect(store.dispatch).toHaveBeenCalledWith('logout')
    expect(router.push).toHaveBeenCalledWith('/login')
    expect(message.error).toHaveBeenCalledWith('请先登录')
  })

  it('业务失败时把 code 与 msg 挂到错误对象上，供页面区分处理', async () => {
    const error = await rejectionOf(
      onResponse({ data: { code: 429, msg: '请求过于频繁' }, config: {} })
    )

    expect(error.code).toBe(429)
    expect(error.msg).toBe('请求过于频繁')
    expect(message.error).toHaveBeenCalledWith('请求过于频繁')
  })

  it('suppressMessage 时不弹提示，但仍然 reject 交给页面处理', async () => {
    const error = await rejectionOf(
      onResponse({ data: { code: 500, msg: '生成失败' }, config: { suppressMessage: true } })
    )

    expect(message.error).not.toHaveBeenCalled()
    expect(error.msg).toBe('生成失败')
  })

  it('suppressMessage 对 401 同样生效，但登录态照常清理', async () => {
    await rejectionOf(
      onResponse({ data: { code: 401, msg: '过期' }, config: { suppressMessage: true } })
    )

    expect(message.error).not.toHaveBeenCalled()
    expect(store.dispatch).toHaveBeenCalledWith('logout')
  })
})

describe('响应拦截器：网络与状态码', () => {
  it('用户主动取消不算失败，不弹提示', async () => {
    await rejectionOf(onResponseError({ __cancel: true, message: 'canceled' }))
    await rejectionOf(onResponseError({ code: 'ERR_CANCELED', message: 'canceled' }))

    expect(message.error).not.toHaveBeenCalled()
  })

  it('断网与超时给出可读文案', async () => {
    await rejectionOf(onResponseError({ message: 'Network Error' }))
    expect(message.error).toHaveBeenCalledWith('网络连接失败，请检查网络')

    vi.clearAllMocks()
    await rejectionOf(onResponseError({ message: 'timeout of 30000ms exceeded' }))
    expect(message.error).toHaveBeenCalledWith('请求超时，请稍后重试')
  })

  it('HTTP 401 同样清理登录态', async () => {
    await rejectionOf(onResponseError({ message: 'Request failed', response: { status: 401 } }))

    expect(store.dispatch).toHaveBeenCalledWith('logout')
    expect(router.push).toHaveBeenCalledWith('/login')
  })

  it('404 与 500 分别给出对应文案', async () => {
    await rejectionOf(onResponseError({ message: 'x', response: { status: 404 } }))
    expect(message.error).toHaveBeenCalledWith('请求的资源不存在')

    vi.clearAllMocks()
    await rejectionOf(onResponseError({ message: 'x', response: { status: 500 } }))
    expect(message.error).toHaveBeenCalledWith('服务器内部错误')
  })

  it('其他状态码带上状态数字，便于排查', async () => {
    await rejectionOf(onResponseError({ message: 'x', response: { status: 418 } }))

    expect(message.error).toHaveBeenCalledWith('请求失败: 418')
  })

  it('连响应都没有时给出兜底文案', async () => {
    await rejectionOf(onResponseError({ message: '未知' }))

    expect(message.error).toHaveBeenCalledWith('请求失败，请稍后重试')
  })

  it('设置了 suppressMessage 的请求，网络层失败也不弹提示', async () => {
    await rejectionOf(
      onResponseError({ message: 'Network Error', config: { suppressMessage: true } })
    )

    expect(message.error).not.toHaveBeenCalled()
  })
})

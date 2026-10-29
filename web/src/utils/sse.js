/**
 * Server-Sent Events 帧解析
 *
 * 没有直接用 EventSource，因为它不支持自定义请求头，也不便于用 AbortSignal 取消；
 * 这里基于 fetch + ReadableStream 自己按协议切帧。
 *
 * 帧格式：以 `event:` 指定事件名（缺省 message），`data:` 可出现多行，空行表示一帧结束。
 */

/** 按 SSE 规范，字段值只去掉紧跟冒号的一个空格，不能整体 trim */
const stripFieldPrefix = (line, field) => {
  const value = line.slice(field.length + 1)
  return value.startsWith(' ') ? value.slice(1) : value
}

/**
 * 逐帧读取事件流，每读到完整一帧就回调。
 *
 * @param {Response} response fetch 的响应，需带 body
 * @param {(event: string, data: string) => (void | Promise<void>)} onFrame
 * @throws {Error} 响应不可读时抛出
 */
export const readEventStream = async (response, onFrame) => {
  if (!response?.ok || !response.body) throw new Error('SSE 连接失败')

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')

  let buffer = ''
  let eventName = 'message'
  let dataLines = []

  const flush = async () => {
    if (!dataLines.length) return
    const data = dataLines.join('\n')
    dataLines = []
    const name = eventName
    eventName = 'message'
    await onFrame(name, data)
  }

  const consume = async (line) => {
    if (line === '') {
      await flush()
      return
    }
    // 以冒号开头的是注释行（常用作心跳），直接忽略
    if (line.startsWith(':')) return
    if (line.startsWith('event:')) eventName = stripFieldPrefix(line, 'event').trim()
    else if (line.startsWith('data:')) dataLines.push(stripFieldPrefix(line, 'data'))
  }

  for (;;) {
    const { value, done } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    // 最后一段可能被切在半路，留到下次拼接
    buffer = lines.pop()

    for (const raw of lines) {
      await consume(raw.replace(/\r$/, ''))
    }
  }

  // 流结束时缓冲区里可能还剩最后一行（服务端没有以换行收尾）
  if (buffer) await consume(buffer.replace(/\r$/, ''))
  await flush()
}

/**
 * 在 readEventStream 之上把 data 解析成对象。
 * 单帧解析失败不应中断整个流，退化成空对象继续。
 *
 * @param {Response} response
 * @param {(event: string, payload: object) => (void | Promise<void>)} onEvent
 */
export const readJsonEventStream = (response, onEvent) =>
  readEventStream(response, async (event, data) => {
    let payload
    try {
      payload = JSON.parse(data)
    } catch {
      payload = {}
    }
    await onEvent(event, payload)
  })

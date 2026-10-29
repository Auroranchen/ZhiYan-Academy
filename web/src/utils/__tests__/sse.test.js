import { describe, expect, it } from 'vitest'
import { readEventStream, readJsonEventStream } from '@/utils/sse'

const encoder = new TextEncoder()

/** 构造一个把若干文本块依次吐出的假 Response，用于模拟分片到达 */
const fakeResponse = (chunks, { ok = true, hasBody = true } = {}) => {
  let index = 0
  return {
    ok,
    body: hasBody
      ? {
          getReader: () => ({
            read: async () =>
              index < chunks.length
                ? { value: encoder.encode(chunks[index++]), done: false }
                : { value: undefined, done: true }
          })
        }
      : null
  }
}

const collect = async (chunks) => {
  const frames = []
  await readEventStream(fakeResponse(chunks), (event, data) => {
    frames.push({ event, data })
  })
  return frames
}

describe('SSE 帧解析', () => {
  it('按空行切分事件帧', async () => {
    const frames = await collect(['event: step\ndata: one\n\nevent: done\ndata: two\n\n'])

    expect(frames).toEqual([
      { event: 'step', data: 'one' },
      { event: 'done', data: 'two' }
    ])
  })

  it('缺省事件名为 message', async () => {
    expect(await collect(['data: hello\n\n'])).toEqual([{ event: 'message', data: 'hello' }])
  })

  it('多行 data 合并成一条，用换行连接', async () => {
    expect(await collect(['data: a\ndata: b\n\n'])).toEqual([{ event: 'message', data: 'a\nb' }])
  })

  it('事件名不会串到下一帧', async () => {
    const frames = await collect(['event: step\ndata: one\n\ndata: two\n\n'])
    expect(frames[1].event).toBe('message')
  })

  it('只去掉冒号后的一个空格，保留其余空白', async () => {
    expect(await collect(['data:  两个空格\n\n'])).toEqual([
      { event: 'message', data: ' 两个空格' }
    ])
  })

  it('帧被切分到多个数据块时能正确拼接', async () => {
    const frames = await collect(['event: st', 'ep\ndata: hel', 'lo\n\n'])
    expect(frames).toEqual([{ event: 'step', data: 'hello' }])
  })

  it('中文在 UTF-8 分片边界被切开也不会乱码', async () => {
    // 把「诊断完成」的字节流从中间截断，模拟 TCP 分片
    const bytes = encoder.encode('data: 诊断完成\n\n')
    const cut = 8
    const decoder = new TextDecoder('utf-8')
    const frames = []
    await readEventStream(
      fakeResponse([decoder.decode(bytes.slice(0, cut), { stream: true })]),
      () => {}
    ).catch(() => {})

    // 直接用原始字节分片验证
    let index = 0
    const parts = [bytes.slice(0, cut), bytes.slice(cut)]
    await readEventStream(
      {
        ok: true,
        body: {
          getReader: () => ({
            read: async () =>
              index < parts.length ? { value: parts[index++], done: false } : { done: true }
          })
        }
      },
      (event, data) => frames.push(data)
    )

    expect(frames).toEqual(['诊断完成'])
  })

  it('末尾没有换行时最后一帧也不会丢', async () => {
    expect(await collect(['data: last'])).toEqual([{ event: 'message', data: 'last' }])
  })

  it('忽略注释行（服务端心跳）', async () => {
    expect(await collect([': keep-alive\ndata: real\n\n'])).toEqual([
      { event: 'message', data: 'real' }
    ])
  })

  it('响应不可读时抛错', async () => {
    await expect(readEventStream(fakeResponse([], { ok: false }), () => {})).rejects.toThrow()
    await expect(readEventStream(fakeResponse([], { hasBody: false }), () => {})).rejects.toThrow()
  })
})

describe('JSON 事件流', () => {
  it('自动解析成对象', async () => {
    const events = []
    await readJsonEventStream(fakeResponse(['event: step\ndata: {"step":"diagnose"}\n\n']), (e, p) =>
      events.push([e, p])
    )
    expect(events).toEqual([['step', { step: 'diagnose' }]])
  })

  it('单帧内容非法时退化成空对象，不中断后续帧', async () => {
    const events = []
    await readJsonEventStream(
      fakeResponse(['data: 不是JSON\n\ndata: {"ok":true}\n\n']),
      (e, p) => events.push(p)
    )
    expect(events).toEqual([{}, { ok: true }])
  })
})

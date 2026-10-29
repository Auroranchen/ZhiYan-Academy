import { describe, it, expect, vi, beforeEach } from 'vitest'

// 只桩掉 API 层：utils/ai.js 仅依赖这两个函数，
// 桩掉之后就不会连带加载 http/router/element-plus。
vi.mock('@/api/ai', () => ({
  chatWithAssistant: vi.fn(() => Promise.resolve({ data: 'ok' })),
  chatWithHomeworkAssistant: vi.fn(() => Promise.resolve({ data: 'ok' }))
}))

import { chatWithAssistant, chatWithHomeworkAssistant } from '@/api/ai'
import { sendMessage } from '@/utils/ai'

const MESSAGES = [{ role: 'user', content: '这道题选什么？' }]
const HOMEWORK_BRIEF = '当前作业：Java 集合框架练习（在线答题，共 5 道题）'

describe('utils/ai 的作业辅导路由与加固声明', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('带 homeworkId 时改走作业辅导端点', async () => {
    await sendMessage(MESSAGES, 'student', '张三', HOMEWORK_BRIEF, 3, 7)

    expect(chatWithHomeworkAssistant).toHaveBeenCalledTimes(1)
    expect(chatWithAssistant).not.toHaveBeenCalled()
    expect(chatWithHomeworkAssistant.mock.calls[0][0].homeworkId).toBe('7')
  })

  it('不带 homeworkId 时走通用端点，且不夹带该字段', async () => {
    await sendMessage(MESSAGES, 'student', '张三', null, 3)

    expect(chatWithAssistant).toHaveBeenCalledTimes(1)
    expect(chatWithHomeworkAssistant).not.toHaveBeenCalled()
    expect(chatWithAssistant.mock.calls[0][0]).not.toHaveProperty('homeworkId')
  })

  it('systemPrompt 不再声明「作业辅导模式」——加固已不由客户端自报', async () => {
    await sendMessage(MESSAGES, 'student', '张三', HOMEWORK_BRIEF, 3, 7)

    const { systemPrompt } = chatWithHomeworkAssistant.mock.calls[0][0]
    // 这两个串原先既是「加固声明」又被服务端当作策略开关，是漏洞的成因
    expect(systemPrompt).not.toContain('作业辅导模式')
    expect(systemPrompt).not.toContain('学术诚信')
    // 上下文本身保留，只为让模型知道学生在哪份作业里提问
    expect(systemPrompt).toContain(HOMEWORK_BRIEF)
  })

  it('课程 ID 仍然透传，服务端据此做课程资料检索与权限校验', async () => {
    await sendMessage(MESSAGES, 'student', '张三', HOMEWORK_BRIEF, 3, 7)

    expect(chatWithHomeworkAssistant.mock.calls[0][0].courseId).toBe('3')
  })
})

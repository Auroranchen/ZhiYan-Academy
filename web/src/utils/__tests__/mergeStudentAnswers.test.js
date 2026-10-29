import { describe, expect, it } from 'vitest'
import { mergeStudentAnswers } from '@/utils/exam'

const homework = (questions) => JSON.stringify({ version: 1, questions })
const submission = (questions, answers) => JSON.stringify({ version: 1, questions, answers })

describe('mergeStudentAnswers', () => {
  it('按题目 id 回填标准答案', () => {
    const result = mergeStudentAnswers(
      submission([{ id: 'b', title: '第二题' }, { id: 'a', title: '第一题' }], { a: 1, b: 0 }),
      homework([{ id: 'a', title: '第一题', answer: 2 }, { id: 'b', title: '第二题', answer: 3 }])
    )

    expect(result.questions).toEqual([
      { id: 'b', title: '第二题', answer: 3 },
      { id: 'a', title: '第一题', answer: 2 }
    ])
    expect(result.answers).toEqual({ a: 1, b: 0 })
  })

  it('历史数据缺少 id 时按序号对齐', () => {
    const result = mergeStudentAnswers(
      submission([{ title: '第一题' }, { title: '第二题' }], {}),
      homework([{ title: '第一题', answer: 'A' }, { title: '第二题', answer: 'B' }])
    )

    expect(result.questions.map((q) => q.answer)).toEqual(['A', 'B'])
  })

  it('原题比提交少时，多出的题答案置空而不是抛错', () => {
    const result = mergeStudentAnswers(
      submission([{ id: 'a' }, { id: 'b' }], {}),
      homework([{ id: 'a', answer: 1 }])
    )

    expect(result.questions[0].answer).toBe(1)
    expect(result.questions[1].answer).toBe(null)
  })

  it('拿不到原题时原样返回学生提交', () => {
    const result = mergeStudentAnswers(submission([{ id: 'a', title: '题' }], { a: 1 }), null)

    expect(result.questions).toEqual([{ id: 'a', title: '题' }])
    expect(result.answers).toEqual({ a: 1 })
  })

  it('学生提交无法解析时返回 null，由调用方回退到原始内容', () => {
    expect(mergeStudentAnswers('不是 JSON', homework([]))).toBe(null)
    expect(mergeStudentAnswers('', homework([]))).toBe(null)
    expect(mergeStudentAnswers(JSON.stringify({ questions: [] }), homework([]))).toBe(null)
  })
})

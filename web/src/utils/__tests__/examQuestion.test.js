import { describe, expect, it } from 'vitest'
import {
  createExamQuestion,
  isCodeQuestionType,
  resetExamAnswerForType,
  sumExamScore,
  toSavedExamQuestions,
  validateExamQuestions
} from '@/utils/examQuestion'

/** 默认造一道合法的单选题，各用例只覆盖自己关心的字段 */
const question = (overrides = {}) => ({
  ...createExamQuestion(),
  title: '题干',
  options: ['甲', '乙'],
  answer: 0,
  ...overrides
})

describe('新建题目', () => {
  it('默认是带四个空选项的单选题', () => {
    const q = createExamQuestion()

    expect(q.type).toBe('single')
    expect(q.options).toHaveLength(4)
    expect(q.answer).toBeNull()
    expect(q.score).toBeGreaterThan(0)
  })

  it('每题 id 唯一，否则列表渲染会串行', () => {
    const ids = Array.from({ length: 20 }, () => createExamQuestion().id)

    expect(new Set(ids).size).toBe(20)
  })
})

describe('切换题型时重置答案', () => {
  it('切到多选，答案变成空数组而不是残留的下标', () => {
    const q = resetExamAnswerForType({ type: 'multi', answer: 2, options: ['甲', '乙'] })

    expect(q.answer).toEqual([])
  })

  it('切到判断，选项清空、默认选「正确」', () => {
    const q = resetExamAnswerForType({ type: 'judge', answer: [0, 1], options: ['甲', '乙'] })

    expect(q.options).toEqual([])
    expect(q.answer).toBe(true)
  })

  it('切到程序填空，答案变成含一个空的数组', () => {
    const q = resetExamAnswerForType({ type: 'prog_blank', answer: '旧答案', options: ['甲'] })

    expect(q.answer).toEqual([''])
    expect(q.options).toEqual([])
    expect(q.code).toBe('')
  })

  it('切到程序题与代码解释题，答案是文本并保留代码区', () => {
    const types = ['prog', 'code_explain']

    types.forEach((type) => {
      const q = resetExamAnswerForType({ type, answer: ['a'], code: 'int x;' })

      expect(q.answer, type).toBe('')
      expect(q.code, type).toBe('int x;')
    })
  })

  it('切到填空与简答，答案是空文本', () => {
    expect(resetExamAnswerForType({ type: 'blank', answer: 1 }).answer).toBe('')
    expect(resetExamAnswerForType({ type: 'short', answer: [1] }).answer).toBe('')
  })

  it('选择题选项不足两个时补齐，避免出现无从作答的题', () => {
    expect(resetExamAnswerForType({ type: 'single', options: ['只有一个'] }).options).toHaveLength(4)
  })

  it('已有的多个选项会被保留，不会被默认值冲掉', () => {
    const options = ['甲', '乙', '丙']

    expect(resetExamAnswerForType({ type: 'single', options }).options).toEqual(options)
  })

  it('需要判分策略的题型会补上默认值', () => {
    expect(resetExamAnswerForType({ type: 'blank' }).gradingPolicy).toBe('normal')
    expect(resetExamAnswerForType({ type: 'prog_blank' }).gradingPolicy).toBe('normal')
  })

  it('已设置的判分策略不被覆盖', () => {
    expect(resetExamAnswerForType({ type: 'blank', gradingPolicy: 'strict' }).gradingPolicy).toBe(
      'strict'
    )
  })
})

describe('保存前校验', () => {
  it('空试卷不允许保存', () => {
    expect(validateExamQuestions([])).toBe('请至少添加一道题目')
  })

  it('题干为空时指出是第几题', () => {
    expect(validateExamQuestions([question(), question({ title: '  ' })])).toBe('第 2 题题干不能为空')
  })

  it('选择题的空选项与缺失答案都会被拦下', () => {
    const emptyOption = question({ type: 'single', options: ['甲', ''], answer: 0 })
    const noAnswer = question({ type: 'single', options: ['甲', '乙'], answer: null })

    expect(validateExamQuestions([emptyOption])).toContain('空选项')
    expect(validateExamQuestions([noAnswer])).toContain('未设置正确答案')
  })

  it('多选题必须至少选一个正确答案', () => {
    const q = question({ type: 'multi', options: ['甲', '乙'], answer: [] })

    expect(validateExamQuestions([q])).toContain('未设置正确答案')
  })

  it('判断题答案必须是布尔值', () => {
    expect(validateExamQuestions([question({ type: 'judge', answer: null })])).toContain(
      '未设置正确答案'
    )
    expect(validateExamQuestions([question({ type: 'judge', answer: false })])).toBe('')
  })

  it('填空题必须有标准答案', () => {
    expect(validateExamQuestions([question({ type: 'blank', answer: '  ' })])).toContain(
      '未设置标准答案'
    )
  })

  it('程序填空题的每个空都要有答案', () => {
    const noBlank = question({ type: 'prog_blank', answer: [] })
    const emptyBlank = question({ type: 'prog_blank', answer: ['int', ' '] })

    expect(validateExamQuestions([noBlank])).toContain('至少设置一个填空答案')
    expect(validateExamQuestions([emptyBlank])).toContain('空的填空答案')
  })

  it('代码解释题必须给出待解释的代码', () => {
    const q = question({ type: 'code_explain', answer: '解释', code: '' })

    expect(validateExamQuestions([q])).toContain('待解释的代码')
  })

  it('简答与程序题不强制填参考答案，教师可后续评阅', () => {
    const short = question({ type: 'short', answer: '' })
    const prog = question({ type: 'prog', answer: '', code: '' })

    expect(validateExamQuestions([short, prog])).toBe('')
  })

  it('一份合法试卷通过校验', () => {
    const paper = [
      question({ type: 'single', options: ['甲', '乙'], answer: 1 }),
      question({ type: 'multi', options: ['甲', '乙'], answer: [0, 1] }),
      question({ type: 'judge', answer: true }),
      question({ type: 'blank', answer: 'final' }),
      question({ type: 'prog_blank', answer: ['int'], code: '___ x;' })
    ]

    expect(validateExamQuestions(paper)).toBe('')
  })
})

describe('保存形态', () => {
  it('程序填空题带上空的数量，学生端据此渲染输入框', () => {
    const saved = toSavedExamQuestions([question({ type: 'prog_blank', answer: ['a', 'b', 'c'] })])

    expect(saved[0].blankCount).toBe(3)
  })

  it('其他题型不添加多余字段', () => {
    const saved = toSavedExamQuestions([question({ type: 'single' })])

    expect(saved[0].blankCount).toBeUndefined()
  })

  it('不改动传入的题目对象', () => {
    const origin = question({ type: 'prog_blank', answer: ['a'] })
    toSavedExamQuestions([origin])

    expect(origin.blankCount).toBeUndefined()
  })
})

describe('总分统计', () => {
  it('各题分值累加', () => {
    expect(sumExamScore([{ score: 5 }, { score: 10 }, { score: 2.5 }])).toBe(17.5)
  })

  it('分值缺失或非数字按 0 计，不会算出 NaN', () => {
    expect(sumExamScore([{ score: 5 }, {}, { score: 'abc' }, { score: null }])).toBe(5)
  })

  it('空试卷总分为 0', () => {
    expect(sumExamScore([])).toBe(0)
  })
})

describe('代码类题型判定', () => {
  it('三种代码题型返回真，其余为假', () => {
    const codeTypes = ['prog_blank', 'prog', 'code_explain']
    const plainTypes = ['single', 'multi', 'judge', 'blank', 'short']

    codeTypes.forEach((t) => expect(isCodeQuestionType(t), t).toBe(true))
    plainTypes.forEach((t) => expect(isCodeQuestionType(t), t).toBe(false))
  })
})

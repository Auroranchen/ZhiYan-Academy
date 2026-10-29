import { describe, expect, it } from 'vitest'
import {
  createQuestion,
  normalizeQuestion,
  resetAnswerForType,
  validateQuestions
} from '@/utils/questionEditor'

describe('新建试题', () => {
  it('默认是带两个空选项的单选题', () => {
    const q = createQuestion()
    expect(q.type).toBe('single')
    expect(q.options).toEqual(['', ''])
    expect(q.answer).toBeNull()
  })

  it('id 不重复', () => {
    expect(createQuestion().id).not.toBe(createQuestion().id)
  })
})

describe('切换题型', () => {
  it('单选切多选后答案变成数组，避免残留下标', () => {
    const q = { type: 'multi', options: ['A', 'B'], answer: 1 }
    resetAnswerForType(q)
    expect(q.answer).toEqual([])
  })

  it('切成判断题会清空选项', () => {
    const q = { type: 'judge', options: ['A', 'B'], answer: 0 }
    resetAnswerForType(q)
    expect(q.options).toEqual([])
    expect(q.answer).toBeNull()
  })

  it('切成填空题时补上默认判分宽严', () => {
    const q = { type: 'blank', options: ['A'], answer: 0 }
    resetAnswerForType(q)
    expect(q.answer).toBe('')
    expect(q.gradingPolicy).toBe('normal')
  })
})

describe('外部来源的题目规整', () => {
  it('单选答案的字符串下标转成数字', () => {
    expect(normalizeQuestion({ type: 'single', answer: '2', options: ['a', 'b', 'c'] }).answer).toBe(2)
  })

  it('单选没有答案时保持 null 而不是 0', () => {
    expect(normalizeQuestion({ type: 'single', answer: '' }).answer).toBeNull()
  })

  it('多选答案统一成数字数组，非法项丢弃', () => {
    expect(normalizeQuestion({ type: 'multi', answer: ['0', 'x', 2] }).answer).toEqual([0, 2])
  })

  it('多选答案不是数组时兜底为空数组', () => {
    expect(normalizeQuestion({ type: 'multi', answer: '0,1' }).answer).toEqual([])
  })

  it('判断题的字符串答案转成布尔', () => {
    expect(normalizeQuestion({ type: 'judge', answer: 'true' }).answer).toBe(true)
    expect(normalizeQuestion({ type: 'judge', answer: 'false' }).answer).toBe(false)
  })

  it('填空题答案去掉首尾空格', () => {
    expect(normalizeQuestion({ type: 'blank', answer: '  int  ' }).answer).toBe('int')
  })
})

describe('保存前校验', () => {
  const single = { type: 'single', title: '题干', options: ['A', 'B'], answer: 0 }

  it('通过时返回空串', () => {
    expect(validateQuestions([single])).toBe('')
  })

  it('没有试题时拦下', () => {
    expect(validateQuestions([])).toContain('至少添加一道试题')
  })

  it('题干为空时指出题号', () => {
    expect(validateQuestions([{ ...single, title: '  ' }])).toContain('第 1 题')
  })

  it('选项不足两个时拦下', () => {
    expect(validateQuestions([{ ...single, options: ['A'] }])).toContain('两个选项')
  })

  it('选项内容为空时拦下', () => {
    expect(validateQuestions([{ ...single, options: ['A', ' '] }])).toContain('选项内容不能为空')
  })

  it('未选正确答案时拦下', () => {
    expect(validateQuestions([{ ...single, answer: null }])).toContain('请选择正确答案')
    expect(validateQuestions([{ type: 'multi', title: 'x', options: ['A', 'B'], answer: [] }])).toContain(
      '至少选择一个正确答案'
    )
    expect(validateQuestions([{ type: 'judge', title: 'x', answer: null }])).toContain('请选择正确答案')
  })
})

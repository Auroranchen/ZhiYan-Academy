import { describe, expect, it } from 'vitest'
import {
  QUESTION_TYPES,
  isChoiceType,
  isObjectiveType,
  normalizeQuestionType,
  questionTypeLabel,
  questionTypeShort,
  questionTypeTone
} from '@/utils/questionTypes'

describe('题型别名归一', () => {
  it('把后端的各种写法收敛到内部题型', () => {
    expect(normalizeQuestionType('single_choice')).toBe('single')
    expect(normalizeQuestionType('multiple')).toBe('multi')
    expect(normalizeQuestionType('true_false')).toBe('judge')
    expect(normalizeQuestionType('fill_blank')).toBe('blank')
    expect(normalizeQuestionType('essay')).toBe('short')
  })

  it('已经是内部题型时原样返回', () => {
    Object.keys(QUESTION_TYPES).forEach((type) => {
      expect(normalizeQuestionType(type)).toBe(type)
    })
  })

  it('缺省时按单选处理，避免出现无题型的题目', () => {
    expect(normalizeQuestionType(undefined)).toBe('single')
    expect(normalizeQuestionType('')).toBe('single')
  })
})

describe('题型展示', () => {
  it('每个题型都有完整名与简称', () => {
    Object.entries(QUESTION_TYPES).forEach(([type, meta]) => {
      expect(questionTypeLabel(type)).toBe(meta.label)
      expect(questionTypeShort(type)).toBe(meta.short)
      expect(meta.short.length).toBeLessThanOrEqual(meta.label.length)
    })
  })

  it('未知题型原样显示而不是空白', () => {
    expect(questionTypeLabel('unknown')).toBe('unknown')
    expect(questionTypeTone('unknown')).toBe('')
  })

  it('单选的 tone 是空串，el-tag 用默认样式', () => {
    expect(questionTypeTone('single')).toBe('')
    expect(questionTypeTone('multi')).toBe('warning')
  })
})

describe('题型分类', () => {
  it('客观题可自动判分，主观题不可', () => {
    expect(isObjectiveType('single')).toBe(true)
    expect(isObjectiveType('prog_blank')).toBe(true)
    expect(isObjectiveType('short')).toBe(false)
    expect(isObjectiveType('prog')).toBe(false)
  })

  it('只有单选多选有选项', () => {
    expect(isChoiceType('single')).toBe(true)
    expect(isChoiceType('multi')).toBe(true)
    expect(isChoiceType('judge')).toBe(false)
  })
})

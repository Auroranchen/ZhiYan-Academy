import { describe, expect, it } from 'vitest'
import { checkAnswerCorrect, formatAnswerText, toJudgeBoolean } from '@/utils/exam'

const blank = (answer, gradingPolicy) => ({ type: 'blank', answer, gradingPolicy })

describe('填空题判分', () => {
  describe('strict：完全一致才算对', () => {
    it('大小写、空格、标点任一不同都判错', () => {
      expect(checkAnswerCorrect(blank('int a;', 'strict'), 'int a;')).toBe(true)
      expect(checkAnswerCorrect(blank('int a;', 'strict'), 'INT a;')).toBe(false)
      expect(checkAnswerCorrect(blank('int a;', 'strict'), 'int  a;')).toBe(false)
      expect(checkAnswerCorrect(blank('int a;', 'strict'), 'int a')).toBe(false)
    })
  })

  describe('normal：忽略标点，保留大小写与空格', () => {
    it('标点差异判对', () => {
      expect(checkAnswerCorrect(blank('数组，链表', 'normal'), '数组链表')).toBe(true)
      expect(checkAnswerCorrect(blank('return 0;', 'normal'), 'return 0')).toBe(true)
    })

    it('连字符属于标点，同样忽略', () => {
      expect(checkAnswerCorrect(blank('read-only', 'normal'), 'readonly')).toBe(true)
    })

    it('大小写与空格差异仍判错', () => {
      expect(checkAnswerCorrect(blank('return 0;', 'normal'), 'RETURN 0')).toBe(false)
      expect(checkAnswerCorrect(blank('return 0;', 'normal'), 'return  0')).toBe(false)
    })
  })

  describe('loose：忽略大小写、空格与标点，并容忍一个字符的编辑距离', () => {
    it('空格差异判对', () => {
      expect(checkAnswerCorrect(blank('int a = 1;', 'loose'), 'int a=1')).toBe(true)
      expect(checkAnswerCorrect(blank('快速排序', 'loose'), '快速 排序')).toBe(true)
    })

    it('大小写差异判对', () => {
      expect(checkAnswerCorrect(blank('HashMap', 'loose'), 'hashmap')).toBe(true)
    })

    it('相差一个字符仍判对，相差两个字符判错', () => {
      expect(checkAnswerCorrect(blank('binary', 'loose'), 'binry')).toBe(true)
      expect(checkAnswerCorrect(blank('binary', 'loose'), 'bnry')).toBe(false)
    })
  })

  it('未配置标准答案时返回 null，交由人工判分', () => {
    expect(checkAnswerCorrect(blank('   ', 'normal'), '任意')).toBe(null)
    expect(checkAnswerCorrect({ type: 'blank' }, '任意')).toBe(null)
  })
})

describe('判断题判分', () => {
  const judge = (answer) => ({ type: 'judge', answer })

  it('接受布尔、英文与中文三类写法', () => {
    const truthy = [true, 'true', 'True', '1', '正确', '对', '是', '√']
    const falsy = [false, 'false', '0', '错误', '错', '否', '×', '', null, undefined]

    truthy.forEach((value) => expect(toJudgeBoolean(value)).toBe(true))
    falsy.forEach((value) => expect(toJudgeBoolean(value)).toBe(false))
  })

  it('标准答案用中文、学生答布尔时也能判对', () => {
    expect(checkAnswerCorrect(judge('正确'), true)).toBe(true)
    expect(checkAnswerCorrect(judge('正确'), false)).toBe(false)
    expect(checkAnswerCorrect(judge('错误'), false)).toBe(true)
    expect(checkAnswerCorrect(judge(true), '对')).toBe(true)
  })

  it('展示文案同样按归一化结果输出', () => {
    const labels = { trueLabel: '正确', falseLabel: '错误' }
    expect(formatAnswerText(judge(true), '1', labels)).toBe('正确')
    expect(formatAnswerText(judge(true), '错误', labels)).toBe('错误')
  })
})

describe('程序填空判分', () => {
  const progBlank = (answer, gradingPolicy) => ({ type: 'prog_blank', answer, gradingPolicy })

  it('所有空都正确才算对', () => {
    expect(checkAnswerCorrect(progBlank(['int', 'i++'], 'loose'), ['int', 'i++'])).toBe(true)
    expect(checkAnswerCorrect(progBlank(['int', 'i++'], 'loose'), ['int', 'i--'])).toBe(false)
  })

  it('loose 下各空分别忽略空格', () => {
    expect(checkAnswerCorrect(progBlank(['a + b', 'c * d'], 'loose'), ['a+b', 'c*d'])).toBe(true)
  })

  it('漏填判错', () => {
    expect(checkAnswerCorrect(progBlank(['int', 'i++'], 'normal'), ['int'])).toBe(false)
  })
})

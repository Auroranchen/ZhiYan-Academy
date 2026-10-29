import { describe, expect, it } from 'vitest'
import {
  backfillBlanks,
  buildAiQuestions,
  gradingPolicyOf,
  normalizeAiQuestion,
  parseAiQuestions
} from '@/utils/aiQuestions'

describe('判分策略映射', () => {
  it('三档宽严各自对应后端取值', () => {
    expect(gradingPolicyOf('严格')).toBe('strict')
    expect(gradingPolicyOf('一般')).toBe('normal')
    expect(gradingPolicyOf('宽松')).toBe('lenient')
  })

  it('未知或缺省一律按一般处理', () => {
    expect(gradingPolicyOf(undefined)).toBe('normal')
    expect(gradingPolicyOf('随便')).toBe('normal')
  })
})

describe('解析模型返回', () => {
  const payload = { success: true, questions: [{ title: '题干', type: 'single' }] }

  it('接受 JSON 字符串', () => {
    const result = parseAiQuestions(JSON.stringify(payload))

    expect(result.success).toBe(true)
    expect(result.questions).toHaveLength(1)
  })

  it('接受已经反序列化的对象', () => {
    expect(parseAiQuestions(payload).success).toBe(true)
  })

  it('清掉模型输出的非法转义，不让整批题目失败', () => {
    // \( 不是合法的 JSON 转义，直接 JSON.parse 会抛错
    const raw = '{"success":true,"questions":[{"title":"求 f\\(x\\) 的值"}]}'

    const result = parseAiQuestions(raw)

    expect(result.success).toBe(true)
    expect(result.questions[0].title).toBe('求 f(x) 的值')
  })

  it('保留合法转义，换行不会被吃掉', () => {
    const raw = '{"success":true,"questions":[{"title":"第一行\\n第二行"}]}'

    expect(parseAiQuestions(raw).questions[0].title).toBe('第一行\n第二行')
  })

  it('空返回与坏 JSON 给出可读提示而不是抛错', () => {
    const blanks = [null, undefined, '', '这不是 JSON', '{"questions":']

    blanks.forEach((raw) => {
      const result = parseAiQuestions(raw)
      expect(result.success).toBe(false)
      expect(result.questions).toEqual([])
      expect(result.message).toBeTruthy()
    })
  })

  it('题目为空时透传模型给的原因', () => {
    const result = parseAiQuestions({ success: false, questions: [], message: '主题过于宽泛' })

    expect(result.success).toBe(false)
    expect(result.message).toBe('主题过于宽泛')
  })
})

describe('单题规范化', () => {
  it('补上唯一 id 与默认分值', () => {
    const a = normalizeAiQuestion({ title: '甲' })
    const b = normalizeAiQuestion({ title: '乙' })

    expect(a.id).toBeTruthy()
    expect(a.id).not.toBe(b.id)
    expect(a.score).toBe(5)
  })

  it('已有的 id 与分值不被覆盖', () => {
    const q = normalizeAiQuestion({ id: 'keep-me', score: 10, title: '甲' })

    expect(q.id).toBe('keep-me')
    expect(q.score).toBe(10)
  })

  it('题型别名与大小写统一', () => {
    expect(normalizeAiQuestion({ type: 'single_choice' }).type).toBe('single')
    expect(normalizeAiQuestion({ type: 'ESSAY' }).type).toBe('short')
    expect(normalizeAiQuestion({ type: undefined }).type).toBe('single')
  })

  it('保留程序题等本系统特有题型，不会被压成单选', () => {
    expect(normalizeAiQuestion({ type: 'prog_blank' }).type).toBe('prog_blank')
    expect(normalizeAiQuestion({ type: 'code_explain' }).type).toBe('code_explain')
  })

  it('剥掉选项自带的字母前缀', () => {
    const q = normalizeAiQuestion({ type: 'single', options: ['A. 甲', 'B、乙', 'c：丙', '丁'] })

    expect(q.options).toEqual(['甲', '乙', '丙', '丁'])
  })

  it('只给主观题写判分策略', () => {
    const policy = 'strict'

    expect(normalizeAiQuestion({ type: 'short' }, { policy }).gradingPolicy).toBe('strict')
    expect(normalizeAiQuestion({ type: 'blank' }, { policy }).gradingPolicy).toBe('strict')
    expect(normalizeAiQuestion({ type: 'single' }, { policy }).gradingPolicy).toBeUndefined()
  })

  it('填空题清空选项并去掉答案首尾空白', () => {
    const q = normalizeAiQuestion({ type: 'blank', options: ['A. 甲', 'B. 乙'], answer: '  final  ' })

    expect(q.options).toEqual([])
    expect(q.answer).toBe('final')
  })

  it('按需清理题干里的答案标记与选项残留', () => {
    const messy = { type: 'single', title: '以下哪个正确？（A） A. 甲 B. 乙' }

    expect(normalizeAiQuestion(messy, { tidyTitle: true }).title).toBe('以下哪个正确？')
    expect(normalizeAiQuestion(messy).title).toBe(messy.title)
  })

  it('不改动传入的原始题目对象', () => {
    const origin = { type: 'single_choice', options: ['A. 甲'] }
    normalizeAiQuestion(origin, { tidyTitle: true })

    expect(origin.type).toBe('single_choice')
    expect(origin.options).toEqual(['A. 甲'])
  })
})

describe('填空题配额补齐', () => {
  const single = (answer, options = ['甲', '乙', '丙']) => ({
    type: 'single',
    title: '题干',
    options,
    answer
  })

  it('数量已够时原样返回', () => {
    const questions = [{ type: 'blank', answer: 'x' }, single(0)]

    expect(backfillBlanks(questions, 1, 'normal').filter((q) => q.type === 'blank')).toHaveLength(1)
  })

  it('不足时把单选题改造成填空题，答案取正确选项的文字', () => {
    const result = backfillBlanks([single(1)], 1, 'strict')

    expect(result[0].type).toBe('blank')
    expect(result[0].answer).toBe('乙')
    expect(result[0].options).toEqual([])
    expect(result[0].gradingPolicy).toBe('strict')
  })

  it('只补到配额为止，多余的单选题保持原样', () => {
    const result = backfillBlanks([single(0), single(1), single(2)], 2, 'normal')

    expect(result.filter((q) => q.type === 'blank')).toHaveLength(2)
    expect(result[2].type).toBe('single')
  })

  it('答案越界或缺失的单选题不参与改造，避免造出错误答案', () => {
    const broken = [single(9), single(null), single(0, [])]

    expect(backfillBlanks(broken, 3, 'normal').every((q) => q.type === 'single')).toBe(true)
  })

  it('正确选项是空字符串时跳过，不会生成没有答案的填空题', () => {
    expect(backfillBlanks([single(0, ['   ', '乙'])], 1, 'normal')[0].type).toBe('single')
  })

  it('配额为 0 或没有可改造题目时不报错', () => {
    expect(backfillBlanks([], 3, 'normal')).toEqual([])
    expect(backfillBlanks([single(0)], 0, 'normal')[0].type).toBe('single')
  })

  it('不改动传入的数组与题目对象', () => {
    const origin = [single(0)]
    backfillBlanks(origin, 1, 'normal')

    expect(origin[0].type).toBe('single')
    expect(origin[0].options).toEqual(['甲', '乙', '丙'])
  })
})

describe('出题结果整体处理', () => {
  const raw = JSON.stringify({
    success: true,
    questions: [
      { type: 'single_choice', title: '题一', options: ['A. 甲', 'B. 乙'], answer: 0 },
      { type: 'essay', title: '题二' }
    ]
  })

  it('解析、规范化、补配额一次完成', () => {
    const result = buildAiQuestions(raw, { scoringStandard: '严格', blankCount: 1 })

    expect(result.success).toBe(true)
    expect(result.questions.map((q) => q.type)).toEqual(['blank', 'short'])
    expect(result.questions[0].answer).toBe('甲')
    expect(result.questions.every((q) => q.gradingPolicy === 'strict')).toBe(true)
  })

  it('不要求填空题时不做改造', () => {
    const result = buildAiQuestions(raw, { scoringStandard: '一般' })

    expect(result.questions.map((q) => q.type)).toEqual(['single', 'short'])
  })

  it('解析失败时直接返回失败，不产出半成品题目', () => {
    const result = buildAiQuestions('坏数据', { blankCount: 2 })

    expect(result.success).toBe(false)
    expect(result.questions).toEqual([])
  })
})

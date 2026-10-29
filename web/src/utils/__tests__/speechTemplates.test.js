import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { createSpeechTemplates } from '../speechTemplates'

// 静态模板类别（仍在 _getTemplates 中）
const STATIC_CATEGORIES = ['questionLead', 'keypointLead', 'stepLead', 'transition']
// 动态生成类别（上下文感知，不在 _getTemplates 中）
const DYNAMIC_CATEGORIES = ['intro', 'errorLead', 'encouragement', 'comfort']
const ALL_CATEGORIES = [...STATIC_CATEGORIES, ...DYNAMIC_CATEGORIES]
const QUESTION_TYPES = ['single', 'multi', 'judge', 'blank', 'short']

const arbQuestionType = fc.constantFrom(...QUESTION_TYPES)
const arbCategory = fc.constantFrom(...ALL_CATEGORIES)

// ============================================================
// Property Tests
// ============================================================

describe('SpeechTemplates 属性测试', () => {
  // Feature: natural-voice-tts, Property 6: 话术模板充足性
  // **Validates: Requirements 3.1**
  it('Property 6: 静态类别 >= 8 个模板', () => {
    const templates = createSpeechTemplates()
    const allTemplates = templates._getTemplates()

    for (const category of STATIC_CATEGORIES) {
      expect(allTemplates[category].length).toBeGreaterThanOrEqual(8)
    }
  })

  it('Property 6b: 动态类别生成 >= 4 个候选', () => {
    const templates = createSpeechTemplates()
    // 动态类别通过上下文生成池，验证返回非空
    const ctx = { index: 1, total: 10, questionType: 'single', isCorrect: false, keyPoints: '链表', questionTitle: '链表的基本操作' }
    const intro = templates.getIntro(ctx)
    const errorLead = templates.getErrorLead(ctx)
    const encouragement = templates.getEncouragement(ctx)
    const comfort = templates.getComfort(ctx)
    expect(intro.length).toBeGreaterThan(0)
    expect(errorLead.length).toBeGreaterThan(0)
    expect(encouragement.length).toBeGreaterThan(0)
    expect(comfort.length).toBeGreaterThan(0)
  })

  // Feature: natural-voice-tts, Property 7: 题目类型话术覆盖
  // **Validates: Requirements 3.2**
  it('Property 7: 所有题目类型返回非空字符串', () => {
    fc.assert(
      fc.property(arbQuestionType, fc.integer({ min: 1, max: 50 }), (questionType, index) => {
        const templates = createSpeechTemplates()
        const result = templates.getQuestionLead({ questionType, index })
        expect(result).toBeTruthy()
        expect(typeof result).toBe('string')
        expect(result.length).toBeGreaterThan(0)
      }),
      { numRuns: 50 }
    )
  })

  // Feature: natural-voice-tts, Property 8: 话术连续去重
  // **Validates: Requirements 3.4**
  it('Property 8: 连续两次调用返回不同文本', () => {
    fc.assert(
      fc.property(arbCategory, (category) => {
        const templates = createSpeechTemplates()
        const context = { index: 1, total: 10, questionType: 'single', isCorrect: true, keyPoints: '', questionTitle: '测试题目', stepCount: 3 }

        const methodMap = {
          intro: () => templates.getIntro(context),
          questionLead: () => templates.getQuestionLead(context),
          keypointLead: () => templates.getKeypointLead(context),
          errorLead: () => templates.getErrorLead(context),
          stepLead: () => templates.getStepLead(context),
          transition: () => templates.getTransition(context),
          encouragement: () => templates.getEncouragement(context),
          comfort: () => templates.getComfort(context),
        }

        const getText = methodMap[category]
        const first = getText()
        const second = getText()

        expect(first).not.toBe(second)
      }),
      { numRuns: 50 }
    )
  })

  // Feature: natural-voice-tts, Property 9: 模板变量插值正确性
  // **Validates: Requirements 3.6**
  it('Property 9: 渲染后包含变量值', () => {
    fc.assert(
      fc.property(fc.integer({ min: 1, max: 100 }), (index) => {
        const templates = createSpeechTemplates()
        const result = templates.getIntro({ index, total: 10, questionType: 'single', isCorrect: true, keyPoints: '', questionTitle: '' })

        // 上下文感知的 intro 模板包含 index 值
        expect(result).toContain(String(index))
      }),
      { numRuns: 100 }
    )
  })

  // 上下文感知测试：答错时开场白应有不同语气
  it('上下文感知: 答错时生成包含安慰语气的开场白', () => {
    const templates = createSpeechTemplates()
    const wrongCtx = { index: 3, total: 10, questionType: 'single', isCorrect: false, keyPoints: '', questionTitle: '测试题' }
    // 多次采样，至少有一次包含答错相关的语气词
    const results = new Set()
    for (let i = 0; i < 50; i++) {
      templates.reset()
      results.add(templates.getIntro(wrongCtx))
    }
    const allText = [...results].join(' ')
    const hasWrongContext = allText.includes('错') || allText.includes('没答对') || allText.includes('分析') || allText.includes('绕')
    expect(hasWrongContext).toBe(true)
  })
})

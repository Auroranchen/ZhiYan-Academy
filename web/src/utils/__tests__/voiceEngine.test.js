import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { scoreVoice, createVoiceEngine } from '../voiceEngine'

/**
 * 构造一个模拟的 SpeechSynthesisVoice 对象
 */
function mockVoice(overrides = {}) {
  return {
    name: overrides.name ?? 'Test Voice',
    lang: overrides.lang ?? 'zh-CN',
    localService: overrides.localService ?? true,
    voiceURI: overrides.voiceURI ?? 'test',
    default: overrides.default ?? false,
  }
}

/**
 * fast-check arbitrary: 生成随机的模拟语音对象
 */
const arbVoiceName = fc.string({ minLength: 1, maxLength: 40 })

const arbLang = fc.constantFrom('zh-CN', 'zh-TW', 'zh-HK', 'zh', 'en-US', 'ja-JP', 'ko-KR')

const arbChineseLang = fc.constantFrom('zh-CN', 'zh-TW', 'zh-HK', 'zh')

const arbMockVoice = fc.record({
  name: arbVoiceName,
  lang: arbLang,
  localService: fc.boolean(),
  voiceURI: fc.constant('test'),
  default: fc.boolean(),
})

const arbChineseVoice = fc.record({
  name: arbVoiceName,
  lang: arbChineseLang,
  localService: fc.boolean(),
  voiceURI: fc.constant('test'),
  default: fc.boolean(),
})


// ============================================================
// Property Tests
// ============================================================

describe('VoiceEngine 属性测试', () => {
  // Feature: natural-voice-tts, Property 1: 语音评分一致性
  // **Validates: Requirements 1.2**
  it('Property 1: 含 Neural 关键词的语音评分高于不含的（同等其他条件）', () => {
    fc.assert(
      fc.property(arbMockVoice, (baseVoice) => {
        // 确保 base 名称不含 Neural/Natural/Premium
        const cleanName = baseVoice.name
          .replace(/Neural/gi, '')
          .replace(/Natural/gi, '')
          .replace(/Premium/gi, '')

        const withoutNeural = mockVoice({ ...baseVoice, name: cleanName })
        const withNeural = mockVoice({ ...baseVoice, name: cleanName + ' Neural' })

        const scoreWithout = scoreVoice(withoutNeural)
        const scoreWith = scoreVoice(withNeural)

        expect(scoreWith).toBeGreaterThan(scoreWithout)
      }),
      { numRuns: 100 }
    )
  })

  // Feature: natural-voice-tts, Property 2: 最高分语音选择
  // **Validates: Requirements 1.3**
  it('Property 2: primaryVoice 的评分等于列表最高分', () => {
    fc.assert(
      fc.property(
        fc.array(arbChineseVoice, { minLength: 1, maxLength: 20 }),
        (voices) => {
          const engine = createVoiceEngine()
          engine._initWithVoices(voices)

          const primary = engine.getPrimaryVoice()
          if (!primary) return // 无中文语音时跳过

          const scored = engine.getScoredVoices()
          const maxScore = Math.max(...scored.map(s => s.score))
          const primaryScore = scoreVoice(primary)

          expect(primaryScore).toBe(maxScore)
        }
      ),
      { numRuns: 100 }
    )
  })

  // Feature: natural-voice-tts, Property 3: 主辅语音区分
  // **Validates: Requirements 1.6**
  it('Property 3: primary 和 secondary 为不同语音且 primary 评分 >= secondary', () => {
    fc.assert(
      fc.property(
        fc.array(arbChineseVoice, { minLength: 2, maxLength: 20 }),
        (voices) => {
          const engine = createVoiceEngine()
          engine._initWithVoices(voices)

          const primary = engine.getPrimaryVoice()
          const secondary = engine.getSecondaryVoice()

          if (!primary || !secondary) return // 不足两个语音时跳过

          // primary 和 secondary 不是同一个对象
          expect(primary).not.toBe(secondary)

          // primary 评分 >= secondary 评分
          expect(scoreVoice(primary)).toBeGreaterThanOrEqual(scoreVoice(secondary))
        }
      ),
      { numRuns: 100 }
    )
  })
})


// ============================================================
// Unit Tests
// ============================================================

describe('VoiceEngine 单元测试', () => {
  // Requirements 1.4: 无中文语音返回 null
  it('空语音列表时 getPrimaryVoice 返回 null', () => {
    const engine = createVoiceEngine()
    engine._initWithVoices([])
    expect(engine.getPrimaryVoice()).toBeNull()
    expect(engine.getSecondaryVoice()).toBeNull()
  })

  it('仅有非中文语音时 getPrimaryVoice 返回 null', () => {
    const engine = createVoiceEngine()
    engine._initWithVoices([
      mockVoice({ lang: 'en-US', name: 'English Voice' }),
      mockVoice({ lang: 'ja-JP', name: 'Japanese Voice' }),
    ])
    expect(engine.getPrimaryVoice()).toBeNull()
  })

  // Requirements 1.5: 仅一个中文语音时 secondary 行为
  it('仅一个中文语音时 secondary 返回 null', () => {
    const engine = createVoiceEngine()
    engine._initWithVoices([
      mockVoice({ lang: 'zh-CN', name: 'Chinese Voice' }),
    ])
    expect(engine.getPrimaryVoice()).not.toBeNull()
    expect(engine.getSecondaryVoice()).toBeNull()
  })

  it('getVoice("secondary") 在无 secondary 时回退到 primary', () => {
    const engine = createVoiceEngine()
    engine._initWithVoices([
      mockVoice({ lang: 'zh-CN', name: 'Only Chinese' }),
    ])
    const voice = engine.getVoice('secondary')
    expect(voice).toBe(engine.getPrimaryVoice())
  })

  it('getVoice("primary") 返回主讲语音', () => {
    const engine = createVoiceEngine()
    engine._initWithVoices([
      mockVoice({ lang: 'zh-CN', name: 'Voice A' }),
      mockVoice({ lang: 'zh-CN', name: 'Voice B Neural' }),
    ])
    expect(engine.getVoice('primary')).toBe(engine.getPrimaryVoice())
  })

  it('isReady 初始为 false', () => {
    const engine = createVoiceEngine()
    expect(engine.isReady()).toBe(false)
  })

  it('getScoredVoices 返回按分数降序排列的列表', () => {
    const engine = createVoiceEngine()
    engine._initWithVoices([
      mockVoice({ lang: 'zh-CN', name: 'Basic' }),
      mockVoice({ lang: 'zh-CN', name: 'Neural Premium' }),
      mockVoice({ lang: 'zh', name: 'Partial' }),
    ])
    const scored = engine.getScoredVoices()
    for (let i = 1; i < scored.length; i++) {
      expect(scored[i - 1].score).toBeGreaterThanOrEqual(scored[i].score)
    }
  })
})

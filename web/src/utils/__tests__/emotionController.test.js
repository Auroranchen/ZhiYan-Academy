import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { createEmotionController, EMOTION_PRESETS, SEGMENT_TYPES } from '../emotionController'

const ALL_SEGMENT_TYPES = Object.values(SEGMENT_TYPES)
const arbSegmentType = fc.constantFrom(...ALL_SEGMENT_TYPES)

// ============================================================
// Property Tests
// ============================================================

describe('EmotionController 属性测试', () => {
  // Feature: natural-voice-tts, Property 4: 情感参数范围正确性
  // **Validates: Requirements 2.2, 2.3, 2.4, 2.5, 2.6, 2.8**
  it('Property 4: 所有 Segment_Type 的参数在预定义范围内', () => {
    fc.assert(
      fc.property(arbSegmentType, (segmentType) => {
        const controller = createEmotionController()
        const params = controller.getParams(segmentType)
        const preset = EMOTION_PRESETS[segmentType]

        // rate 在范围内
        expect(params.rate).toBeGreaterThanOrEqual(preset.rate[0])
        expect(params.rate).toBeLessThanOrEqual(preset.rate[1])

        // pitch 在范围内
        expect(params.pitch).toBeGreaterThanOrEqual(preset.pitch[0])
        expect(params.pitch).toBeLessThanOrEqual(preset.pitch[1])

        // volume 在范围内
        expect(params.volume).toBeGreaterThanOrEqual(preset.volume[0])
        expect(params.volume).toBeLessThanOrEqual(preset.volume[1])

        // pauseAfter 在 300-1400ms 之间
        const pause = controller.getPauseAfter(segmentType)
        expect(pause).toBeGreaterThanOrEqual(300)
        expect(pause).toBeLessThanOrEqual(1400)
      }),
      { numRuns: 200 }
    )
  })

  // Feature: natural-voice-tts, Property 5: 情感参数随机波动
  // **Validates: Requirements 2.7**
  it('Property 5: 多次调用产生不同值（100次中至少2个不同）', () => {
    fc.assert(
      fc.property(arbSegmentType, (segmentType) => {
        const controller = createEmotionController()
        const rates = new Set()

        for (let i = 0; i < 100; i++) {
          const params = controller.getParams(segmentType)
          rates.add(params.rate)
        }

        // 至少有 2 个不同的 rate 值
        expect(rates.size).toBeGreaterThanOrEqual(2)
      }),
      { numRuns: 20 }
    )
  })
})

// ============================================================
// Unit Tests
// ============================================================

describe('EmotionController 单元测试', () => {
  it('未知段落类型使用默认配置不报错', () => {
    const controller = createEmotionController()
    const params = controller.getParams('unknown_type')
    expect(params.rate).toBeGreaterThan(0)
    expect(params.pitch).toBeGreaterThan(0)
    expect(params.volume).toBeGreaterThan(0)
  })

  it('question 和 intro 使用 primary 角色', () => {
    const controller = createEmotionController()
    expect(controller.getVoiceRole('question')).toBe('primary')
    expect(controller.getVoiceRole('intro')).toBe('primary')
  })

  it('error_analysis、keypoint、step 使用 secondary 角色', () => {
    const controller = createEmotionController()
    expect(controller.getVoiceRole('error_analysis')).toBe('secondary')
    expect(controller.getVoiceRole('keypoint')).toBe('secondary')
    expect(controller.getVoiceRole('step')).toBe('secondary')
  })
})

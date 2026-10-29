import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { typewriterEffect, springAnimation, SPRING_PRESETS } from '../animationUtils'

// Chinese punctuation characters
const PUNCTUATION_CHARS = [
  '\u3002', // 。
  '\uff0c', // ，
  '\uff01', // ！
  '\uff1f', // ？
  '\uff1b', // ；
  '\uff1a', // ：
  '\u3001', // 、
  '\u2026', // …
  '\u2014', // —
  '\u201c', // "
  '\u201d', // "
  '\u2018', // '
  '\u2019', // '
  '\uff08', // （
  '\uff09', // ）
]

const PUNCT_SET = new Set(PUNCTUATION_CHARS)

// Generate text with at least one punctuation and one normal char
const arbTextWithPunctuation = fc.tuple(
  fc.string({ minLength: 1, maxLength: 10 }),
  fc.constantFrom(...PUNCTUATION_CHARS)
).map(([text, punct]) => {
  // Ensure we have at least one non-punctuation char
  const clean = Array.from(text).filter(c => !PUNCT_SET.has(c)).join('') || 'abc'
  return clean + punct + clean
})

// ============================================================
// Property Tests
// ============================================================

describe('AnimationUtils property tests', () => {
  // Feature: natural-voice-tts, Property 11
  // **Validates: Requirements 5.2**
  it('Property 11: punctuation delay > normal char delay', () => {
    fc.assert(
      fc.property(arbTextWithPunctuation, (text) => {
        const chars = typewriterEffect(text)

        const punctDelays = chars.filter(c => PUNCT_SET.has(c.char)).map(c => c.delay)
        const normalDelays = chars.filter(c => !PUNCT_SET.has(c.char)).map(c => c.delay)

        if (punctDelays.length === 0 || normalDelays.length === 0) return

        const minPunctDelay = Math.min(...punctDelays)
        const maxNormalDelay = Math.max(...normalDelays)

        expect(minPunctDelay).toBeGreaterThan(maxNormalDelay)
      }),
      { numRuns: 100 }
    )
  })
})

// ============================================================
// Unit Tests
// ============================================================

describe('AnimationUtils unit tests', () => {
  it('empty text returns empty array', () => {
    expect(typewriterEffect('')).toEqual([])
    expect(typewriterEffect(null)).toEqual([])
    expect(typewriterEffect(undefined)).toEqual([])
  })

  it('spring returns 0 at t=0 and 1 at t=1', () => {
    for (const preset of Object.values(SPRING_PRESETS)) {
      const fn = springAnimation(preset)
      expect(fn(0)).toBe(0)
      expect(fn(1)).toBe(1)
    }
  })

  it('smooth spring values stay in reasonable range', () => {
    const fn = springAnimation(SPRING_PRESETS.smooth)
    for (let t = 0; t <= 1; t += 0.1) {
      const val = fn(t)
      expect(val).toBeGreaterThanOrEqual(-0.1)
      expect(val).toBeLessThanOrEqual(1.1)
    }
  })
})

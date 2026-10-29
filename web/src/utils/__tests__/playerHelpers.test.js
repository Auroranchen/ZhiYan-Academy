import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { getTeacherAction, shouldShowCode } from '../playerHelpers'

const VALID_SEGMENT_TYPES = ['intro', 'question', 'error_analysis', 'step', 'keypoint', 'transition', 'summary']

/**
 * Property 2: Pause state overrides action
 * Validates: Requirements 1.6
 */
describe('getTeacherAction - Property Tests', () => {
  it('should always return idle when isPaused is true, for any segmentType', () => {
    const arbSegmentType = fc.constantFrom(...VALID_SEGMENT_TYPES)

    fc.assert(
      fc.property(arbSegmentType, (segmentType) => {
        expect(getTeacherAction(segmentType, true)).toBe('idle')
      }),
      { numRuns: 100 }
    )
  })
})

describe('getTeacherAction - Unit Tests', () => {
  it('intro -> teaching', () => {
    expect(getTeacherAction('intro', false)).toBe('teaching')
  })
  it('question -> teaching', () => {
    expect(getTeacherAction('question', false)).toBe('teaching')
  })
  it('error_analysis -> thinking', () => {
    expect(getTeacherAction('error_analysis', false)).toBe('thinking')
  })
  it('step -> writing', () => {
    expect(getTeacherAction('step', false)).toBe('writing')
  })
  it('keypoint -> pointing', () => {
    expect(getTeacherAction('keypoint', false)).toBe('pointing')
  })
  it('transition -> encouraging', () => {
    expect(getTeacherAction('transition', false)).toBe('encouraging')
  })
  it('summary -> encouraging', () => {
    expect(getTeacherAction('summary', false)).toBe('encouraging')
  })
  it('unknown type -> idle', () => {
    expect(getTeacherAction('unknown', false)).toBe('idle')
  })
})

/**
 * Property 3: Code panel display decision
 * Validates: Requirements 4.1
 */
describe('shouldShowCode - Property Tests', () => {
  it('should return true when hasReferenceCode is true regardless of type/lang', () => {
    const arbType = fc.constantFrom(1, 2, 3)
    const arbLang = fc.constantFrom('c_cpp', 'java', 'python', 'c', 'javascript', 'sql', 'plaintext')

    fc.assert(
      fc.property(arbType, arbLang, (type, lang) => {
        expect(shouldShowCode(type, lang, true)).toBe(true)
      }),
      { numRuns: 100 }
    )
  })

  it('should return true iff type=2 and language is not plaintext when no referenceCode', () => {
    const arbType = fc.constantFrom(1, 2, 3)
    const arbLang = fc.constantFrom('c_cpp', 'java', 'python', 'c', 'javascript', 'sql', 'plaintext')

    fc.assert(
      fc.property(arbType, arbLang, (type, lang) => {
        const result = shouldShowCode(type, lang, false)
        const expected = type === 2 && lang !== 'plaintext'
        expect(result).toBe(expected)
      }),
      { numRuns: 100 }
    )
  })
})

describe('shouldShowCode - Unit Tests', () => {
  it('type=1 without referenceCode = false', () => {
    expect(shouldShowCode(1, 'java', false)).toBe(false)
  })
  it('type=2 + plaintext without referenceCode = false', () => {
    expect(shouldShowCode(2, 'plaintext', false)).toBe(false)
  })
  it('type=2 + java = true', () => {
    expect(shouldShowCode(2, 'java', false)).toBe(true)
  })
  it('hasReferenceCode=true always returns true', () => {
    expect(shouldShowCode(1, 'plaintext', true)).toBe(true)
  })
})

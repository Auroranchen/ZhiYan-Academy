import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { mapCourseToLanguage, LANGUAGE_RULES } from '../languageMapper'

/**
 * Property 1: Course language mapping correctness
 * Validates: Requirements 3.1, 3.2, 3.3
 */
describe('mapCourseToLanguage - Property Tests', () => {
  // Known keyword-language pairs for generating test inputs
  const KEYWORD_PAIRS = [
    { keyword: '\u6570\u636E\u7ED3\u6784', expected: 'c_cpp' },
    { keyword: '\u7B97\u6CD5', expected: 'c_cpp' },
    { keyword: 'Java', expected: 'java' },
    { keyword: 'Python', expected: 'python' },
    { keyword: 'C\u8BED\u8A00', expected: 'c' },
    { keyword: 'Web', expected: 'javascript' },
    { keyword: '\u524D\u7AEF', expected: 'javascript' },
    { keyword: '\u6570\u636E\u5E93', expected: 'sql' },
  ]

  it('should return correct language for course names containing known keywords', () => {
    /**
     * Validates: Requirements 3.1
     */
    const arbKeywordPair = fc.constantFrom(...KEYWORD_PAIRS)
    const arbPrefix = fc.string({ minLength: 0, maxLength: 10 })
    const arbSuffix = fc.string({ minLength: 0, maxLength: 10 })

    fc.assert(
      fc.property(arbKeywordPair, arbPrefix, arbSuffix, (pair, prefix, suffix) => {
        // Filter out prefixes/suffixes that accidentally contain other keywords
        const fullName = prefix + pair.keyword + suffix
        // Find the first matching rule for this full name
        const firstMatch = LANGUAGE_RULES.find(r => r.pattern.test(fullName))
        const result = mapCourseToLanguage(fullName)
        expect(result).toBe(firstMatch.language)
      }),
      { numRuns: 100 }
    )
  })

  it('should return plaintext for strings without any known keywords', () => {
    /**
     * Validates: Requirements 3.2
     */
    // Generate strings from safe characters that won't match any rule
    const safeChars = fc.array(
      fc.constantFrom('a', 'b', 'x', 'z', '1', '2', '3', ' ', '-'),
      { minLength: 1, maxLength: 20 }
    ).map(arr => arr.join(''))

    fc.assert(
      fc.property(safeChars, (name) => {
        // Double-check none of the rules match
        const anyMatch = LANGUAGE_RULES.some(r => r.pattern.test(name))
        if (!anyMatch) {
          expect(mapCourseToLanguage(name)).toBe('plaintext')
        }
      }),
      { numRuns: 100 }
    )
  })

  it('should return first matching rule when multiple keywords present', () => {
    /**
     * Validates: Requirements 3.3
     */
    // Generate course names with multiple keywords
    const arbTwoKeywords = fc.tuple(
      fc.constantFrom(...KEYWORD_PAIRS),
      fc.constantFrom(...KEYWORD_PAIRS)
    ).filter(([a, b]) => a.keyword !== b.keyword)

    fc.assert(
      fc.property(arbTwoKeywords, ([first, second]) => {
        const combined = first.keyword + second.keyword
        const result = mapCourseToLanguage(combined)
        // Should match the first rule that applies
        const firstMatch = LANGUAGE_RULES.find(r => r.pattern.test(combined))
        expect(result).toBe(firstMatch.language)
      }),
      { numRuns: 100 }
    )
  })
})

describe('mapCourseToLanguage - Unit Tests', () => {
  it('should return plaintext for null', () => {
    expect(mapCourseToLanguage(null)).toBe('plaintext')
  })

  it('should return plaintext for undefined', () => {
    expect(mapCourseToLanguage(undefined)).toBe('plaintext')
  })

  it('should return plaintext for empty string', () => {
    expect(mapCourseToLanguage('')).toBe('plaintext')
  })

  it('should return plaintext for whitespace-only string', () => {
    expect(mapCourseToLanguage('   ')).toBe('plaintext')
  })

  it('should return plaintext for non-string input', () => {
    expect(mapCourseToLanguage(123)).toBe('plaintext')
    expect(mapCourseToLanguage(true)).toBe('plaintext')
  })

  it('should map specific course names correctly', () => {
    expect(mapCourseToLanguage('\u6570\u636E\u7ED3\u6784\u4E0E\u7B97\u6CD5')).toBe('c_cpp')
    expect(mapCourseToLanguage('Java\u7A0B\u5E8F\u8BBE\u8BA1')).toBe('java')
    expect(mapCourseToLanguage('Python\u57FA\u7840')).toBe('python')
    expect(mapCourseToLanguage('C\u8BED\u8A00\u7A0B\u5E8F\u8BBE\u8BA1')).toBe('c')
    expect(mapCourseToLanguage('Web\u524D\u7AEF\u5F00\u53D1')).toBe('javascript')
    expect(mapCourseToLanguage('\u6570\u636E\u5E93\u539F\u7406')).toBe('sql')
    expect(mapCourseToLanguage('\u9AD8\u7B49\u6570\u5B66')).toBe('plaintext')
  })

  it('should prioritize earlier rules (e.g. Java\u6570\u636E\u7ED3\u6784 -> c_cpp)', () => {
    // \u6570\u636E\u7ED3\u6784 rule comes before Java rule
    expect(mapCourseToLanguage('Java\u6570\u636E\u7ED3\u6784')).toBe('c_cpp')
  })
})

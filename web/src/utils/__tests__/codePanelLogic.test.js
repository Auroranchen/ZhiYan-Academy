import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { highlightCode } from '../codePanelLogic'

/**
 * Strip HTML tags from a string
 */
function stripTags(html) {
  return html.replace(/<[^>]*>/g, '')
}

/**
 * Reverse HTML entity escaping
 */
function unescapeHtml(str) {
  return str
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
}

const SUPPORTED_LANGS = ['c_cpp', 'c', 'java', 'python', 'javascript', 'sql']

/**
 * Property 4: Syntax highlighting preserves original content
 * Validates: Requirements 4.5
 */
describe('highlightCode - Property Tests', () => {
  it('highlighted output stripped of tags contains original text content', () => {
    const arbLang = fc.constantFrom(...SUPPORTED_LANGS)
    // Generate code-like strings with safe ASCII chars
    const arbCode = fc.string({ minLength: 1, maxLength: 200 })
      .filter(s => s.trim().length > 0)

    fc.assert(
      fc.property(arbLang, arbCode, (lang, code) => {
        const highlighted = highlightCode(code, lang)
        const stripped = unescapeHtml(stripTags(highlighted))
        // Every non-whitespace character from original should appear in stripped output
        const originalChars = code.replace(/\s+/g, '')
        const strippedChars = stripped.replace(/\s+/g, '')
        expect(strippedChars).toContain(originalChars)
      }),
      { numRuns: 100 }
    )
  })
})

describe('highlightCode - Unit Tests', () => {
  it('returns empty string for empty input', () => {
    expect(highlightCode('', 'java')).toBe('')
    expect(highlightCode(null, 'java')).toBe('')
    expect(highlightCode(undefined, 'java')).toBe('')
  })

  it('highlights Java keywords', () => {
    const result = highlightCode('public class Main {}', 'java')
    expect(result).toContain('hl-keyword')
    expect(result).toContain('public')
    expect(result).toContain('class')
  })

  it('highlights Python comments', () => {
    const result = highlightCode('x = 1 # comment', 'python')
    expect(result).toContain('hl-comment')
  })

  it('highlights strings', () => {
    const result = highlightCode('let s = "hello"', 'javascript')
    expect(result).toContain('hl-string')
  })

  it('highlights numbers', () => {
    const result = highlightCode('int x = 42;', 'c_cpp')
    expect(result).toContain('hl-number')
  })

  it('escapes HTML in code', () => {
    const result = highlightCode('<div>test</div>', 'javascript')
    expect(result).toContain('&lt;')
    expect(result).toContain('&gt;')
    expect(result).not.toContain('<div>')
  })
})

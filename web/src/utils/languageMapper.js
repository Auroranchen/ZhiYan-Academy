/**
 * \u8BFE\u7A0B\u8BED\u8A00\u6620\u5C04\u5668 - \u6839\u636E\u8BFE\u7A0B\u540D\u79F0\u63A8\u65AD\u7F16\u7A0B\u8BED\u8A00\u6807\u8BC6\u7B26
 * \u7528\u4E8E CodePanel \u7EC4\u4EF6\u786E\u5B9A\u8BED\u6CD5\u9AD8\u4EAE\u89C4\u5219
 */

/**
 * \u6620\u5C04\u89C4\u5219\u6570\u7EC4\uFF0C\u6309\u4F18\u5148\u7EA7\u6392\u5E8F\uFF0C\u7B2C\u4E00\u4E2A\u5339\u914D\u7684\u89C4\u5219\u751F\u6548
 * @type {Array<{pattern: RegExp, language: string}>}
 */
export const LANGUAGE_RULES = [
  { pattern: /\u6570\u636E\u7ED3\u6784|\u7B97\u6CD5/, language: 'c_cpp' },
  { pattern: /Java/i, language: 'java' },
  { pattern: /Python/i, language: 'python' },
  { pattern: /C\u8BED\u8A00/, language: 'c' },
  { pattern: /Web|\u524D\u7AEF/i, language: 'javascript' },
  { pattern: /\u6570\u636E\u5E93/, language: 'sql' },
]

/**
 * \u6839\u636E\u8BFE\u7A0B\u540D\u79F0\u63A8\u65AD\u7F16\u7A0B\u8BED\u8A00
 * @param {string} courseName - \u8BFE\u7A0B\u540D\u79F0
 * @returns {string} \u8BED\u8A00\u6807\u8BC6\u7B26\uFF08\u5982 'c_cpp', 'java', 'plaintext'\uFF09
 */
export function mapCourseToLanguage(courseName) {
  if (!courseName || typeof courseName !== 'string') return 'plaintext'
  const trimmed = courseName.trim()
  if (!trimmed) return 'plaintext'

  for (const rule of LANGUAGE_RULES) {
    if (rule.pattern.test(trimmed)) {
      return rule.language
    }
  }
  return 'plaintext'
}

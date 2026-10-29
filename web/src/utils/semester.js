/**
 * 学期工具：根据日期自动推算当前学年学期
 *
 * 学期格式：`${startYear}-${endYear}-${term}`，如 "2025-2026-2"
 * 规则（中国高校常见学历）：
 *   - 9 月 ~ 次年 1 月：第一学期（秋季）
 *   - 2 月 ~ 8 月：第二学期（春季）
 *   - 学年从 9 月起算，例如 2025-09 至 2026-08 都属于 2025-2026 学年
 */

/**
 * 推算指定日期所属的学期字符串
 * @param {Date} [date=new Date()] 参考日期
 * @returns {string} 形如 "2025-2026-2"
 */
export function getCurrentSemester(date = new Date()) {
  const y = date.getFullYear()
  const month = date.getMonth() + 1 // 1-12

  let startYear
  let term
  if (month >= 9) {
    // 9~12 月：本年开学的第一学期
    startYear = y
    term = 1
  } else if (month === 1) {
    // 1 月：上一年开学学年的第一学期
    startYear = y - 1
    term = 1
  } else {
    // 2~8 月：上一年开学学年的第二学期
    startYear = y - 1
    term = 2
  }
  return `${startYear}-${startYear + 1}-${term}`
}

/**
 * 推算指定日期所属的学年字符串
 * @param {Date} [date=new Date()] 参考日期
 * @returns {string} 形如 "2025-2026"
 */
export function getCurrentAcademicYear(date = new Date()) {
  const semester = getCurrentSemester(date)
  const [startYear, endYear] = semester.split('-')
  return `${startYear}-${endYear}`
}

/**
 * 生成最近若干个学年的学期下拉选项（从新到旧）
 * @param {number} [yearCount=3] 包含的学年数量
 * @param {Date} [date=new Date()] 参考日期
 * @returns {Array<{label: string, value: string}>}
 */
export function getSemesterOptions(yearCount = 3, date = new Date()) {
  const current = getCurrentSemester(date)
  const currentStartYear = Number(current.split('-')[0])
  const options = []
  for (let i = 0; i < yearCount; i++) {
    const s = currentStartYear - i
    const e = s + 1
    options.push({ label: `${s}-${e}第二学期`, value: `${s}-${e}-2` })
    options.push({ label: `${s}-${e}第一学期`, value: `${s}-${e}-1` })
  }
  return options
}

/**
 * 生成最近若干个学年下拉选项（从新到旧）
 * @param {number} [yearCount=4] 包含的学年数量
 * @param {Date} [date=new Date()] 参考日期
 * @returns {Array<{label: string, value: string}>}
 */
export function getAcademicYearOptions(yearCount = 4, date = new Date()) {
  const current = getCurrentAcademicYear(date)
  const currentStartYear = Number(current.split('-')[0])
  const options = []
  for (let i = 0; i < yearCount; i++) {
    const s = currentStartYear - i
    const e = s + 1
    options.push({ label: `${s}-${e}学年`, value: `${s}-${e}` })
  }
  return options
}

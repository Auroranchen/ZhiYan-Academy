import { describe, expect, it } from 'vitest'
import {
  getAcademicYearOptions,
  getCurrentAcademicYear,
  getCurrentSemester,
  getSemesterOptions
} from '@/utils/semester'

/** 用本地时区构造日期，避免 UTC 解析把月份挪走 */
const on = (year, month, day = 15) => new Date(year, month - 1, day)

describe('学期推算', () => {
  it('9 月开学进入新学年第一学期', () => {
    expect(getCurrentSemester(on(2025, 9))).toBe('2025-2026-1')
    expect(getCurrentSemester(on(2025, 12))).toBe('2025-2026-1')
  })

  it('1 月仍属上一年开学的第一学期，不是新学年', () => {
    expect(getCurrentSemester(on(2026, 1))).toBe('2025-2026-1')
  })

  it('2 月到 8 月是第二学期', () => {
    expect(getCurrentSemester(on(2026, 2))).toBe('2025-2026-2')
    expect(getCurrentSemester(on(2026, 8))).toBe('2025-2026-2')
  })

  describe('月份边界', () => {
    it('8 月末到 9 月初跨学年', () => {
      expect(getCurrentSemester(on(2025, 8, 31))).toBe('2024-2025-2')
      expect(getCurrentSemester(on(2025, 9, 1))).toBe('2025-2026-1')
    })

    it('1 月末到 2 月初跨学期但不跨学年', () => {
      expect(getCurrentSemester(on(2026, 1, 31))).toBe('2025-2026-1')
      expect(getCurrentSemester(on(2026, 2, 1))).toBe('2025-2026-2')
    })

    it('12 月末到 1 月初跨自然年但学期不变', () => {
      expect(getCurrentSemester(on(2025, 12, 31))).toBe('2025-2026-1')
      expect(getCurrentSemester(on(2026, 1, 1))).toBe('2025-2026-1')
    })
  })

  it('全年 12 个月都能推算出合法学期', () => {
    for (let month = 1; month <= 12; month += 1) {
      expect(getCurrentSemester(on(2025, month))).toMatch(/^\d{4}-\d{4}-[12]$/)
    }
  })
})

describe('学年推算', () => {
  it('去掉学期序号后即为学年', () => {
    expect(getCurrentAcademicYear(on(2025, 10))).toBe('2025-2026')
    expect(getCurrentAcademicYear(on(2026, 3))).toBe('2025-2026')
  })
})

describe('下拉选项', () => {
  it('每个学年两个学期，第二学期在前（更接近当下）', () => {
    const options = getSemesterOptions(2, on(2026, 3))

    expect(options.map((o) => o.value)).toEqual([
      '2025-2026-2',
      '2025-2026-1',
      '2024-2025-2',
      '2024-2025-1'
    ])
  })

  it('学年选项从新到旧', () => {
    expect(getAcademicYearOptions(3, on(2025, 10)).map((o) => o.value)).toEqual([
      '2025-2026',
      '2024-2025',
      '2023-2024'
    ])
  })

  it('当前学期一定出现在选项里，否则筛选框会显示空值', () => {
    for (let month = 1; month <= 12; month += 1) {
      const date = on(2025, month)
      const values = getSemesterOptions(3, date).map((o) => o.value)
      expect(values, `${month} 月的当前学期不在选项中`).toContain(getCurrentSemester(date))
    }
  })

  it('标签与取值一一对应，不会串行', () => {
    getSemesterOptions(2, on(2026, 3)).forEach(({ label, value }) => {
      const [start, end, term] = value.split('-')
      expect(label).toBe(`${start}-${end}第${term === '1' ? '一' : '二'}学期`)
    })
  })
})

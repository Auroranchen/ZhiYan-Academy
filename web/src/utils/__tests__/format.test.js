import { describe, expect, it } from 'vitest'
import {
  countdown,
  dateKey,
  formatDate,
  formatFileSize,
  formatNumber,
  formatPercent,
  formatScore,
  timeAgo,
  toDate
} from '@/utils/format'

describe('日期解析', () => {
  it('接受后端的 yyyy-MM-dd HH:mm:ss 格式', () => {
    const date = toDate('2026-03-15 14:30:00')

    expect(date.getFullYear()).toBe(2026)
    expect(date.getMonth()).toBe(2)
    expect(date.getDate()).toBe(15)
    expect(date.getHours()).toBe(14)
  })

  it('接受 ISO 的 T 分隔写法', () => {
    // Safari 不接受空格分隔，工具内部会统一替换，两种写法结果必须一致
    expect(toDate('2026-03-15T14:30:00').getTime()).toBe(toDate('2026-03-15 14:30:00').getTime())
  })

  it('接受时间戳与 Date 实例', () => {
    const now = Date.now()
    expect(toDate(now).getTime()).toBe(now)
    expect(toDate(new Date(now)).getTime()).toBe(now)
  })

  it('无效输入一律返回 null，调用方只需判一次空', () => {
    expect(toDate(null)).toBeNull()
    expect(toDate('')).toBeNull()
    expect(toDate(undefined)).toBeNull()
    expect(toDate('不是日期')).toBeNull()
    expect(toDate(new Date('x'))).toBeNull()
  })
})

describe('日期格式化', () => {
  const sample = '2026-03-05 09:07:03'

  it('四种格式各取所需', () => {
    expect(formatDate(sample)).toBe('2026-03-05')
    expect(formatDate(sample, 'minute')).toBe('2026-03-05 09:07')
    expect(formatDate(sample, 'datetime')).toBe('2026-03-05 09:07:03')
    expect(formatDate(sample, 'time')).toBe('09:07')
  })

  it('个位数月日时分补零', () => {
    expect(formatDate('2026-01-02 03:04:05', 'datetime')).toBe('2026-01-02 03:04:05')
  })

  it('无效值显示占位符而不是 Invalid Date', () => {
    expect(formatDate(null)).toBe('—')
    expect(formatDate('乱码', 'minute')).toBe('—')
    expect(formatDate(null, 'date', '未设置')).toBe('未设置')
  })
})

describe('按天分组的键', () => {
  it('输出本地时区的 yyyy-MM-dd', () => {
    expect(dateKey('2026-03-05 09:00:00')).toBe('2026-03-05')
  })

  it('东八区凌晨不会被算到前一天', () => {
    // 用 toISOString 会转成 UTC，00:30 会退成前一天，这是它存在的理由
    const midnight = new Date(2026, 2, 5, 0, 30)
    expect(dateKey(midnight)).toBe('2026-03-05')
    expect(dateKey(midnight)).not.toBe(midnight.toISOString().slice(0, 10))
  })

  it('深夜时段同样落在当天', () => {
    expect(dateKey(new Date(2026, 2, 5, 23, 59))).toBe('2026-03-05')
  })

  it('无效值返回空串，分组时会落到一个空键而不是崩溃', () => {
    expect(dateKey(null)).toBe('')
    expect(dateKey('乱码')).toBe('')
  })
})

describe('相对时间', () => {
  const minute = 60 * 1000

  it('按距今时长选择不同粒度', () => {
    const now = Date.now()
    expect(timeAgo(now - 30 * 1000)).toBe('刚刚')
    expect(timeAgo(now - 5 * minute)).toBe('5 分钟前')
    expect(timeAgo(now - 3 * 60 * minute)).toBe('3 小时前')
    expect(timeAgo(now - 2 * 24 * 60 * minute)).toBe('2 天前')
  })

  it('超过一周显示具体日期', () => {
    expect(timeAgo(Date.now() - 10 * 24 * 60 * minute)).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('未来时间显示具体时刻，不说「-5 分钟前」', () => {
    expect(timeAgo(Date.now() + 60 * minute)).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/)
  })
})

describe('截止倒计时', () => {
  const hour = 60 * 60 * 1000

  it('没有截止时间视为不限时', () => {
    expect(countdown(null)).toEqual({ expired: false, urgent: false, text: '不限时' })
  })

  it('已过期', () => {
    const result = countdown(Date.now() - hour)
    expect(result.expired).toBe(true)
    expect(result.text).toBe('已截止')
  })

  it('不足一天标记为紧急', () => {
    const result = countdown(Date.now() + 5 * hour)
    expect(result.urgent).toBe(true)
    expect(result.text).toContain('小时')
  })

  it('超过一天同时显示天与小时，且不算紧急', () => {
    const result = countdown(Date.now() + 50 * hour)
    expect(result.urgent).toBe(false)
    expect(result.text).toMatch(/剩余 2 天 \d+ 小时/)
  })
})

describe('数值格式化', () => {
  it('文件大小按量级换算', () => {
    expect(formatFileSize(512)).toBe('512 B')
    expect(formatFileSize(2048)).toBe('2.0 KB')
    expect(formatFileSize(5 * 1024 * 1024)).toBe('5.0 MB')
    expect(formatFileSize(3 * 1024 ** 3)).toBe('3.0 GB')
  })

  it('文件大小超过 100 时省略小数，避免过长', () => {
    expect(formatFileSize(200 * 1024)).toBe('200 KB')
  })

  it('无效大小显示占位符', () => {
    expect(formatFileSize(null)).toBe('—')
    expect(formatFileSize('abc')).toBe('—')
  })

  it('分数去掉多余的 .0', () => {
    expect(formatScore(90)).toBe('90')
    expect(formatScore(88.5)).toBe('88.5')
    expect(formatScore(88.46)).toBe('88.5')
  })

  it('0 分是有效分数，不能当成空值', () => {
    expect(formatScore(0)).toBe('0')
    expect(formatNumber(0)).toBe('0')
  })

  it('空值显示占位符', () => {
    expect(formatScore(null)).toBe('—')
    expect(formatScore('')).toBe('—')
    expect(formatNumber(undefined)).toBe('—')
    expect(formatPercent(null)).toBe('—')
  })

  it('千分位与百分比', () => {
    expect(formatNumber(1234567)).toBe('1,234,567')
    expect(formatPercent(66.666)).toBe('66.7%')
    expect(formatPercent(66.666, 0)).toBe('67%')
  })
})

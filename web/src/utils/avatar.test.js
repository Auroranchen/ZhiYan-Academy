import { describe, it, expect } from 'vitest'
import { getAvatarColor, getFirstChar, resolveAvatarUrl } from './avatar'

describe('getAvatarColor', () => {
  it('should return a valid hex color string for a normal name', () => {
    const color = getAvatarColor('张三')
    expect(color).toMatch(/^#[0-9a-f]{6}$/)
  })

  it('should be deterministic - same name always produces same color', () => {
    const color1 = getAvatarColor('李四')
    const color2 = getAvatarColor('李四')
    expect(color1).toBe(color2)
  })

  it('should produce different colors for different names', () => {
    const color1 = getAvatarColor('张三')
    const color2 = getAvatarColor('李四')
    expect(color1).not.toBe(color2)
  })

  it('should return fallback color for null input', () => {
    const color = getAvatarColor(null)
    expect(color).toBe('#94a3b8')
  })

  it('should return fallback color for undefined input', () => {
    const color = getAvatarColor(undefined)
    expect(color).toBe('#94a3b8')
  })

  it('should return fallback color for empty string', () => {
    const color = getAvatarColor('')
    expect(color).toBe('#94a3b8')
  })

  it('should return fallback color for whitespace-only string', () => {
    const color = getAvatarColor('   ')
    expect(color).toBe('#94a3b8')
  })

  it('should handle single character names', () => {
    const color = getAvatarColor('A')
    expect(color).toMatch(/^#[0-9a-f]{6}$/)
  })

  it('should handle English names', () => {
    const color = getAvatarColor('John Doe')
    expect(color).toMatch(/^#[0-9a-f]{6}$/)
  })

  it('should only produce colors from the predefined palette', () => {
    const palette = [
      '#3b82f6', '#0ea5e9', '#14b8a6', '#10b981', '#f59e0b', '#f97316',
      '#ef4444', '#64748b', '#06b6d4', '#84cc16', '#ea580c', '#0284c7'
    ]
    const names = ['张三', '李四', '王五', 'Alice', 'Bob', 'Charlie', '赵六']
    for (const name of names) {
      const color = getAvatarColor(name)
      expect(palette).toContain(color)
    }
  })
})

describe('getFirstChar', () => {
  it('should return the first character of a Chinese name', () => {
    expect(getFirstChar('张三')).toBe('张')
  })

  it('should return the first character of an English name', () => {
    expect(getFirstChar('John')).toBe('J')
  })

  it('should return empty string for null input', () => {
    expect(getFirstChar(null)).toBe('')
  })

  it('should return empty string for undefined input', () => {
    expect(getFirstChar(undefined)).toBe('')
  })

  it('should return empty string for empty string input', () => {
    expect(getFirstChar('')).toBe('')
  })

  it('should return empty string for whitespace-only string', () => {
    expect(getFirstChar('   ')).toBe('')
  })

  it('should trim leading whitespace before extracting first char', () => {
    expect(getFirstChar('  张三')).toBe('张')
  })

  it('should handle single character names', () => {
    expect(getFirstChar('A')).toBe('A')
  })
})

describe('resolveAvatarUrl', () => {
  it('保持完整 URL 不变', () => {
    expect(resolveAvatarUrl('https://cdn.example.com/a.png', 'student', 7)).toBe(
      'https://cdn.example.com/a.png'
    )
  })

  it('已带 API 前缀的地址不重复拼接', () => {
    expect(resolveAvatarUrl('/ZhiYanAcademy/avatar/get/student/7', 'student', 7)).toBe(
      '/ZhiYanAcademy/avatar/get/student/7'
    )
  })

  it('上传目录改走受控头像接口，避免部署带 contextPath 时 404', () => {
    expect(resolveAvatarUrl('/upload/avatar/a.jpg', 'student', 7)).toBe(
      '/ZhiYanAcademy/avatar/get/student/7'
    )
  })

  it('其他绝对路径补上 API 前缀', () => {
    expect(resolveAvatarUrl('/static/a.png', 'teacher', 3)).toBe('/ZhiYanAcademy/static/a.png')
  })

  it('没有 avatar 或是历史相对路径时回退到头像接口', () => {
    expect(resolveAvatarUrl('', 'admin', 1)).toBe('/ZhiYanAcademy/avatar/get/admin/1')
    expect(resolveAvatarUrl('a.png', 'admin', 1)).toBe('/ZhiYanAcademy/avatar/get/admin/1')
  })

  it('缺少角色或 id 时返回空串，由调用方展示文字头像', () => {
    expect(resolveAvatarUrl('', 'student', null)).toBe('')
    expect(resolveAvatarUrl(null, null, null)).toBe('')
  })
})

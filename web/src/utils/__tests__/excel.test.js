import { describe, expect, it, vi } from 'vitest'
import { buildCsv, importRows } from '@/utils/excel'

const lines = (csv) => csv.split('\n')

describe('CSV 拼装', () => {
  it('表头在首行，数据依次跟随', () => {
    const csv = buildCsv({
      headers: ['学号', '姓名'],
      rows: [
        ['2021001', '张三'],
        ['2021002', '李四']
      ]
    })

    expect(lines(csv)).toEqual(['"学号","姓名"', '"2021001","张三"', '"2021002","李四"'])
  })

  it('含逗号的内容不会被拆成两列', () => {
    const csv = buildCsv({ headers: ['备注'], rows: [['迟交,已扣分']] })

    expect(lines(csv)[1]).toBe('"迟交,已扣分"')
  })

  it('引号按 CSV 规范双写', () => {
    expect(lines(buildCsv({ headers: ['题目'], rows: [['他说"好"']] }))[1]).toBe('"他说""好"""')
  })

  it('换行留在单元格内，不会顶出一整行', () => {
    const csv = buildCsv({ headers: ['评语'], rows: [['第一行\n第二行']] })

    expect(csv).toContain('"第一行\n第二行"')
    expect(lines(csv)).toHaveLength(3) // 表头 + 被换行拆开的两段，仍属同一个字段
  })

  it('空值导出为空单元格而不是 null 字样', () => {
    expect(lines(buildCsv({ headers: ['分数'], rows: [[null], [undefined], ['']] }))).toEqual([
      '"分数"',
      '""',
      '""',
      '""'
    ])
  })

  describe('公式注入防护', () => {
    it('以 = + @ 开头的内容会被转成纯文本', () => {
      const rows = [['=1+1'], ['+SUM(A1)'], ['@import'], ['\tcmd']]
      const csv = buildCsv({ headers: ['姓名'], rows })

      lines(csv)
        .slice(1)
        .forEach((line) => expect(line.startsWith(`"'`)).toBe(true))
    })

    it('拦下经典的命令执行载荷', () => {
      const payload = '=cmd|\' /C calc\'!A0'
      expect(lines(buildCsv({ headers: ['姓名'], rows: [[payload]] }))[1]).toContain(`"'=cmd`)
    })

    it('负数照常导出，不会被加引号变成文本', () => {
      expect(lines(buildCsv({ headers: ['分差'], rows: [[-5], [-3.5], [0], [88]] }))).toEqual([
        '"分差"',
        '"-5"',
        '"-3.5"',
        '"0"',
        '"88"'
      ])
    })

    it('以减号开头的文字仍然要防护', () => {
      expect(lines(buildCsv({ headers: ['备注'], rows: [['-未提交']] }))[1]).toBe(`"'-未提交"`)
    })
  })
})

describe('批量导入', () => {
  it('全部成功时不产生错误信息', async () => {
    const handler = vi.fn().mockResolvedValue()
    const result = await importRows([{ name: '甲' }, { name: '乙' }], handler)

    expect(result).toEqual({ total: 2, success: 2, fail: 0, errors: [] })
    expect(handler).toHaveBeenCalledTimes(2)
  })

  it('单行失败不打断后续导入', async () => {
    const handler = vi.fn().mockImplementation((row) => {
      if (row.name === '乙') throw new Error('学号重复')
      return Promise.resolve()
    })

    const result = await importRows([{ name: '甲' }, { name: '乙' }, { name: '丙' }], handler)

    expect(result.success).toBe(2)
    expect(result.fail).toBe(1)
    expect(handler).toHaveBeenCalledTimes(3)
  })

  it('错误信息带上 Excel 里的真实行号（表头占第 1 行）', async () => {
    const handler = vi.fn().mockRejectedValue(new Error('学号重复'))
    const result = await importRows([{}], handler)

    expect(result.errors).toEqual(['第 2 行：学号重复'])
  })

  it('异常没有 message 时给出兜底文案', async () => {
    const result = await importRows([{}], () => Promise.reject(new Error()))

    expect(result.errors[0]).toBe('第 2 行：导入失败')
  })

  it('空表返回全零，调用方无需额外判空', async () => {
    expect(await importRows([], vi.fn())).toEqual({ total: 0, success: 0, fail: 0, errors: [] })
  })

  it('逐行串行处理，避免并发写库互相覆盖', async () => {
    const order = []
    const handler = vi.fn().mockImplementation(async (row) => {
      order.push(`start-${row.id}`)
      await new Promise((resolve) => setTimeout(resolve, row.id === 1 ? 20 : 0))
      order.push(`end-${row.id}`)
    })

    await importRows([{ id: 1 }, { id: 2 }], handler)

    expect(order).toEqual(['start-1', 'end-1', 'start-2', 'end-2'])
  })
})

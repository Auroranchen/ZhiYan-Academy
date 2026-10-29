/**
 * Excel 导入导出
 *
 * 学生、教师等名单页各自实现过一遍 XLSX 装载、模板下载与表格解析，逻辑完全一致。
 *
 * 注意：SheetJS 目前仍按需从 CDN 载入。内网或离线部署环境下需要把 XLSX_CDN 指向
 * 自建静态资源地址，或改为将 xlsx 作为项目依赖打包。
 */

const XLSX_CDN = 'https://cdn.jsdelivr.net/npm/xlsx@0.18.5/dist/xlsx.full.min.js'

let loadingPromise = null

/** 按需加载 SheetJS，并发调用只会真正加载一次 */
export const ensureXlsx = () => {
  if (window.XLSX) return Promise.resolve(window.XLSX)
  if (loadingPromise) return loadingPromise

  loadingPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = XLSX_CDN
    script.onload = () => resolve(window.XLSX)
    script.onerror = () => {
      loadingPromise = null
      reject(new Error('Excel 组件加载失败，请检查网络后重试'))
    }
    document.head.appendChild(script)
  })

  return loadingPromise
}

/**
 * 生成并下载导入模板
 * @param {object} options
 * @param {string[]} options.headers 表头
 * @param {Array<string|number>} [options.sample] 示例行
 * @param {string} options.fileName 下载文件名，含扩展名
 * @param {string} [options.sheetName]
 */
export const downloadTemplate = async ({ headers, sample = [], fileName, sheetName = 'Sheet1' }) => {
  const XLSX = await ensureXlsx()
  const rows = sample.length ? [headers, sample] : [headers]
  const sheet = XLSX.utils.aoa_to_sheet(rows)
  sheet['!cols'] = headers.map((h) => ({ wch: Math.max(10, String(h).length + 4) }))
  const book = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(book, sheet, sheetName)
  XLSX.writeFile(book, fileName)
}

/**
 * 读取上传文件的首个工作表
 * @param {File} file
 * @returns {Promise<object[]>} 以表头为键的行对象数组
 */
export const readSheetRows = async (file) => {
  const XLSX = await ensureXlsx()
  const buffer = await file.arrayBuffer()
  const book = XLSX.read(buffer, { type: 'array' })
  const sheet = book.Sheets[book.SheetNames[0]]
  return XLSX.utils.sheet_to_json(sheet, { defval: '' })
}

/**
 * 把表头与数据行拼成 CSV 文本。
 *
 * 字段一律加引号，逗号、引号、换行都能安全落进单元格。
 * 另外，以 = + - @ 开头的内容会被 Excel 当公式执行（CSV 注入），
 * 所以非数字内容前面补一个单引号，强制按纯文本打开。
 *
 * @param {object} options
 * @param {string[]} options.headers
 * @param {Array<Array<string|number>>} options.rows
 * @returns {string}
 */
export const buildCsv = ({ headers, rows }) => {
  const escape = (value) => {
    let text = String(value ?? '')
    if (/^[=+\-@\t\r]/.test(text) && !/^-?\d+(\.\d+)?$/.test(text)) {
      text = `'${text}`
    }
    return `"${text.replace(/"/g, '""')}"`
  }

  return [headers, ...rows].map((line) => line.map(escape).join(',')).join('\n')
}

/**
 * 导出 CSV 并触发下载。
 *
 * 不走 SheetJS，纯前端拼装即可，也就没有 CDN 依赖。
 * 开头的 BOM 是必需的，否则 Excel 打开中文会乱码。
 *
 * @param {object} options
 * @param {string[]} options.headers
 * @param {Array<Array<string|number>>} options.rows
 * @param {string} options.fileName 含扩展名
 */
export const downloadCsv = ({ headers, rows, fileName }) => {
  const blob = new Blob([`\ufeff${buildCsv({ headers, rows })}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

/**
 * 逐行导入并汇总结果。行级失败不中断整体导入。
 * @param {object[]} rows
 * @param {(row: object, index: number) => Promise<void>} handler 单行处理，抛错即视为该行失败
 * @returns {Promise<{ total: number, success: number, fail: number, errors: string[] }>}
 */
export const importRows = async (rows, handler) => {
  const result = { total: rows.length, success: 0, fail: 0, errors: [] }

  for (const [index, row] of rows.entries()) {
    // 表头占第 1 行，数据从第 2 行开始，报错时给用户真实行号
    const lineNo = index + 2
    try {
      await handler(row, index)
      result.success += 1
    } catch (e) {
      result.fail += 1
      result.errors.push(`第 ${lineNo} 行：${e?.message || '导入失败'}`)
    }
  }

  return result
}

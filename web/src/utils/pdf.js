const escapeHtml = (value) => {
  if (value === null || value === undefined) {
    return ''
  }
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

const buildRows = (rows = []) => rows.map((row) => {
  const label = escapeHtml(row.label || '')
  const value = row.isHtml ? (row.value || '') : escapeHtml(row.value || '')
  return `
    <tr>
      <td style="border: 1px solid #e4e7ed; padding: 8px 10px; width: 140px; background: #f5f7fa; color: #606266;">${label}</td>
      <td style="border: 1px solid #e4e7ed; padding: 8px 10px; color: #303133;">${value}</td>
    </tr>
  `
}).join('')

const buildMeta = (items = []) => {
  const meta = items
    .filter(item => item && item.value !== undefined && item.value !== null && item.value !== '')
    .map(item => `${escapeHtml(item.label || '')}: ${escapeHtml(item.value)}`)
    .join(' | ')
  if (!meta) {
    return ''
  }
  return `
    <div style="font-size: 12px; color: #909399; text-align: center; margin-bottom: 14px;">
      ${meta}
    </div>
  `
}

export const buildHomeworkPdfHtml = ({
  title,
  metaItems,
  infoTitle,
  infoRows,
  answerTitle,
  answerHtml,
  scoreTitle,
  scoreRows,
  footerText,
  isCodeContent
}) => {
  // 如果是代码/文档内容，用pre标签格式化
  const formattedAnswerHtml = isCodeContent 
    ? `<pre style="margin: 0; white-space: pre-wrap; word-break: break-word; font-family: Consolas, Monaco, monospace; font-size: 12px; line-height: 1.5; background: #f8f9fa; padding: 12px; border-radius: 4px;">${escapeHtml(answerHtml || '')}</pre>`
    : (answerHtml || '')
  
  return `
  <div style="padding: 20px; font-family: 'Microsoft YaHei', sans-serif; color: #303133; font-size: 14px;">
    <style>
      @media print {
        .pdf-section { page-break-inside: avoid; }
        table { page-break-inside: avoid; }
        tr { page-break-inside: avoid; }
      }
    </style>
    <div style="text-align: center; font-size: 18px; font-weight: 600; margin-bottom: 6px;">
      ${escapeHtml(title || '')}
    </div>
    ${buildMeta(metaItems)}
    ${infoTitle ? `<div style="font-weight: 600; margin: 16px 0 8px; font-size: 14px;">${escapeHtml(infoTitle)}</div>` : ''}
    ${infoRows && infoRows.length ? `<table style="width: 100%; border-collapse: collapse; margin-bottom: 16px;">${buildRows(infoRows)}</table>` : ''}
    ${answerTitle ? `<div style="font-weight: 600; margin: 16px 0 8px; font-size: 14px;">${escapeHtml(answerTitle)}</div>` : ''}
    ${answerHtml ? `<div class="pdf-section" style="border: 1px solid #e4e7ed; border-radius: 4px; padding: 12px; background: #fafafa; margin-bottom: 16px; line-height: 1.6; word-break: break-word;">${formattedAnswerHtml}</div>` : ''}
    ${scoreTitle ? `<div style="font-weight: 600; margin: 16px 0 8px; font-size: 14px;">${escapeHtml(scoreTitle)}</div>` : ''}
    ${scoreRows && scoreRows.length ? `<table style="width: 100%; border-collapse: collapse; margin-bottom: 16px;">${buildRows(scoreRows)}</table>` : ''}
    ${footerText ? `<div style="margin-top: 16px; padding-top: 10px; border-top: 1px dashed #dcdfe6; font-size: 12px; color: #606266; text-align: right;">${escapeHtml(footerText)}</div>` : ''}
  </div>
`
}

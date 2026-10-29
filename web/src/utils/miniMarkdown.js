/**
 * 极简 Markdown 渲染（仅用于展示 AI 生成的报告，避免引入额外依赖）。
 * 支持：标题(#~###)、无序/有序列表、加粗、行内代码、分隔线、段落。
 * 会先做 HTML 转义，防止 XSS。
 */
function escapeHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function inline(text) {
  return text
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+?)`/g, '<code>$1</code>')
}

export function renderMarkdown(md) {
  if (!md) return ''
  const lines = escapeHtml(md).split(/\r?\n/)
  const html = []
  let listType = null // 'ul' | 'ol'
  let inCode = false
  let codeBuf = []

  const closeList = () => {
    if (listType) {
      html.push(`</${listType}>`)
      listType = null
    }
  }

  for (const raw of lines) {
    const line = raw.trimEnd()
    // 围栏代码块 ```
    if (/^\s*```/.test(line)) {
      if (inCode) {
        html.push(`<pre><code>${codeBuf.join('\n')}</code></pre>`)
        codeBuf = []
        inCode = false
      } else {
        closeList()
        inCode = true
      }
      continue
    }
    if (inCode) { codeBuf.push(raw); continue }
    if (!line.trim()) {
      closeList()
      continue
    }
    let m
    if ((m = line.match(/^######\s+(.*)/))) { closeList(); html.push(`<h6>${inline(m[1])}</h6>`); continue }
    if ((m = line.match(/^#####\s+(.*)/))) { closeList(); html.push(`<h5>${inline(m[1])}</h5>`); continue }
    if ((m = line.match(/^####\s+(.*)/))) { closeList(); html.push(`<h4>${inline(m[1])}</h4>`); continue }
    if ((m = line.match(/^###\s+(.*)/))) { closeList(); html.push(`<h3>${inline(m[1])}</h3>`); continue }
    if ((m = line.match(/^##\s+(.*)/))) { closeList(); html.push(`<h2>${inline(m[1])}</h2>`); continue }
    if ((m = line.match(/^#\s+(.*)/))) { closeList(); html.push(`<h1>${inline(m[1])}</h1>`); continue }
    if (/^[-*_]{3,}$/.test(line.trim())) { closeList(); html.push('<hr/>'); continue }
    if ((m = line.match(/^\s*[-*+]\s+(.*)/))) {
      if (listType !== 'ul') { closeList(); listType = 'ul'; html.push('<ul>') }
      html.push(`<li>${inline(m[1])}</li>`); continue
    }
    if ((m = line.match(/^\s*\d+[.)]\s+(.*)/))) {
      if (listType !== 'ol') { closeList(); listType = 'ol'; html.push('<ol>') }
      html.push(`<li>${inline(m[1])}</li>`); continue
    }
    closeList()
    html.push(`<p>${inline(line.trim())}</p>`)
  }
  if (inCode && codeBuf.length) html.push(`<pre><code>${codeBuf.join('\n')}</code></pre>`)
  closeList()
  return html.join('\n')
}

export default renderMarkdown

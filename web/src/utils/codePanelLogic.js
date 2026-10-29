/**
 * 轻量级语法高亮工具（基于正则分词）
 * 无外部依赖，保持打包体积小
 */

// 各语言关键字表
const KEYWORDS = {
  c_cpp: ['int', 'char', 'void', 'struct', 'return', 'if', 'else', 'for', 'while', 'do', 'switch', 'case', 'break', 'continue', 'typedef', 'sizeof', 'const', 'static', 'unsigned', 'long', 'short', 'double', 'float', '#include', 'printf', 'scanf', 'malloc', 'free', 'NULL'],
  c: ['int', 'char', 'void', 'struct', 'return', 'if', 'else', 'for', 'while', 'do', 'switch', 'case', 'break', 'continue', 'typedef', 'sizeof', 'const', 'static', '#include', 'printf', 'scanf', 'malloc', 'free', 'NULL'],
  java: ['public', 'private', 'protected', 'class', 'interface', 'extends', 'implements', 'void', 'int', 'String', 'boolean', 'return', 'if', 'else', 'for', 'while', 'new', 'import', 'package', 'static', 'final', 'try', 'catch', 'throw', 'throws', 'null', 'true', 'false', 'this', 'super'],
  python: ['def', 'class', 'return', 'if', 'elif', 'else', 'for', 'while', 'import', 'from', 'as', 'in', 'not', 'and', 'or', 'is', 'with', 'try', 'except', 'finally', 'raise', 'pass', 'break', 'continue', 'lambda', 'yield', 'print', 'True', 'False', 'None', 'self'],
  javascript: ['function', 'const', 'let', 'var', 'return', 'if', 'else', 'for', 'while', 'class', 'import', 'export', 'default', 'from', 'new', 'this', 'async', 'await', 'try', 'catch', 'throw', 'null', 'undefined', 'true', 'false', 'typeof', 'instanceof'],
  sql: ['SELECT', 'FROM', 'WHERE', 'INSERT', 'INTO', 'UPDATE', 'DELETE', 'CREATE', 'TABLE', 'ALTER', 'DROP', 'JOIN', 'LEFT', 'RIGHT', 'INNER', 'ON', 'AND', 'OR', 'NOT', 'NULL', 'AS', 'ORDER', 'BY', 'GROUP', 'HAVING', 'LIMIT', 'VALUES', 'SET', 'INDEX', 'PRIMARY', 'KEY', 'FOREIGN'],
}

/** HTML特殊字符转义 */
function escapeHtml(str) {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/**
 * 将代码拆分为词法单元（token），再逐个转义和包装
 * 避免正则匹配到HTML实体内部的问题
 */
function tokenize(code, language) {
  const tokens = []
  const kws = KEYWORDS[language] || []
  const kwSet = new Set(language === 'sql' ? kws.map(k => k.toUpperCase()) : kws)

  let i = 0
  while (i < code.length) {
    // 单行注释 //
    if (code[i] === '/' && code[i + 1] === '/') {
      const end = code.indexOf('\n', i)
      const commentEnd = end === -1 ? code.length : end
      tokens.push({ type: 'comment', text: code.slice(i, commentEnd) })
      i = commentEnd
      continue
    }
    // Python # 注释 / SQL -- 注释
    if ((language === 'python' && code[i] === '#') ||
        (language === 'sql' && code[i] === '-' && code[i + 1] === '-')) {
      const end = code.indexOf('\n', i)
      const commentEnd = end === -1 ? code.length : end
      tokens.push({ type: 'comment', text: code.slice(i, commentEnd) })
      i = commentEnd
      continue
    }
    // 字符串字面量
    if (code[i] === '"' || code[i] === "'") {
      const quote = code[i]
      let j = i + 1
      while (j < code.length && code[j] !== quote) {
        if (code[j] === '\\') j++ // 跳过转义字符
        j++
      }
      j = Math.min(j + 1, code.length) // 包含结束引号
      tokens.push({ type: 'string', text: code.slice(i, j) })
      i = j
      continue
    }
    // 数字
    if (/\d/.test(code[i]) && (i === 0 || /\W/.test(code[i - 1]))) {
      let j = i
      while (j < code.length && /[\d.]/.test(code[j])) j++
      tokens.push({ type: 'number', text: code.slice(i, j) })
      i = j
      continue
    }
    // 单词（可能是关键字）
    if (/[a-zA-Z_#]/.test(code[i])) {
      let j = i
      if (code[i] === '#') j++ // 处理 #include
      while (j < code.length && /[a-zA-Z0-9_]/.test(code[j])) j++
      const word = code.slice(i, j)
      const check = language === 'sql' ? word.toUpperCase() : word
      if (kwSet.has(check)) {
        tokens.push({ type: 'keyword', text: word })
      } else {
        tokens.push({ type: 'plain', text: word })
      }
      i = j
      continue
    }
    // 其他字符
    tokens.push({ type: 'plain', text: code[i] })
    i++
  }
  return tokens
}

/**
 * 对代码字符串进行语法高亮
 * 返回带有 <span> 标签的 HTML 字符串
 * @param {string} code - 原始代码文本
 * @param {string} language - 语言标识符
 * @returns {string} 带语法高亮的 HTML 字符串
 */
export function highlightCode(code, language) {
  if (!code) return ''
  const tokens = tokenize(code, language)
  return tokens.map(t => {
    const escaped = escapeHtml(t.text)
    if (t.type === 'plain') return escaped
    return `<span class="hl-${t.type}">${escaped}</span>`
  }).join('')
}

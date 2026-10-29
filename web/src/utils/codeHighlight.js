/**
 * 轻量代码高亮
 *
 * 只为教师评阅、题目展示这类只读场景服务，不引入完整的高亮库。
 * 输出会直接进 v-safe-html，所以必须先转义再套标签，顺序不能颠倒。
 */

const KEYWORDS = [
  // 修饰符与结构
  'public', 'private', 'protected', 'static', 'final', 'void',
  'class', 'interface', 'extends', 'implements', 'new', 'return',
  // 控制流
  'if', 'else', 'for', 'while', 'do', 'switch', 'case', 'break', 'continue',
  'try', 'catch', 'finally', 'throw', 'throws',
  'import', 'package',
  // 类型
  'int', 'double', 'float', 'boolean', 'char', 'long', 'short', 'byte', 'String',
  // JS / Python 常见
  'var', 'let', 'const', 'function', 'async', 'await',
  'def', 'print', 'self', 'True', 'False', 'None'
]

const COLORS = {
  string: '#ce9178',
  comment: '#6a9955',
  keyword: '#569cd6',
  number: '#b5cea8'
}

const escapeHtml = (text) =>
  text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

const wrap = (color, value) => `<span style="color:${color}">${value}</span>`

/**
 * @param {string} code
 * @returns {string} 带内联颜色的 HTML
 */
export const highlightCode = (code) => {
  if (!code) return ''

  return escapeHtml(String(code))
    // 字符串要最先处理，否则里面的关键字会被误染色
    .replace(/("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')/g, (m) => wrap(COLORS.string, m))
    .replace(/(\/\/.*)/g, (m) => wrap(COLORS.comment, m))
    .replace(new RegExp(`\\b(${KEYWORDS.join('|')})\\b`, 'g'), (m) => wrap(COLORS.keyword, m))
    .replace(/\b(\d+(?:\.\d+)?)\b/g, (m) => wrap(COLORS.number, m))
}

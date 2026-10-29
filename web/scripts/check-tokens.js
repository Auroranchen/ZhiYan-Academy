/**
 * 设计令牌引用检查
 *
 * 拼错的 CSS 变量不会报错，只会静默失效（颜色变透明、间距塌成 0），
 * 在深色模式或某个少见页面上才被发现。这里在 CI 里提前拦下来。
 *
 * 用法：node scripts/check-tokens.js
 */
const fs = require('fs')
const path = require('path')

const SRC = path.join(__dirname, '..', 'src')
const TOKEN_FILES = [
  path.join(SRC, 'assets', 'styles', 'tokens.css'),
  path.join(SRC, 'assets', 'styles', 'design-system.css')
]

const walk = (dir, out = []) => {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) walk(full, out)
    else if (/\.(vue|css|js)$/.test(entry.name)) out.push(full)
  }
  return out
}

const DEFINITION = /['"]?(--zy-[\w-]+)['"]?\s*:/g

const global = new Set()
for (const file of TOKEN_FILES) {
  for (const [, name] of fs.readFileSync(file, 'utf8').matchAll(DEFINITION)) global.add(name)
}

const problems = []
for (const file of walk(SRC)) {
  const text = fs.readFileSync(file, 'utf8')

  // 组件可以用内联 style 就地定义变量（如 QuickLinks 的列宽），这些同样算已定义
  const local = new Set([...text.matchAll(DEFINITION)].map(([, name]) => name))

  text.split('\n').forEach((line, index) => {
    for (const [, name] of line.matchAll(/var\(\s*(--zy-[\w-]+)/g)) {
      if (!global.has(name) && !local.has(name)) {
        problems.push(`${path.relative(SRC, file)}:${index + 1}  ${name}`)
      }
    }
  })
}

if (problems.length) {
  console.error(`发现 ${problems.length} 处未定义的设计令牌：\n`)
  problems.forEach((p) => console.error('  ' + p))
  console.error('\n请在 src/assets/styles/tokens.css 中定义，或改用已有令牌。')
  process.exit(1)
}

console.log(`设计令牌检查通过（已定义 ${global.size} 个）`)

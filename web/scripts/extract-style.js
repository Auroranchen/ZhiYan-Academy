/**
 * 把 SFC 里过长的 <style scoped> 块拆到独立 CSS 文件，改用 <style scoped src="...">。
 * scoped 行为不受影响，但组件文件能回到可浏览的长度。
 *
 * 用法：node scripts/extract-style.js <vue 文件> <目标 css 相对路径> [文件头注释]
 */
const fs = require('fs')
const path = require('path')

const [vueFile, cssRelative, banner = ''] = process.argv.slice(2)

if (!vueFile || !cssRelative) {
  console.error('用法: node scripts/extract-style.js <vue 文件> <目标 css 相对路径> [文件头注释]')
  process.exit(1)
}

const lines = fs.readFileSync(vueFile, 'utf8').split('\n')
const start = lines.findIndex((l) => l.startsWith('<style scoped>'))
const end = lines.findIndex((l) => l.startsWith('</style>'))

if (start < 0 || end < 0) {
  console.error(`${vueFile} 中没有找到 <style scoped> 块`)
  process.exit(1)
}

const css = lines.slice(start + 1, end).join('\n').trim()
const cssPath = path.join(path.dirname(vueFile), cssRelative)

fs.mkdirSync(path.dirname(cssPath), { recursive: true })
fs.writeFileSync(cssPath, `${banner ? `/* ${banner} */\n\n` : ''}${css}\n`)

const tail = lines.slice(end + 1).filter((l) => l.trim() !== '')
const next = [
  ...lines.slice(0, start),
  `<style scoped src="${cssRelative.startsWith('.') ? cssRelative : `./${cssRelative}`}"></style>`,
  ...tail,
  ''
]

fs.writeFileSync(vueFile, next.join('\n'))

console.log(`已拆出 ${css.split('\n').length} 行样式到 ${cssPath}`)
console.log(`${vueFile} 现在 ${next.length} 行`)

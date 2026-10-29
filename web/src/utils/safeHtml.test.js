import { describe, expect, it } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'
import { escapeHtml, sanitizeHtml } from './safeHtml'

describe('safe html boundary', () => {
  it('escapes untrusted chat text before formatting', () => {
    expect(escapeHtml('<img src=x onerror=alert(1)>'))
      .toBe('&lt;img src=x onerror=alert(1)&gt;')
  })

  it('never returns executable raw tags without a DOM sanitizer', () => {
    const cleaned = sanitizeHtml('<script>alert(1)</script><img src=x onerror=alert(2)>')
    expect(cleaned).not.toContain('<script')
    expect(cleaned).not.toContain('<img')
  })

  it('does not allow raw v-html sinks back into source components', () => {
    const sourceRoot = path.resolve(process.cwd(), 'src')
    const files = []
    const walk = (dir) => {
      fs.readdirSync(dir, { withFileTypes: true }).forEach((entry) => {
        const full = path.join(dir, entry.name)
        if (entry.isDirectory()) walk(full)
        else if (/\.(vue|js)$/.test(entry.name)) files.push(full)
      })
    }
    walk(sourceRoot)
    const offenders = files.filter(file => /\bv-html\s*=/.test(fs.readFileSync(file, 'utf8')))
    expect(offenders).toEqual([])
  })
})

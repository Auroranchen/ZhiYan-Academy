import { describe, it, expect } from 'vitest'
import { renderMarkdown } from '../miniMarkdown'

describe('miniMarkdown 基本渲染', () => {
  it('渲染标题、列表与加粗', () => {
    const html = renderMarkdown('## 要点\n- **有序**数组\n- 时间复杂度 `O(log n)`')
    expect(html).toContain('<h2>要点</h2>')
    expect(html).toContain('<ul>')
    expect(html).toContain('<strong>有序</strong>')
    expect(html).toContain('<code>O(log n)</code>')
  })

  it('渲染围栏代码块', () => {
    const html = renderMarkdown('```\nint a = 1;\n```')
    expect(html).toContain('<pre><code>')
    expect(html).toContain('int a = 1;')
  })

  it('空输入返回空串', () => {
    expect(renderMarkdown('')).toBe('')
    expect(renderMarkdown(null)).toBe('')
    expect(renderMarkdown(undefined)).toBe('')
  })
})

describe('miniMarkdown XSS 防护', () => {
  it('转义 script 标签', () => {
    const html = renderMarkdown('<script>alert(1)</script>')
    expect(html).not.toContain('<script>')
    expect(html).toContain('&lt;script&gt;')
  })

  it('转义 img onerror 事件注入', () => {
    const html = renderMarkdown('<img src=x onerror=alert(1)>')
    expect(html).not.toContain('<img')
    expect(html).toContain('&lt;img')
  })

  it('代码块中的 HTML 同样被转义', () => {
    const html = renderMarkdown('```\n<script>steal()</script>\n```')
    expect(html).not.toContain('<script>steal()')
    expect(html).toContain('&lt;script&gt;')
  })

  it('行内代码不引入未转义标签', () => {
    const html = renderMarkdown('运行 `<b>bold</b>` 试试')
    expect(html).not.toContain('<b>bold</b>')
    expect(html).toContain('&lt;b&gt;')
  })
})

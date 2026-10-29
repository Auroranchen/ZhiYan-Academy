import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'

const source = readFileSync(
  new URL('../../views/student/AiExplainHomework.vue', import.meta.url),
  'utf8'
)

describe('AI 作业讲解真实性保护', () => {
  it('失败时不生成或展示 Mock 讲解', () => {
    expect(source).not.toContain('generateMockData')
    expect(source).not.toContain('studentAnswer: \'A\', // Mock')
    expect(source).toContain('AI 暂未生成有效讲解，请稍后重试')
  })

  it('收藏失败时不伪造成功状态', () => {
    expect(source).not.toContain('收藏成功 (演示)')
    expect(source).toContain('收藏失败，请稍后重试')
  })

  it('本地分镜播放不伪装为视频渲染任务', () => {
    expect(source).not.toContain('渲染完成:')
    expect(source).toContain('点击后直接启动本地动画与语音播放')
  })
})

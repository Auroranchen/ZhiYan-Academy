import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'

const source = readFileSync(
  new URL('../../components/AgentWorkbench.vue', import.meta.url),
  'utf8'
)

const profileApi = readFileSync(new URL('../../api/learningProfile.js', import.meta.url), 'utf8')

describe('多智能体协作可视化', () => {
  it('展示协调、评审、复审和讲解动画智能体', () => {
    for (const key of ['coordinate', 'explainer', 'review', 'reviewFinal']) {
      expect(source).toContain(`key: '${key}'`)
    }
  })

  it('资源库能够恢复多模态微课与质量报告', () => {
    expect(source).toContain('p.explainer?.scenes')
    expect(source).toContain('p.qualityReview || collab.finalReview')
    expect(source).toContain('多模态动画微课')
    expect(source).toContain('协作质量报告')
  })

  it('资源包练习形成评估闭环并展示RAG引用', () => {
    // 接口地址收在 API 层，组件只认函数名
    expect(profileApi).toContain('/profile/resource-evaluate')
    expect(source).toContain('evaluateResourcePractice')
    expect(source).toContain('提交练习并更新学习画像')
    expect(source).toContain('adaptivePath')
    expect(source).toContain('知识依据与引用校验')
    expect(source).toContain('citationValidation')
  })
})

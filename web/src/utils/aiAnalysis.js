/**
 * 错题 AI 解析按钮
 *
 * renderExamResultHtml 产出的是 HTML 字符串而非 Vue 组件，解析按钮只能在渲染后
 * 用原生事件绑定。管理端、教师端、学生端三处查看批改结果时都需要这段逻辑。
 */
import { nextTick } from 'vue'
import { analyzeWrongAnswer } from '@/api/ai'
import { escapeHtml } from '@/utils/safeHtml'

/**
 * 为容器内所有 .ai-analysis-btn 绑定点击展开解析。
 * 重复调用是安全的：用 onclick 赋值而非 addEventListener，不会叠加监听。
 *
 * @param {ParentNode} [root] 限定查找范围，默认整个文档
 */
export const bindAiAnalysisButtons = (root = document) => {
  nextTick(() => {
    root.querySelectorAll('.ai-analysis-btn').forEach((button) => {
      button.onclick = async () => {
        const { questionId, questionTitle, questionType, options, correctAnswer, studentAnswer } =
          button.dataset
        const panel = document.getElementById(`analysis-${questionId}`)
        if (!panel) return

        // 再次点击则收起
        if (panel.style.display !== 'none') {
          panel.style.display = 'none'
          return
        }

        panel.textContent = 'AI 正在解析中...'
        panel.style.display = 'block'

        try {
          const res = await analyzeWrongAnswer({
            questionTitle,
            questionType,
            options,
            correctAnswer,
            studentAnswer
          })
          panel.innerHTML = `<div class="ai-analysis-text">${escapeHtml(res.data)}</div>`
        } catch {
          panel.textContent = 'AI 解析失败，请稍后重试'
        }
      }
    })
  })
}

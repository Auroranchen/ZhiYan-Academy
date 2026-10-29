/**
 * 试题编辑的数据规整
 *
 * 试题可能来自手工添加、文本解析、AI 出题或 PPT 出题，各来源给出的
 * 字段完整度不一样。统一在这里补齐并按题型修正 answer 的形态，
 * 避免「多选题的 answer 是字符串」这类问题在保存后才暴露。
 */

const DEFAULT_SCORE = 5
const DEFAULT_POLICY = 'normal'

const newId = () => `q_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`

export const createQuestion = () => ({
  id: newId(),
  type: 'single',
  title: '',
  score: DEFAULT_SCORE,
  options: ['', ''],
  answer: null,
  gradingPolicy: DEFAULT_POLICY
})

/**
 * 按题型重置答案与选项，用于用户切换题型时。
 * 单选的答案是下标、多选是下标数组、判断是布尔、填空简答是文本，
 * 不重置会留下上一题型的残值。
 */
export const resetAnswerForType = (question) => {
  switch (question.type) {
    case 'single':
      question.options = question.options?.length ? question.options : ['', '']
      question.answer = null
      break
    case 'multi':
      question.options = question.options?.length ? question.options : ['', '']
      question.answer = []
      break
    case 'judge':
      question.options = []
      question.answer = null
      break
    case 'blank':
    case 'short':
      question.options = []
      question.answer = ''
      if (!question.gradingPolicy) question.gradingPolicy = DEFAULT_POLICY
      break
    default:
      question.options = []
      question.answer = null
  }
  return question
}

/** 把外部来源的题目补齐成编辑器可用的形态 */
export const normalizeQuestion = (question) => {
  const normalized = {
    id: question?.id || newId(),
    type: question?.type || 'single',
    title: question?.title || '',
    score: Number(question?.score) || 0,
    options: Array.isArray(question?.options) ? question.options : [],
    answer: question?.answer ?? null,
    gradingPolicy: question?.gradingPolicy || DEFAULT_POLICY
  }

  if (normalized.type === 'single') {
    normalized.options = normalized.options.length ? normalized.options : ['', '']
    const index = Number(normalized.answer)
    const blank =
      normalized.answer === null || normalized.answer === undefined || normalized.answer === ''
    normalized.answer = blank || Number.isNaN(index) ? null : index
  }

  if (normalized.type === 'multi') {
    normalized.options = normalized.options.length ? normalized.options : ['', '']
    normalized.answer = Array.isArray(normalized.answer)
      ? normalized.answer.map(Number).filter((n) => !Number.isNaN(n))
      : []
  }

  if (normalized.type === 'judge') {
    normalized.options = []
    if (typeof normalized.answer !== 'boolean') {
      normalized.answer = normalized.answer == null ? null : String(normalized.answer) === 'true'
    }
  }

  if (normalized.type === 'blank' || normalized.type === 'short') {
    normalized.options = []
    normalized.answer = typeof normalized.answer === 'string' ? normalized.answer.trim() : null
  }

  return normalized
}

/**
 * 保存前校验，返回第一条错误信息；全部通过返回空串。
 */
export const validateQuestions = (questions) => {
  if (!questions.length) return '请至少添加一道试题'

  for (let i = 0; i < questions.length; i += 1) {
    const q = questions[i]
    const label = `第 ${i + 1} 题`

    if (!q.title?.trim()) return `${label}题干不能为空`

    if (q.type === 'single' || q.type === 'multi') {
      if (!Array.isArray(q.options) || q.options.length < 2) return `${label}至少需要两个选项`
      if (q.options.some((o) => !String(o).trim())) return `${label}的选项内容不能为空`
    }

    if (q.type === 'single' && typeof q.answer !== 'number') return `${label}请选择正确答案`
    if (q.type === 'multi' && !(Array.isArray(q.answer) && q.answer.length)) {
      return `${label}请至少选择一个正确答案`
    }
    if (q.type === 'judge' && typeof q.answer !== 'boolean') return `${label}请选择正确答案`
    if (q.type === 'blank' && !String(q.answer || '').trim()) return `${label}请填写正确答案`
  }

  return ''
}

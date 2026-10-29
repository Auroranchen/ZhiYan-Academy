/**
 * AI 出题结果的解析与规范化
 *
 * 文本出题、PPT 出题、试题识别三个入口拿到的都是大模型返回的 JSON 字符串，
 * 原先各自在 TeacherHomework.vue 里重写一遍反转义、解析、字段补全和填空回填，
 * 三份实现已经出现分歧（同样的清洗只有一处做了题干去噪）。
 * 这里作为唯一来源，并保持纯函数，出题结果的正确性可以单独验证。
 */
import { normalizeQuestionType } from '@/utils/questionTypes'

/** 评分宽严档位 → 后端判分策略 */
const POLICY_BY_STANDARD = {
  严格: 'strict',
  一般: 'normal',
  宽松: 'lenient'
}

export const gradingPolicyOf = (standard) => POLICY_BY_STANDARD[standard] || 'normal'

let idSeq = 0

/** 题目在前端需要稳定且唯一的 key，后端此时还没有分配 ID */
const nextQuestionId = () => `q_${Date.now()}_${(idSeq += 1)}`

/**
 * 解析大模型返回的题目 JSON。
 *
 * 模型经常输出 JSON 不认的转义（如 \( 、\[ ），直接 JSON.parse 会整批失败，
 * 因此先把非法转义还原成原字符；\n \r \t \" \\ \/ \b \f \u 是合法的，要保留。
 *
 * @param {string|object} raw 接口返回的 data
 * @returns {{ success: boolean, questions: object[], message: string }}
 */
export const parseAiQuestions = (raw) => {
  if (raw === null || raw === undefined || raw === '') {
    return { success: false, questions: [], message: 'AI 未返回内容，请重试' }
  }

  let payload = raw
  if (typeof payload === 'string') {
    try {
      payload = JSON.parse(payload.replace(/\\([^nrt"\\/bfu])/g, '$1'))
    } catch (e) {
      return { success: false, questions: [], message: 'AI 返回格式异常，请重试' }
    }
  }

  if (!payload || typeof payload !== 'object') {
    return { success: false, questions: [], message: 'AI 返回格式异常，请重试' }
  }
  if (!Array.isArray(payload.questions) || !payload.questions.length) {
    return { success: false, questions: [], message: payload.message || 'AI 未生成试题，请重试' }
  }

  return {
    success: payload.success !== false,
    questions: payload.questions,
    message: payload.message || `成功识别 ${payload.questions.length} 道试题`
  }
}

/** 去掉选项自带的 "A. " 前缀，题库里只存内容本身 */
const stripOptionPrefix = (option) =>
  typeof option === 'string' ? option.replace(/^[A-Da-d][.、．:：\s]\s*/, '').trim() : option

/**
 * 清掉题干里混进来的答案标记与选项文本。
 *
 * 从文档识别出的题目常把 "（A）" 或整排选项留在题干里，
 * 不清掉的话教师得逐题手工删。
 */
const cleanTitle = (title) => {
  if (typeof title !== 'string') return title
  // 先摘掉混进来的选项，答案标记才会落到末尾，后面几条针对行尾的规则才生效
  return title
    .replace(/\s+[A-D][.、．:：]\s*\S+/g, '')
    .replace(/\s*[（(]\s*[A-Da-d√×✓✗对错]\s*[)）]\s*$/g, '')
    .replace(/\s*[（(]\s*(正确|错误|对|错)\s*[)）]\s*$/g, '')
    .replace(/\s*[√×✓✗]\s*$/g, '')
    .trim()
}

/**
 * 补全并清洗单道 AI 生成的题目。
 *
 * @param {object} question 模型返回的原始题目
 * @param {object} [options]
 * @param {string} [options.policy] 主观题判分策略
 * @param {boolean} [options.tidyTitle] 是否清理题干中的答案标记与选项
 * @returns {object} 新对象，不改动入参
 */
export const normalizeAiQuestion = (question, options = {}) => {
  const { policy, tidyTitle = false } = options
  const type = normalizeQuestionType(question?.type)

  const result = {
    ...question,
    id: question?.id || nextQuestionId(),
    type,
    score: question?.score || 5
  }

  if (tidyTitle) {
    result.title = cleanTitle(result.title)
  }

  if (policy && (type === 'blank' || type === 'short')) {
    result.gradingPolicy = policy
  }

  if (type === 'blank') {
    // 填空题不该带选项，模型偶尔会把原单选题的选项一并返回
    result.options = []
    if (typeof result.answer === 'string') {
      result.answer = result.answer.trim()
    }
  } else if (Array.isArray(result.options)) {
    result.options = result.options.map(stripOptionPrefix)
  }

  return result
}

/**
 * 填空题不足时，把单选题改造成填空题补齐。
 *
 * 模型对填空题的配额常常不照做，直接交付会让教师拿到一份题型不符的作业。
 * 只改造答案明确的单选题，把正确选项的文字当作填空答案。
 *
 * @param {object[]} questions 已规范化的题目，函数不修改入参
 * @param {number} wanted 期望的填空题数量
 * @param {string} policy 判分策略
 * @returns {object[]} 新数组
 */
export const backfillBlanks = (questions, wanted, policy) => {
  const list = questions.map((q) => ({ ...q }))
  let need = Math.max(0, (wanted || 0) - list.filter((q) => q.type === 'blank').length)
  if (!need) return list

  for (const q of list) {
    if (!need) break
    const convertible =
      q.type === 'single' &&
      Array.isArray(q.options) &&
      typeof q.answer === 'number' &&
      q.answer >= 0 &&
      q.answer < q.options.length
    if (!convertible) continue

    const correctText = String(q.options[q.answer] || '').trim()
    if (!correctText) continue

    q.type = 'blank'
    q.options = []
    q.answer = correctText
    q.gradingPolicy = policy
    need -= 1
  }

  return list
}

/**
 * 出题接口返回值的一站式处理：解析 → 规范化 → 按配额补填空题。
 *
 * @param {string|object} raw 接口返回的 data
 * @param {object} [options]
 * @param {string} [options.scoringStandard] 评分宽严，如「一般」
 * @param {number} [options.blankCount] 期望的填空题数量
 * @param {boolean} [options.tidyTitle] 是否清理题干
 * @returns {{ success: boolean, questions: object[], message: string }}
 */
export const buildAiQuestions = (raw, options = {}) => {
  const parsed = parseAiQuestions(raw)
  if (!parsed.success) return parsed

  const policy = gradingPolicyOf(options.scoringStandard)
  const normalized = parsed.questions.map((q) =>
    normalizeAiQuestion(q, { policy, tidyTitle: options.tidyTitle })
  )
  const questions = options.blankCount
    ? backfillBlanks(normalized, options.blankCount, policy)
    : normalized

  return { success: true, questions, message: parsed.message }
}

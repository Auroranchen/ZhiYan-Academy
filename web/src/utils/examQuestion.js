/**
 * 试卷题目的数据规整
 *
 * 与作业的 questionEditor 分开：考试多出程序填空、程序题、代码解释三种代码类题型，
 * 它们的 answer 形态各不相同（程序填空是数组、其余是文本），
 * 切换题型时不重置会留下上一题型的残值，保存后才在学生端暴露。
 */

const DEFAULT_SCORE = 5
const DEFAULT_POLICY = 'normal'

/** 需要指定判分宽严的题型 */
const POLICY_TYPES = ['blank', 'prog_blank']

/** 带代码编辑区的题型 */
const CODE_TYPES = ['prog_blank', 'prog', 'code_explain']

const newId = () => `q_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`

export const createExamQuestion = () => ({
  id: newId(),
  type: 'single',
  title: '',
  score: DEFAULT_SCORE,
  options: ['', '', '', ''],
  answer: null,
  gradingPolicy: DEFAULT_POLICY
})

/**
 * 按题型重置答案与选项，用于切换题型时。
 * 直接修改传入对象，与编辑器里 v-model 绑定的写法保持一致。
 */
export const resetExamAnswerForType = (question) => {
  const keepOptions = question.options?.length >= 2 ? question.options : ['', '', '', '']

  switch (question.type) {
    case 'single':
      question.options = keepOptions
      question.answer = null
      break
    case 'multi':
      question.options = keepOptions
      question.answer = []
      break
    case 'judge':
      question.options = []
      question.answer = true
      break
    case 'prog_blank':
      question.options = []
      question.answer = ['']
      question.code = question.code || ''
      break
    case 'prog':
    case 'code_explain':
      question.options = []
      question.answer = ''
      question.code = question.code || ''
      break
    default:
      question.options = []
      question.answer = ''
  }

  if (POLICY_TYPES.includes(question.type) && !question.gradingPolicy) {
    question.gradingPolicy = DEFAULT_POLICY
  }

  return question
}

/**
 * 保存前校验，返回第一条错误信息；全部通过返回空串。
 */
export const validateExamQuestions = (questions) => {
  if (!questions.length) return '请至少添加一道题目'

  for (let i = 0; i < questions.length; i += 1) {
    const q = questions[i]
    const label = `第 ${i + 1} 题`

    if (!String(q.title || '').trim()) return `${label}题干不能为空`

    if (q.type === 'single' || q.type === 'multi') {
      if (!Array.isArray(q.options) || q.options.length < 2) return `${label}至少需要两个选项`
      if (q.options.some((o) => !String(o).trim())) return `${label}存在空选项`
      if (q.type === 'single' && typeof q.answer !== 'number') return `${label}未设置正确答案`
      if (q.type === 'multi' && !(Array.isArray(q.answer) && q.answer.length)) {
        return `${label}未设置正确答案`
      }
    }

    if (q.type === 'judge' && typeof q.answer !== 'boolean') return `${label}未设置正确答案`
    if (q.type === 'blank' && !String(q.answer || '').trim()) return `${label}未设置标准答案`

    if (q.type === 'prog_blank') {
      if (!Array.isArray(q.answer) || !q.answer.length) return `${label}请至少设置一个填空答案`
      if (q.answer.some((a) => !String(a || '').trim())) return `${label}存在空的填空答案`
    }

    if (q.type === 'code_explain' && !String(q.code || '').trim()) {
      return `${label}请填写待解释的代码`
    }
  }

  return ''
}

/**
 * 转成保存用的形态。
 *
 * 程序填空题要额外记录空的数量：标准答案下发给学生前会被脱敏，
 * 学生端只能靠这个数字决定渲染几个输入框。
 */
export const toSavedExamQuestions = (questions) =>
  questions.map((q) =>
    q.type === 'prog_blank'
      ? { ...q, blankCount: Array.isArray(q.answer) ? q.answer.length : 1 }
      : q
  )

/** 试卷总分由各题分值累加，不单独维护 */
export const sumExamScore = (questions) =>
  questions.reduce((sum, q) => sum + (Number(q.score) || 0), 0)

export const isCodeQuestionType = (type) => CODE_TYPES.includes(type)

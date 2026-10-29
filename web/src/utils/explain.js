/**
 * AI 讲解页的纯函数
 *
 * 从 AiExplainHomework.vue 里抽出来的无状态逻辑：答案解析、判分展示、
 * 朗读文本清洗、知识点与代码讲解生成。抽出后可以单独测试，
 * 不必再跑起整个播放器组件。
 */
import { sanitizeHtml } from '@/utils/safeHtml'

const OPTION_LETTERS = ['A', 'B', 'C', 'D']

const QUESTION_TYPE_LABEL = {
  single: '单选题',
  multi: '多选题',
  judge: '判断题',
  blank: '填空题',
  short: '简答题'
}

/** 有选项的题型，代码题判定要排除它们 */
const CHOICE_TYPES = ['single', 'multi', 'judge', 'blank']

export const getQuestionTypeLabel = (type) => QUESTION_TYPE_LABEL[type] || '题目'

/**
 * 题库保护：未提交作业或本题未作答时，服务端不会下发答案与解析，
 * 避免讲解页变成抄答案的入口。
 */
export const isExplainRestricted = (question) =>
  !!(question && (question.explainRestricted === true || question.explainPolicy === 'restricted'))

/** 只有服务端下发了 correctAnswer 才能比对正误 */
export const correctnessKnown = (question) =>
  !!question &&
  !isExplainRestricted(question) &&
  question.correctAnswer !== undefined &&
  question.correctAnswer !== null &&
  question.correctAnswer !== ''

/**
 * 把答案统一解析成字母集合。
 *
 * 历史数据里同一个含义存过五种形态：下标数字 2、下标数组 [0,1]、
 * JSON 字符串 "[0,1]"、逗号分隔 "A,C"、连写字母 "AC"。
 *
 * @param {*} answer
 * @returns {Set<string>} 形如 Set { 'A', 'C' }
 */
export const parseAnswerLetters = (answer) => {
  if (!answer && answer !== 0) return new Set()

  if (Array.isArray(answer)) {
    return new Set(
      answer.map((value) => {
        const index = parseInt(value, 10)
        return index >= 0 && index < OPTION_LETTERS.length
          ? OPTION_LETTERS[index]
          : String(value).toUpperCase()
      })
    )
  }

  const text = String(answer)

  if (text.startsWith('[')) {
    try {
      const parsed = JSON.parse(text)
      if (Array.isArray(parsed)) return parseAnswerLetters(parsed)
    } catch {
      // 不是合法 JSON 就按普通字符串继续解析
    }
  }

  // 单个下标数字，长度限制避免把 "12" 误判成下标
  const index = parseInt(text, 10)
  if (text.length <= 1 && index >= 0 && index < OPTION_LETTERS.length) {
    return new Set([OPTION_LETTERS[index]])
  }

  if (text.includes(',')) {
    return new Set(text.split(',').map((v) => v.trim().toUpperCase()))
  }

  return new Set(
    text
      .toUpperCase()
      .split('')
      .filter((c) => OPTION_LETTERS.includes(c))
  )
}

export const isOptionCorrect = (index, question) => {
  if (!question || isExplainRestricted(question)) return false
  return parseAnswerLetters(question.correctAnswer).has(OPTION_LETTERS[index])
}

export const isOptionWrong = (index, question) => {
  if (!question || isExplainRestricted(question)) return false
  const letter = OPTION_LETTERS[index]
  return parseAnswerLetters(question.studentAnswer).has(letter) && !isOptionCorrect(index, question)
}

/** 编程题没有标准选项，只能按后端给的实际得分判定 */
export const isCodeQuestion = (question) => {
  if (!question || CHOICE_TYPES.includes(question.questionType)) return false
  return !!question.referenceCode?.trim()
}

export const isCorrect = (question) => {
  if (!correctnessKnown(question)) return false
  return String(question.studentAnswer) === String(question.correctAnswer)
}

export const isFullScore = (question) => {
  if (isCodeQuestion(question)) {
    return question.studentScore !== undefined && question.studentScore >= question.score
  }
  return isCorrect(question)
}

export const getStudentScore = (question, fallback = '—') => {
  if (isCodeQuestion(question)) {
    return question.studentScore !== undefined ? question.studentScore : fallback
  }
  if (!correctnessKnown(question)) return fallback
  return isCorrect(question) ? question.score : 0
}

export const getScoreType = (score) => {
  if (score >= 90) return 'success'
  if (score >= 60) return 'warning'
  return 'danger'
}

/** 把各种形态的答案渲染成给人看的文本 */
export const formatAnswer = (answer, questionType, emptyLabel = '未作答') => {
  if (answer === undefined || answer === null || answer === '') return emptyLabel
  if (typeof answer === 'boolean') return answer ? '正确' : '错误'

  if (questionType === 'single' || questionType === 'multi') {
    const letters = parseAnswerLetters(answer)
    return letters.size ? [...letters].sort().join(', ') : emptyLabel
  }

  if (questionType === 'judge') {
    if (String(answer) === 'true') return '正确'
    if (String(answer) === 'false') return '错误'
  }

  return Array.isArray(answer) ? answer.join(', ') : String(answer)
}

/**
 * 把题干 HTML 清洗成适合语音朗读的纯文本。
 * 下划线、LaTeX 记号直接念出来会很难听懂，这里换成口语说法。
 */
export const stripHtml = (html) => {
  if (!html) return ''

  const container = document.createElement('div')
  container.innerHTML = sanitizeHtml(html)

  return (container.textContent || '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/_{2,}/g, '填空')
    .replace(/\\frac/g, '')
    .replace(/[{}]/g, '')
    .replace(/\^/g, '的次方')
    .replace(/_/g, '的下标')
    .replace(/\$/g, '')
    .replace(/[\r\n\t]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

/** 题干命中这些关键词时，用对应的知识点描述 */
const KEYWORD_TOPICS = [
  ['二叉树', '二叉树的定义、性质与遍历'],
  ['线性表', '线性表的定义和分类，常见线性表类型的特点'],
  ['链表', '链表的基本操作与特性'],
  ['栈', '栈的先进后出特性与应用'],
  ['队列', '队列的先进先出特性与应用'],
  ['散列', '散列表的冲突处理与查找效率'],
  ['排序', '排序算法的原理与复杂度分析'],
  ['查找', '查找算法的效率与适用场景'],
  ['递归', '递归的基本思想与终止条件'],
  ['复杂度', '算法时间复杂度与空间复杂度分析'],
  ['指针', '指针的概念与内存操作'],
  ['删除', '数据结构中删除操作的实现与注意事项'],
  ['插入', '数据结构中插入操作的实现与复杂度'],
  ['树', '树的结构特点与遍历方法'],
  ['图', '图的存储结构与遍历算法']
]

const TITLE_SNIPPET_LIMIT = 20

/**
 * 后端没返回知识点时，从题干里推断一个。
 * 关键词按从具体到宽泛排列，「二叉树」要先于「树」命中。
 */
export const generateKeyPoints = (question) => {
  const title = stripHtml(question?.questionTitle || '')

  const matched = KEYWORD_TOPICS.find(([keyword]) => title.includes(keyword))
  if (matched) return matched[1]

  if (title.length > 5) {
    const snippet =
      title.length > TITLE_SNIPPET_LIMIT ? `${title.slice(0, TITLE_SNIPPET_LIMIT)}...` : title
    return `本题考查：${snippet}`
  }

  return '本题考查基础知识的理解与应用'
}

/** 代码特征 → 讲解语句。逐行念代码没人听得进去，只概述思路 */
const CODE_PATTERNS = [
  {
    test: (code) => /for[\s\S]*for/.test(code),
    text: '这段代码使用了双重循环的方式来解决问题。'
  },
  {
    test: (code) => code.includes('for') || code.includes('while'),
    text: '代码通过循环遍历来处理数据。'
  },
  {
    test: (code) => code.includes('malloc') || code.includes('new '),
    text: '注意这里需要动态分配内存来存储结果。'
  },
  {
    test: (code, keyPoints) =>
      code.includes('HashMap') || code.includes('哈希') || keyPoints.includes('哈希'),
    text: '核心思路是利用哈希表来提高查找效率，将时间复杂度从暴力解法的 O(n²) 降低到 O(n)。'
  },
  {
    test: (code) => /return\s+(NULL|null|None)/.test(code),
    text: '如果没有找到满足条件的结果，函数返回空值。'
  },
  {
    test: (code) => code.includes('递归') || /(\w+)\([^)]*\)[\s\S]*\1\(/.test(code),
    text: '这里用到了递归的思想，注意递归的终止条件。'
  }
]

const GENERIC_CODE_EXPLANATION = [
  '请同学们仔细阅读这段参考代码，注意变量的定义和函数的返回值。',
  '理解代码的整体逻辑比记住每一行更重要。'
]

const CODE_CLOSING = '同学们可以对照代码和前面讲的解题思路，加深理解。'

export const generateCodeExplanation = (question) => {
  const code = question?.referenceCode || ''
  if (!code.trim()) return ''

  const keyPoints = question.keyPoints || ''
  const matched = CODE_PATTERNS.filter((p) => p.test(code, keyPoints)).map((p) => p.text)
  const body = matched.length ? matched : GENERIC_CODE_EXPLANATION

  return [...body, CODE_CLOSING].join('')
}

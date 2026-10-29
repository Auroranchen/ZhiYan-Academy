export const parseHomeworkContent = (content) => {
  if (!content || typeof content !== 'string') {
    return null
  }
  try {
    const data = JSON.parse(content)
    if (!data || !Array.isArray(data.questions)) {
      return null
    }
    return data
  } catch (error) {
    return null
  }
}

export const parseStudentContent = (content) => {
  if (!content || typeof content !== 'string') {
    return null
  }
  try {
    const data = JSON.parse(content)
    if (!data || !Array.isArray(data.questions) || typeof data.answers !== 'object' || data.answers === null) {
      return null
    }
    return data
  } catch (error) {
    return null
  }
}

/**
 * 把作业原题中的标准答案合并进学生提交的题目里。
 *
 * 学生提交的内容出于安全考虑不含标准答案，展示批改结果时必须回填，
 * 否则对错判定拿不到参照。管理端、教师端和学生端三处都要做这件事。
 *
 * @param {string} studentContent 学生提交内容（JSON 字符串）
 * @param {string} homeworkContent 作业原题内容（JSON 字符串）
 * @returns {{ questions: object[], answers: object } | null} 无法解析时返回 null
 */
export const mergeStudentAnswers = (studentContent, homeworkContent) => {
  const submitted = parseStudentContent(studentContent)
  if (!submitted) return null

  const origin = parseHomeworkContent(homeworkContent)
  if (!origin?.questions?.length) {
    return { questions: submitted.questions, answers: submitted.answers }
  }

  const questions = submitted.questions.map((question, index) => {
    // 必须先判断 id 是否存在：历史数据的题目普遍没有 id，
    // 直接 find 会因为 undefined === undefined 让每道题都命中第一题
    const hasId = question.id !== undefined && question.id !== null
    const matched = hasId ? origin.questions.find((q) => q.id === question.id) : null
    const source = matched || origin.questions[index]
    return { ...question, answer: source ? source.answer : null }
  })

  return { questions, answers: submitted.answers }
}

export const buildHomeworkContent = (questions) => JSON.stringify({
  version: 1,
  questions: questions || []
})

export const buildStudentContent = (questions, answers) => JSON.stringify({
  version: 1,
  questions: questions || [],
  answers: answers || {}
})

/** 下发给学生的题目要去掉标准答案，answer 解构出来只为丢弃 */
export const stripQuestionAnswers = (questions) =>
  (questions || []).map((question) => {
    // eslint-disable-next-line no-unused-vars
    const { answer, ...rest } = question || {}
    return rest
  })

export const getOptionLabel = (index) => {
  const code = 65 + index
  if (code >= 65 && code <= 90) {
    return String.fromCharCode(code)
  }
  return String(index + 1)
}

const normalizeAnswerArray = (answer) => {
  if (Array.isArray(answer)) {
    return answer
  }
  if (answer === null || answer === undefined || answer === '') {
    return []
  }
  if (typeof answer === 'number') {
    return [answer]
  }
  if (typeof answer === 'string') {
    const trimmed = answer.trim()
    if (!trimmed) {
      return []
    }
    try {
      const parsed = JSON.parse(trimmed)
      if (Array.isArray(parsed)) {
        return parsed
      }
    } catch (error) {
      return trimmed.split(',').map(item => item.trim()).filter(Boolean)
    }
  }
  return []
}

/**
 * 判断题答案归一化。
 *
 * 教师手工录入与 AI 出题给出的写法很杂：true / 1 / 正确 / 对 / 是 / √，
 * 只认 'true' 会把中文答案一律判成「错误」。
 */
export const toJudgeBoolean = (value) => {
  if (typeof value === 'boolean') return value
  return /^(true|1|t|正确|对|是|√|✓)$/i.test(String(value == null ? '' : value).trim())
}

const formatChoice = (question, selected, labels) => {
  if (!question || !Array.isArray(question.options)) {
    return labels.emptyLabel
  }
  const optionIndex = Number(selected)
  if (Number.isNaN(optionIndex) || optionIndex < 0 || optionIndex >= question.options.length) {
    return labels.emptyLabel
  }
  const label = getOptionLabel(optionIndex)
  const text = question.options[optionIndex]
  return `${label}. ${text}`
}

export const formatAnswerText = (question, answer, labelOverrides = {}) => {
  const labels = {
    emptyLabel: '-',
    trueLabel: 'true',
    falseLabel: 'false',
    ...labelOverrides
  }

  if (!question) {
    return labels.emptyLabel
  }

  if (answer === null || answer === undefined || answer === '') {
    return labels.emptyLabel
  }

  if (question.type === 'single') {
    return formatChoice(question, answer, labels)
  }

  if (question.type === 'multi') {
    const items = normalizeAnswerArray(answer)
      .map(item => formatChoice(question, item, labels))
      .filter(text => text !== labels.emptyLabel)
    return items.length ? items.join(', ') : labels.emptyLabel
  }

  if (question.type === 'judge') {
    return toJudgeBoolean(answer) ? labels.trueLabel : labels.falseLabel
  }

  if (question.type === 'prog_blank') {
    const arr = Array.isArray(answer) ? answer : [answer]
    const items = arr.map((a, i) => {
      const t = String(a == null ? '' : a).trim()
      return `空${i + 1}: ${t || labels.emptyLabel}`
    })
    return items.length ? items.join('；') : labels.emptyLabel
  }

  if (question.type === 'short' || question.type === 'blank' || question.type === 'prog' || question.type === 'code_explain') {
    const text = String(answer).trim()
    return text ? text : labels.emptyLabel
  }

  return labels.emptyLabel
}

const escapeHtml = (value) => {
  if (value === null || value === undefined) {
    return ''
  }
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/**
 * 从一行文本里切出并排书写的选项，如 "A、int  B、String  C、char"
 *
 * 用切分而不是单条正则：选项内容里常含英文（"String"、"C语言"），
 * 且要避开 lookbehind（Safari 16.4 以下不支持）。
 * 要求选项字母连续递增，否则视为正文里恰好出现了 "A." 之类的写法。
 *
 * @param {string} text 待切分的文本
 * @returns {string[]} 选项内容；识别不出时返回空数组
 */
const splitInlineOptions = (text) => {
  const re = /(^|\s)([A-Za-z])\s*[.、．:：)）]\s*/g
  const marks = []
  let m
  while ((m = re.exec(text)) !== null) {
    marks.push({
      letter: m[2].toUpperCase(),
      start: m.index + m[1].length,
      contentStart: re.lastIndex
    })
  }
  if (marks.length < 2) return []

  const consecutive = marks.every(
    (mark, i) => i === 0 || mark.letter.charCodeAt(0) === marks[i - 1].letter.charCodeAt(0) + 1
  )
  if (!consecutive) return []

  const contents = marks.map((mark, i) =>
    text.slice(mark.contentStart, marks[i + 1] ? marks[i + 1].start : text.length).trim()
  )
  // 出现空选项说明切分位置不对，整行放弃，避免答案字母对错选项
  return contents.some(c => !c) ? [] : contents
}

/**
 * 拆开写在同一行的题干与选项，如 "1.题干 A.甲 B.乙 C.丙"
 *
 * @param {string} stem 去掉题号后的整行内容
 * @returns {{ title: string, options: string[] }}
 */
const splitStemAndOptions = (stem) => {
  const at = stem.search(/(?:^|\s)[A-Za-z]\s*[.、．:：)）]\s*\S/)
  if (at <= 0) return { title: stem, options: [] }

  const options = splitInlineOptions(stem.slice(at))
  return options.length >= 2 ? { title: stem.slice(0, at).trim(), options } : { title: stem, options: [] }
}

/**
 * 解析试题文本，自动识别题目、选项和答案
 *
 * 支持格式：
 *   1.题干？(A)                        ← 行内答案
 *   A、el  B、root  C、element  D、comp  ← 单行多选项
 *
 *   1.题干？
 *   A. el                              ← 每行一个选项
 *   B. root
 *   答案：A                            ← 单独答案行
 *
 *   一、单选题                          ← 题型标题行（跳过）
 *   1.（单选）题干                      ← 题干中含题型提示
 *
 *   1.判断题题干（  ）                  ← 判断题
 *   答案：正确 / 错误 / √ / ×
 *
 *   1.____是Java的基本数据类型          ← 填空题（含下划线）
 *   答案：int
 *
 *   1.简述MVC模式                       ← 简答题（无选项无下划线）
 */
export const parseQuestionText = (text) => {
  if (!text || typeof text !== 'string') {
    return { success: false, questions: [], message: '请输入试题内容' }
  }

  const raw = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  const lines = raw.split('\n').map(l => l.trim()).filter(Boolean)
  if (!lines.length) {
    return { success: false, questions: [], message: '请输入试题内容' }
  }

  const questions = []
  let curTitle = null
  let curTitleLines = []
  let curOptions = []
  let curAnswer = null
  let curTypeHint = null
  let idSeq = 0

  // --- 正则 ---
  // 题目行：数字编号开头，支持 1. / 1、/ 1) / 1）/ (1) / 1 题干
  const reQuestion = /^[（(]?\s*(\d+)\s*(?:[)）]\s*[.、．:：]?|[.、．:：]|\s)\s*(.+)/
  // 选项行（单个）：A. / A、/ A) / A）/ (A) 等
  const reOption = /^[（(]?\s*([A-Za-z])\s*(?:[)）]\s*[.、．:：]?|[.、．:：]|\s)\s*(.+)/
  // 答案行
  const reAnswerLine = /^(?:答案|正确答案|参考答案|标准答案)\s*[：:]\s*(.+)/i
  // 行内答案（题干末尾）
  const reInlineAnswer = /[（(【[]\s*([A-Za-z]+|正确|错误|对|错|√|×|true|false)\s*[)）】\]]\s*$/i
  // 判断题答案
  const reJudge = /^(正确|错误|对|错|√|×|✓|✗|true|false|T|F)$/i
  const reJudgeTrue = /^(正确|对|√|✓|true|T)$/i
  // 题型标题行（跳过）
  const reSectionHead = /^[一二三四五六七八九十]+\s*[、.．]\s*(单选|多选|判断|填空|简答|选择|问答|论述|名词解释|计算)/
  // 题干中的题型提示
  const reTypeInTitle = /[（(]\s*(单选|多选|判断|填空|简答|选择题?|多选题?|单选题?|判断题?)\s*[)）]/

  const isLetterAnswer = (s) => /^[A-Za-z]+$/.test(s?.trim())

  const flush = () => {
    if (!curTitle && !curTitleLines.length) return
    const title = curTitle || curTitleLines.join('')
    if (!title) { reset(); return }

    let type = 'single'
    let answer = null
    const ans = curAnswer?.trim()

    // 通过题型提示推断
    if (curTypeHint) {
      const h = curTypeHint
      if (/判断/.test(h)) type = 'judge'
      else if (/多选/.test(h)) type = 'multi'
      else if (/填空/.test(h)) type = 'blank'
      else if (/简答|问答|论述|名词解释|计算/.test(h)) type = 'short'
    }

    if (curOptions.length > 0) {
      // 有选项 → 选择题
      if (ans && isLetterAnswer(ans)) {
        if (ans.length > 1 || type === 'multi') {
          type = 'multi'
          answer = ans.toUpperCase().split('').map(c => c.charCodeAt(0) - 65).filter(i => i >= 0 && i < curOptions.length)
        } else {
          if (type !== 'multi') type = 'single'
          const idx = ans.toUpperCase().charCodeAt(0) - 65
          answer = (idx >= 0 && idx < curOptions.length) ? idx : null
        }
      } else {
        if (type !== 'multi') type = 'single'
      }
    } else {
      // 无选项
      if (ans && reJudge.test(ans)) {
        type = 'judge'
        answer = reJudgeTrue.test(ans)
      } else if (type === 'judge') {
        if (ans) answer = reJudgeTrue.test(ans)
      } else if (/_{2,}/.test(title) || type === 'blank') {
        type = 'blank'
        answer = ans || null
      } else if (ans) {
        if (type === 'single') type = 'short'
        answer = ans
      } else {
        if (type === 'single') type = 'short'
      }
    }

    questions.push({
      id: `q_${Date.now()}_${++idSeq}`,
      type,
      title: title.replace(reTypeInTitle, '').trim(),
      score: 5,
      options: curOptions.length ? [...curOptions] : [],
      answer
    })
    reset()
  }

  const reset = () => { curTitle = null; curTitleLines = []; curOptions = []; curAnswer = null; curTypeHint = null }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]

    // 跳过题型大标题行
    if (reSectionHead.test(line)) continue

    // 答案行
    const ansM = line.match(reAnswerLine)
    if (ansM) { curAnswer = ansM[1].trim(); continue }

    // 题目行
    const qM = line.match(reQuestion)
    if (qM) {
      flush()
      let stem = qM[2]

      // 题型提示
      const typeM = stem.match(reTypeInTitle)
      if (typeM) curTypeHint = typeM[1]

      // 行内答案
      const inlM = stem.match(reInlineAnswer)
      if (inlM) {
        curAnswer = inlM[1]
        stem = stem.replace(reInlineAnswer, '').trim()
      }
      // 题干同行可能跟着选项，如 "1.题干 A.甲 B.乙 C.丙 D.丁"
      const split = splitStemAndOptions(stem)
      curTitle = split.title
      curOptions.push(...split.options)
      continue
    }

    // 选项行：先尝试单行多选项
    const inlineOpts = splitInlineOptions(line)
    if (inlineOpts.length >= 2) {
      curOptions.push(...inlineOpts)
      continue
    }

    // 单个选项行
    const oM = line.match(reOption)
    if (oM) {
      curOptions.push(oM[2].trim())
      continue
    }

    // 判断题独立答案行
    if (curTitle && reJudge.test(line)) {
      curAnswer = line
      continue
    }

    // 可能是题干续行（当前已有题目但还没出现选项）
    if (curTitle && curOptions.length === 0) {
      curTitle += line
      continue
    }
  }
  flush()

  if (!questions.length) {
    return { success: false, questions: [], message: '未能识别到有效试题，请检查格式' }
  }
  return { success: true, questions, message: `成功识别 ${questions.length} 道试题` }
}

export const renderExamHtml = (questions, answers, options = {}) => {
  const labels = {
    answerLabel: 'Answer',
    trueLabel: 'true',
    falseLabel: 'false',
    emptyLabel: '-',
    ...options
  }

  if (!Array.isArray(questions) || !questions.length) {
    return ''
  }

  return questions.map((question, index) => {
    const optionsHtml = Array.isArray(question.options) && question.options.length
      ? `<ul style="margin: 8px 0; padding-left: 18px;">${question.options.map((option, optIndex) => {
        const label = getOptionLabel(optIndex)
        return `<li>${label}. ${escapeHtml(option)}</li>`
      }).join('')}</ul>`
      : ''
    const answerText = answers
      ? formatAnswerText(question, answers[question.id], {
        trueLabel: labels.trueLabel,
        falseLabel: labels.falseLabel,
        emptyLabel: labels.emptyLabel
      })
      : ''
    const answerHtml = answers
      ? `<div style="margin-top: 6px; color: #606266;">${labels.answerLabel}: ${escapeHtml(answerText)}</div>`
      : ''
    const title = escapeHtml(question.title || '')

    return `
      <div style="border: 1px solid #ebeef5; padding: 10px; border-radius: 6px; margin-bottom: 10px; page-break-inside: avoid;">
        <div style="font-weight: 600; color: #303133; margin-bottom: 6px;">${index + 1}. ${title}</div>
        ${optionsHtml}
        ${answerHtml}
      </div>
    `
  }).join('')
}

/**
 * 比较单个填空答案是否正确（供 blank / prog_blank 复用）
 * @returns {boolean}
 */
const compareBlankAnswer = (correctStrRaw, selectedStrRaw, policy = 'normal') => {
  const normalize = (s, { ignoreCase, ignoreSpace, ignorePunct }) => {
    let t = String(s == null ? '' : s)
    if (ignoreCase) t = t.toLowerCase()
    if (ignorePunct) t = t.replace(/[\\.,;:!?\-()[\]{}'"，。；：！？、—（）【】]/g, '')
    // 此处原为 \\s+，匹配的是反斜杠加字母 s 而非空白字符，导致 loose 判分从未真正忽略空格
    if (ignoreSpace) t = t.replace(/\s+/g, '')
    return t.trim()
  }
  const lev = (a, b) => {
    const m = a.length, n = b.length
    if (m === 0) return n
    if (n === 0) return m
    const dp = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0))
    for (let i = 0; i <= m; i++) dp[i][0] = i
    for (let j = 0; j <= n; j++) dp[0][j] = j
    for (let i = 1; i <= m; i++) {
      for (let j = 1; j <= n; j++) {
        const cost = a[i - 1] === b[j - 1] ? 0 : 1
        dp[i][j] = Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
      }
    }
    return dp[m][n]
  }
  const correct = String(correctStrRaw == null ? '' : correctStrRaw)
  const selected = String(selectedStrRaw == null ? '' : selectedStrRaw)
  if (!correct.trim()) return null
  if (policy === 'strict') {
    return normalize(correct, { ignoreCase: false, ignoreSpace: false, ignorePunct: false })
      === normalize(selected, { ignoreCase: false, ignoreSpace: false, ignorePunct: false })
  }
  if (policy === 'normal') {
    return normalize(correct, { ignoreCase: false, ignoreSpace: false, ignorePunct: true })
      === normalize(selected, { ignoreCase: false, ignoreSpace: false, ignorePunct: true })
  }
  // loose：忽略大小写/空格/标点，并允许 1 个字符的编辑距离
  const c = normalize(correct, { ignoreCase: true, ignoreSpace: true, ignorePunct: true })
  const s = normalize(selected, { ignoreCase: true, ignoreSpace: true, ignorePunct: true })
  if (c === s) return true
  return lev(c, s) <= 1
}

/**
 * 检查客观题答案是否正确
 */
export const checkAnswerCorrect = (question, studentAnswer) => {
  if (!question || question.answer === null || question.answer === undefined) {
    return null // 无法判断（如简答题）
  }
  
  const type = question.type
  const correctAnswer = question.answer
  
  if (type === 'single') {
    const correct = typeof correctAnswer === 'number' ? correctAnswer : parseInt(correctAnswer, 10)
    const selected = typeof studentAnswer === 'number' ? studentAnswer : parseInt(studentAnswer, 10)
    return !isNaN(correct) && !isNaN(selected) && correct === selected
  }
  
  if (type === 'judge') {
    return toJudgeBoolean(correctAnswer) === toJudgeBoolean(studentAnswer)
  }
  
  if (type === 'multi') {
    const correctSet = new Set(normalizeAnswerArray(correctAnswer).map(Number))
    const selectedSet = new Set(normalizeAnswerArray(studentAnswer).map(Number))
    if (correctSet.size !== selectedSet.size) return false
    for (const item of correctSet) {
      if (!selectedSet.has(item)) return false
    }
    return true
  }
  
  if (type === 'blank') {
    return compareBlankAnswer(correctAnswer, studentAnswer, question.gradingPolicy || 'normal')
  }

  if (type === 'prog_blank') {
    // 程序填空：逐空比对，全部正确才算对
    const policy = question.gradingPolicy || 'normal'
    const correctArr = Array.isArray(correctAnswer) ? correctAnswer : [correctAnswer]
    const studentArr = Array.isArray(studentAnswer) ? studentAnswer : (studentAnswer == null ? [] : [studentAnswer])
    if (!correctArr.length) return null
    for (let i = 0; i < correctArr.length; i++) {
      const ok = compareBlankAnswer(correctArr[i], studentArr[i], policy)
      if (ok !== true) return false
    }
    return true
  }

  return null // 简答题 / 程序题 / 代码解释题等主观题无法自动判断
}

/**
 * 渲染带对错标识的答题结果HTML（用于查看和导出）
 * 正确显示绿色✓，错误显示红色✗和AI解析按钮
 */
export const renderExamResultHtml = (questions, answers, correctAnswers, options = {}) => {
  const labels = {
    answerLabel: '我的答案',
    correctAnswerLabel: '正确答案',
    analysisLabel: '解析',
    trueLabel: '正确',
    falseLabel: '错误',
    emptyLabel: '未作答',
    scoreSuffix: '分',
    showAnalysisButton: false, // 是否显示AI解析按钮
    totalScore: 100, // 试卷总分
    ...options
  }

  if (!Array.isArray(questions) || !questions.length) {
    return ''
  }

  // 计算每题分值（试卷默认100分，平均分配）
  const scorePerQuestion = (labels.totalScore / questions.length).toFixed(1)

  return questions.map((question, index) => {
    const studentAnswer = answers ? answers[question.id] : null
    const isCorrect = checkAnswerCorrect(question, studentAnswer)
    const isObjective = ['single', 'multi', 'judge', 'blank', 'prog_blank'].includes(question.type)
    const isSubjective = ['short', 'prog', 'code_explain'].includes(question.type)
    
    // 题目类型标签
    const typeLabels = { single: '单选题', multi: '多选题', judge: '判断题', blank: '填空题', short: '简答题', prog_blank: '程序填空题', prog: '程序题', code_explain: '代码解释题' }
    const typeLabel = typeLabels[question.type] || ''
    // 代码块（程序填空 / 程序题 / 代码解释题）
    const codeHtml = question.code
      ? `<pre style="margin: 8px 0; padding: 10px; background: #1e1e1e; color: #d4d4d4; border-radius: 6px; overflow-x: auto; font-family: monospace; font-size: 13px; white-space: pre-wrap;">${escapeHtml(question.code)}</pre>`
      : ''
    const scoreText = `（${scorePerQuestion}${labels.scoreSuffix}）`
    
    // 选项HTML
    const optionsHtml = Array.isArray(question.options) && question.options.length
      ? `<ul style="margin: 8px 0; padding-left: 18px; list-style: none;">${question.options.map((option, optIndex) => {
        const label = getOptionLabel(optIndex)
        // 判断该选项是否被选中
        let isSelected = false
        if (question.type === 'single') {
          isSelected = studentAnswer === optIndex || String(studentAnswer) === String(optIndex)
        } else if (question.type === 'multi') {
          const selectedArr = normalizeAnswerArray(studentAnswer)
          isSelected = selectedArr.includes(optIndex) || selectedArr.includes(String(optIndex))
        }
        // 判断该选项是否是正确答案
        let isCorrectOption = false
        if (question.type === 'single') {
          isCorrectOption = question.answer === optIndex || String(question.answer) === String(optIndex)
        } else if (question.type === 'multi') {
          const correctArr = normalizeAnswerArray(question.answer)
          isCorrectOption = correctArr.includes(optIndex) || correctArr.includes(String(optIndex))
        }
        
        let optionStyle = ''
        let optionPrefix = ''
        if (isSelected && isCorrect === true) {
          optionStyle = 'color: #67c23a; font-weight: 500;'
          optionPrefix = '✓ '
        } else if (isSelected && isCorrect === false) {
          optionStyle = 'color: #f56c6c; font-weight: 500;'
          optionPrefix = '✗ '
        } else if (isCorrectOption && isCorrect === false) {
          optionStyle = 'color: #67c23a;'
        }
        
        return `<li style="${optionStyle}">${optionPrefix}${label}. ${escapeHtml(option)}</li>`
      }).join('')}</ul>`
      : ''
    
    // 学生答案文本
    const answerText = formatAnswerText(question, studentAnswer, {
      trueLabel: labels.trueLabel,
      falseLabel: labels.falseLabel,
      emptyLabel: labels.emptyLabel
    })
    
    // 对错标识
    let resultIcon = ''
    let resultStyle = ''
    if (isObjective) {
      if (isCorrect === true) {
        resultIcon = '<span style="color: #67c23a; font-size: 18px; font-weight: bold;">✓</span>'
        resultStyle = 'border-left: 3px solid #67c23a;'
      } else if (isCorrect === false) {
        resultIcon = '<span style="color: #f56c6c; font-size: 18px; font-weight: bold;">✗</span>'
        resultStyle = 'border-left: 3px solid #f56c6c;'
      }
    }
    
    // 主观题（简答/程序题/代码解释）显示学生作答
    let shortAnswerHtml = ''
    if (isSubjective) {
      const shortAnswer = studentAnswer ? escapeHtml(String(studentAnswer)) : labels.emptyLabel
      shortAnswerHtml = `<div style="margin-top: 8px; padding: 10px; background: #f5f7fa; border-radius: 4px;">
        <div style="color: #909399; font-size: 12px; margin-bottom: 4px;">${labels.answerLabel}：</div>
        <div style="color: #303133; white-space: pre-wrap;">${shortAnswer}</div>
      </div>`
    }
    
    // 正确答案和AI解析按钮（仅错误时显示）
    let analysisHtml = ''
    if (isObjective && isCorrect === false && question.answer !== null && question.answer !== undefined) {
      const correctText = formatAnswerText(question, question.answer, {
        trueLabel: labels.trueLabel,
        falseLabel: labels.falseLabel,
        emptyLabel: '-'
      })
      
      // 构建选项文本用于AI解析
      let optionsText = ''
      if (Array.isArray(question.options) && question.options.length) {
        optionsText = question.options.map((opt, i) => `${getOptionLabel(i)}.${opt}`).join(' ')
      }
      
      const analysisButtonHtml = labels.showAnalysisButton 
        ? `<button class="ai-analysis-btn" 
            data-question-id="${question.id}"
            data-question-title="${escapeHtml(question.title || '')}"
            data-question-type="${question.type}"
            data-options="${escapeHtml(optionsText)}"
            data-correct-answer="${escapeHtml(correctText)}"
            data-student-answer="${escapeHtml(answerText)}"
            style="margin-top: 8px; padding: 4px 12px; background: #409eff; color: #fff; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">
            🤖 AI解析
          </button>
          <div class="ai-analysis-content" id="analysis-${question.id}" style="display: none; margin-top: 8px; padding: 10px; background: #f5f7fa; border-radius: 4px; border: 1px solid #e4e7ed;"></div>`
        : ''
      
      analysisHtml = `<div style="margin-top: 8px; padding: 10px; background: #fef0f0; border-radius: 4px; border: 1px solid #fde2e2;">
        <div style="color: #f56c6c; font-weight: 500;">${labels.correctAnswerLabel}：${escapeHtml(correctText)}</div>
        ${analysisButtonHtml}
      </div>`
    }
    
    const title = escapeHtml(question.title || '')

    return `
      <div style="border: 1px solid #ebeef5; padding: 12px; border-radius: 6px; margin-bottom: 12px; page-break-inside: avoid; ${resultStyle}">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
          <div style="font-weight: 600; color: #303133;">
            ${index + 1}. <span style="color: #909399; font-size: 12px;">[${typeLabel}]</span> ${title} ${scoreText}
          </div>
          ${resultIcon}
        </div>
        ${codeHtml}
        ${optionsHtml}
        ${shortAnswerHtml}
        ${isObjective && !isSubjective ? `<div style="margin-top: 6px; color: ${isCorrect ? '#67c23a' : '#f56c6c'};">${labels.answerLabel}: ${escapeHtml(answerText)}</div>` : ''}
        ${analysisHtml}
      </div>
    `
  }).join('')
}

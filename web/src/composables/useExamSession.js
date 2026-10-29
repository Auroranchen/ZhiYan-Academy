/**
 * 考试作答会话
 *
 * 把答题页里交织在一起的四件事收成一处：倒计时、定时暂存、防作弊监控、交卷。
 * 这些逻辑都带定时器与全局事件监听，散在组件里容易漏清理，也难以单独验证。
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getExamRecord, saveExamProgress, startExam, submitExam } from '@/api/exam'

const AUTOSAVE_INTERVAL = 30000
const TICK_INTERVAL = 1000
const DEFAULT_DURATION_MINUTES = 60

/** 需要文本输入的题型，初始值给空串而不是 null，否则输入框会显示 "null" */
const TEXT_TYPES = ['blank', 'short', 'prog', 'code_explain']

/**
 * @param {number} examid
 * @param {{ onFinished?: (record: object) => void }} [hooks]
 */
export function useExamSession(examid, hooks = {}) {
  const exam = reactive({})
  const questions = ref([])
  const answers = reactive({})
  const result = reactive({})

  const loaded = ref(false)
  const finished = ref(false)
  const submitting = ref(false)

  const remaining = ref(0)
  const switchCount = ref(0)
  const warning = ref(false)

  let tickTimer = null
  let autosaveTimer = null
  let submitted = false

  const antiCheat = computed(() => exam.antiCheat === 1)

  /* ---------- 作答状态 ---------- */

  const isAnswered = (question) => {
    const value = answers[question.id]
    if (value === undefined || value === null || value === '') return false

    // 程序填空有多个空，填了任意一个就算作答过
    if (question.type === 'prog_blank') {
      return Array.isArray(value) && value.some((item) => String(item ?? '').trim() !== '')
    }
    return Array.isArray(value) ? value.length > 0 : true
  }

  const answeredCount = computed(() => questions.value.filter(isAnswered).length)

  /** 按题型给出合适的初始值，并为续答补齐缺失的空位 */
  const initAnswers = () => {
    questions.value.forEach((question) => {
      const { id, type } = question

      if (type === 'multi') {
        if (!Array.isArray(answers[id])) answers[id] = []
        return
      }

      if (type === 'prog_blank') {
        const blanks = Math.max(1, Number(question.blankCount) || 1)
        if (!Array.isArray(answers[id])) {
          answers[id] = new Array(blanks).fill('')
        } else {
          while (answers[id].length < blanks) answers[id].push('')
        }
        return
      }

      if (answers[id] === undefined || answers[id] === null) {
        answers[id] = TEXT_TYPES.includes(type) ? '' : null
      }
    })
  }

  const toggleMulti = (questionId, optionIndex) => {
    if (!Array.isArray(answers[questionId])) answers[questionId] = []

    const picked = answers[questionId]
    const at = picked.indexOf(optionIndex)
    if (at >= 0) picked.splice(at, 1)
    else picked.push(optionIndex)
    picked.sort((a, b) => a - b)
  }

  /* ---------- 计时与暂存 ---------- */

  const stopTimers = () => {
    clearInterval(tickTimer)
    clearInterval(autosaveTimer)
    tickTimer = null
    autosaveTimer = null
  }

  const saveProgress = async () => {
    if (finished.value || submitted) return
    await saveExamProgress(examid, {
      answers: JSON.stringify(answers),
      switchCount: switchCount.value
    }).catch(() => {})
  }

  const startTimers = () => {
    stopTimers()

    tickTimer = setInterval(() => {
      remaining.value -= 1
      if (remaining.value > 0) return

      remaining.value = 0
      clearInterval(tickTimer)
      tickTimer = null
      ElMessage.warning('时间到，系统自动交卷')
      submit(true)
    }, TICK_INTERVAL)

    autosaveTimer = setInterval(saveProgress, AUTOSAVE_INTERVAL)
  }

  /* ---------- 开考与交卷 ---------- */

  const finish = (record) => {
    Object.assign(result, record)
    finished.value = true
    loaded.value = true
    stopTimers()
    hooks.onFinished?.(record)
  }

  const start = async () => {
    // 已交过卷就直接看成绩；没有记录属正常情况，不弹错误
    const existing = await getExamRecord(examid).catch(() => null)
    if (existing?.data?.status >= 1) {
      finish(existing.data)
      return
    }

    try {
      const res = await startExam(examid)
      const data = res?.data || {}

      Object.assign(exam, data.exam)
      try {
        questions.value = JSON.parse(data.exam?.content || '{}').questions || []
      } catch {
        questions.value = []
      }

      remaining.value = Math.max(
        0,
        Math.floor(data.remainingSeconds || (exam.duration || DEFAULT_DURATION_MINUTES) * 60)
      )

      // 续答：恢复上次暂存的作答与切屏次数
      if (data.record?.answers) {
        try {
          Object.assign(answers, JSON.parse(data.record.answers))
        } catch {
          // 暂存数据损坏时按新开考处理
        }
      }
      if (data.record?.switchCount) switchCount.value = data.record.switchCount

      initAnswers()
      loaded.value = true
      startTimers()
      return true
    } catch (e) {
      ElMessage.error(e?.msg || e?.message || '无法进入考试')
      return false
    }
  }

  /**
   * @param {boolean} auto 是否为超时自动交卷
   */
  const submit = async (auto = false) => {
    if (submitted) return false
    submitted = true
    submitting.value = true

    try {
      const duration = (exam.duration || DEFAULT_DURATION_MINUTES) * 60
      const res = await submitExam(examid, {
        answers: JSON.stringify(answers),
        switchCount: switchCount.value,
        usedSeconds: Math.max(0, duration - remaining.value),
        autoSubmit: auto ? 1 : 0
      })
      ElMessage.success('交卷成功')
      finish(res?.data || {})
      return true
    } catch (e) {
      // 交卷失败要把计时恢复起来，否则倒计时停摆、超时也不会再自动交卷，
      // 学生会在毫无提示的情况下一直停在答题页
      submitted = false
      if (!finished.value && remaining.value > 0) startTimers()
      ElMessage.error(e?.msg || e?.message || '交卷失败，请检查网络后重试')
      return false
    } finally {
      submitting.value = false
    }
  }

  /* ---------- 防作弊监控 ---------- */

  const onVisibilityChange = () => {
    if (!antiCheat.value || finished.value || !loaded.value) return

    if (document.hidden) {
      switchCount.value += 1
      warning.value = true
      ElMessage.error(`检测到离开答题页面（第 ${switchCount.value} 次），已记录`)
    } else {
      warning.value = false
    }
  }

  const onBlur = () => {
    if (antiCheat.value && loaded.value && !finished.value) warning.value = true
  }

  const onFocus = () => {
    warning.value = false
  }

  /** 关闭标签页前提示，避免误触丢失作答 */
  const onBeforeUnload = (event) => {
    if (finished.value || !loaded.value) return
    event.preventDefault()
    event.returnValue = ''
  }

  const onContextMenu = (event) => {
    if (antiCheat.value && loaded.value && !finished.value) event.preventDefault()
  }

  onMounted(() => {
    document.addEventListener('visibilitychange', onVisibilityChange)
    window.addEventListener('blur', onBlur)
    window.addEventListener('focus', onFocus)
    window.addEventListener('beforeunload', onBeforeUnload)
    document.addEventListener('contextmenu', onContextMenu)
  })

  onBeforeUnmount(() => {
    stopTimers()
    document.removeEventListener('visibilitychange', onVisibilityChange)
    window.removeEventListener('blur', onBlur)
    window.removeEventListener('focus', onFocus)
    window.removeEventListener('beforeunload', onBeforeUnload)
    document.removeEventListener('contextmenu', onContextMenu)
  })

  return {
    exam,
    questions,
    answers,
    result,
    loaded,
    finished,
    submitting,
    remaining,
    switchCount,
    warning,
    antiCheat,
    answeredCount,
    isAnswered,
    toggleMulti,
    start,
    submit,
    /** 供路由离开时判断是否还需要交卷 */
    isSubmitted: () => submitted
  }
}

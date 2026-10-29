/**
 * 智能语音引擎 VoiceEngine
 * 扫描系统语音、按质量评分排序、选择最佳中文语音
 * 支持主讲/辅助双角色、预热机制
 */

// 评分规则常量
const NEURAL_KEYWORDS = ['Neural', 'Natural', 'Premium']
// 优先选择温柔女声：Xiaoxiao（温柔女教师）、Xiaoyi（亲切女声）排在前面
// Yunxi/Yunyang 是男声，降低优先级
const PREFERRED_FEMALE_NAMES = ['Xiaoxiao', 'Xiaoyi', 'Xiaomo', 'Xiaorui', 'Xiaoshuang']
const ACCEPTABLE_NAMES = ['Yunxi', 'Yunyang', 'Google']
const NEURAL_BONUS = 30
const EXACT_LANG_BONUS = 20
const PARTIAL_LANG_BONUS = 10
const ONLINE_BONUS = 10
const PREFERRED_FEMALE_BONUS = 25  // 女声优先
const ACCEPTABLE_NAME_BONUS = 8

/**
 * 为单个语音对象计算质量评分
 * @param {SpeechSynthesisVoice} voice
 * @returns {number} 评分
 */
export function scoreVoice(voice) {
  let score = 0

  // Neural/Natural/Premium 关键词加分
  const name = voice.name || ''
  if (NEURAL_KEYWORDS.some(kw => name.includes(kw))) {
    score += NEURAL_BONUS
  }

  // 语言匹配
  const lang = (voice.lang || '').toLowerCase()
  if (lang === 'zh-cn') {
    score += EXACT_LANG_BONUS
  } else if (lang.startsWith('zh')) {
    score += PARTIAL_LANG_BONUS
  }

  // 非本地语音（通常质量更高）
  if (!voice.localService) {
    score += ONLINE_BONUS
  }

  // 已知高质量女声优先
  if (PREFERRED_FEMALE_NAMES.some(pn => name.includes(pn))) {
    score += PREFERRED_FEMALE_BONUS
  } else if (ACCEPTABLE_NAMES.some(pn => name.includes(pn))) {
    score += ACCEPTABLE_NAME_BONUS
  }

  return score
}


/**
 * 从语音列表中筛选并评分中文语音
 * @param {SpeechSynthesisVoice[]} voices
 * @returns {Array<{voice: SpeechSynthesisVoice, score: number}>}
 */
export function scoreChinese(voices) {
  if (!voices || voices.length === 0) return []

  return voices
    .filter(v => {
      const lang = (v.lang || '').toLowerCase()
      return lang.startsWith('zh')
    })
    .map(voice => ({ voice, score: scoreVoice(voice) }))
    .sort((a, b) => b.score - a.score)
}

/**
 * 创建智能语音引擎实例
 * @returns {object} VoiceEngine 接口
 */
export function createVoiceEngine() {
  let ready = false
  let scoredVoices = []
  let primaryVoice = null
  let secondaryVoice = null

  /**
   * 用给定的语音列表初始化（内部方法，也用于测试注入）
   */
  function _initWithVoices(voices) {
    scoredVoices = scoreChinese(voices)
    primaryVoice = scoredVoices.length > 0 ? scoredVoices[0].voice : null
    secondaryVoice = scoredVoices.length > 1 ? scoredVoices[1].voice : null
  }

  /**
   * 预热语音引擎 — 静默播放空文本触发引擎初始化
   */
  function _warmup() {
    if (typeof window === 'undefined' || !window.speechSynthesis) return Promise.resolve()

    return new Promise(resolve => {
      const u = new SpeechSynthesisUtterance(' ')
      u.volume = 0
      u.onend = resolve
      u.onerror = resolve
      window.speechSynthesis.speak(u)
    })
  }

  /**
   * 初始化引擎：扫描语音、评分、预热
   */
  async function init() {
    if (typeof window === 'undefined' || !window.speechSynthesis) {
      ready = false
      return
    }

    const synth = window.speechSynthesis

    // 获取语音列表（部分浏览器需要异步等待）
    let voices = synth.getVoices()
    if (!voices || voices.length === 0) {
      voices = await new Promise(resolve => {
        const onVoicesChanged = () => {
          synth.removeEventListener('voiceschanged', onVoicesChanged)
          resolve(synth.getVoices())
        }
        synth.addEventListener('voiceschanged', onVoicesChanged)
        // 超时 3 秒
        setTimeout(() => {
          synth.removeEventListener('voiceschanged', onVoicesChanged)
          resolve(synth.getVoices() || [])
        }, 3000)
      })
    }

    _initWithVoices(voices)

    // 预热
    await _warmup()
    ready = true
  }

  return {
    init,
    _initWithVoices, // 暴露给测试用

    getPrimaryVoice() {
      return primaryVoice
    },

    getSecondaryVoice() {
      return secondaryVoice
    },

    getVoice(role) {
      if (role === 'secondary' && secondaryVoice) return secondaryVoice
      return primaryVoice // primary 或 fallback
    },

    isReady() {
      return ready
    },

    getScoredVoices() {
      return [...scoredVoices]
    }
  }
}

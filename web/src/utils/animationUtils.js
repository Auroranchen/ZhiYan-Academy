/**
 * 动画工具模块 AnimationUtils
 * 参考 Remotion 的 spring 物理动画和序列化理念
 * 在 Vue 3 中用 requestAnimationFrame + CSS 实现
 */

// Spring 预设配置
export const SPRING_PRESETS = {
  smooth: { damping: 200, stiffness: 100, mass: 1 },
  snappy: { damping: 20, stiffness: 200, mass: 1 },
  bouncy: { damping: 8, stiffness: 100, mass: 1 },
  gentle: { damping: 15, stiffness: 80, mass: 2 },
}

// Chinese punctuation characters (using unicode escapes for encoding safety)
const PUNCTUATION = new Set([
  '\u3002', '\uFF0C', '\uFF01', '\uFF1F', '\uFF1B', '\uFF1A',
  '\u3001', '\u2026', '\u2014', '\u201C', '\u201D', '\u2018',
  '\u2019', '\uFF08', '\uFF09'
])

/**
 * 阻尼弹簧动画函数
 * 基于 x(t) = 1 - e^(-damping*t) * cos(sqrt(stiffness/mass) * t)
 * @param {object} config - { damping, stiffness, mass }
 * @returns {function} (t: 0-1) => value (0-1)
 */
export function springAnimation(config = SPRING_PRESETS.smooth) {
  const { damping, stiffness, mass } = config
  const omega = Math.sqrt(stiffness / mass)
  const dampingRatio = damping / (2 * Math.sqrt(stiffness * mass))

  return function (t) {
    if (t <= 0) return 0
    if (t >= 1) return 1

    // 缩放 t 到合理的时间范围
    const scaledT = t * 4

    if (dampingRatio >= 1) {
      // 过阻尼：无振荡
      return 1 - Math.exp(-dampingRatio * omega * scaledT)
    }

    // 欠阻尼：有振荡
    const dampedOmega = omega * Math.sqrt(1 - dampingRatio * dampingRatio)
    return 1 - Math.exp(-dampingRatio * omega * scaledT) * Math.cos(dampedOmega * scaledT)
  }
}


/**
 * 改进的打字机效果生成器
 * 返回一个字符延迟数组，标点位置延迟更大
 * @param {string} text - 要打字的文本
 * @param {object} options - { baseSpeed: ms/字符, punctuationPause: ms }
 * @returns {Array<{char: string, delay: number}>} 每个字符及其延迟
 */
export function typewriterEffect(text, options = {}) {
  const { baseSpeed = 70, punctuationPause = 300 } = options

  if (!text) return []

  return Array.from(text).map(char => ({
    char,
    delay: PUNCTUATION.has(char) ? punctuationPause : baseSpeed,
  }))
}

/**
 * 执行打字机动画（异步）
 * @param {string} text
 * @param {function} onChar - 每个字符的回调 (partialText, charIndex)
 * @param {object} options
 * @returns {object} { promise, cancel }
 */
export function runTypewriter(text, onChar, options = {}) {
  const chars = typewriterEffect(text, options)
  let cancelled = false
  let timeoutId = null

  const promise = new Promise((resolve) => {
    let i = 0
    let accumulated = ''

    function next() {
      if (cancelled || i >= chars.length) {
        resolve(accumulated)
        return
      }

      accumulated += chars[i].char
      onChar(accumulated, i)
      const delay = chars[i].delay
      i++
      timeoutId = setTimeout(next, delay)
    }

    next()
  })

  return {
    promise,
    cancel() {
      cancelled = true
      if (timeoutId) clearTimeout(timeoutId)
    },
  }
}

/**
 * 淡入淡出过渡工具
 * @param {HTMLElement} element
 * @param {'in' | 'out'} direction
 * @param {number} duration - ms
 * @returns {Promise}
 */
export function fadeTransition(element, direction = 'in', duration = 300) {
  if (!element) return Promise.resolve()

  return new Promise(resolve => {
    const start = direction === 'in' ? 0 : 1
    const end = direction === 'in' ? 1 : 0
    const startTime = performance.now()

    function frame(now) {
      const elapsed = now - startTime
      const progress = Math.min(elapsed / duration, 1)
      element.style.opacity = start + (end - start) * progress

      if (progress < 1) {
        requestAnimationFrame(frame)
      } else {
        resolve()
      }
    }

    requestAnimationFrame(frame)
  })
}

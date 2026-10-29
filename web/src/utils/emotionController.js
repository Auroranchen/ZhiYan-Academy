/**
 * 情感语音控制器 EmotionController
 * 根据讲解段落类型映射不同的 rate/pitch/volume 参数组合
 * 带微小随机波动，模拟自然情感变化
 */

// 段落类型枚举
export const SEGMENT_TYPES = {
  intro: 'intro',
  question: 'question',
  keypoint: 'keypoint',
  error_analysis: 'error_analysis',
  step: 'step',
  transition: 'transition',
  summary: 'summary',
}

// 每种段落类型的参数范围配置（和蔼女教师风格：语速适中偏慢、语调柔和、音量适中）
export const EMOTION_PRESETS = {
  intro:          { rate: [0.92, 0.98],  pitch: [1.02, 1.08], volume: [0.88, 0.95], pauseAfter: 600 },
  question:       { rate: [0.95, 1.0],   pitch: [1.02, 1.08], volume: [0.88, 0.95], pauseAfter: 700 },
  keypoint:       { rate: [0.88, 0.95],  pitch: [1.0, 1.05],  volume: [0.85, 0.92], pauseAfter: 600 },
  error_analysis: { rate: [0.85, 0.92],  pitch: [0.98, 1.02], volume: [0.82, 0.90], pauseAfter: 800 },
  step:           { rate: [0.88, 0.95],  pitch: [1.0, 1.02],  volume: [0.85, 0.92], pauseAfter: 500 },
  answer_reveal:  { rate: [0.88, 0.95],  pitch: [1.05, 1.10], volume: [0.90, 0.98], pauseAfter: 900 },
  ask:            { rate: [0.90, 0.98],  pitch: [1.08, 1.15], volume: [0.90, 0.98], pauseAfter: 1400 },
  code_explain:   { rate: [0.85, 0.92],  pitch: [1.0, 1.05],  volume: [0.85, 0.92], pauseAfter: 700 },
  transition:     { rate: [0.95, 1.02],  pitch: [1.02, 1.08], volume: [0.85, 0.92], pauseAfter: 400 },
  summary:        { rate: [0.92, 0.98],  pitch: [1.0, 1.05],  volume: [0.88, 0.95], pauseAfter: 900 },
}

// 默认降级配置（未知段落类型）
const DEFAULT_PRESET = { rate: [0.95, 1.05], pitch: [1.0, 1.05], volume: [0.85, 1.0], pauseAfter: 500 }

// 语音角色映射：哪些段落类型使用 primary 语音
const PRIMARY_ROLES = new Set(['question', 'intro'])

/**
 * 在 [min, max] 范围内生成随机值
 */
function randomInRange(min, max) {
  return min + Math.random() * (max - min)
}

/**
 * 创建情感语音控制器实例
 */
export function createEmotionController() {
  function getPreset(segmentType) {
    return EMOTION_PRESETS[segmentType] || DEFAULT_PRESET
  }

  return {
    /**
     * 获取指定段落类型的语音参数（含随机波动）
     * @param {string} segmentType
     * @returns {{ rate: number, pitch: number, volume: number }}
     */
    getParams(segmentType) {
      const preset = getPreset(segmentType)
      return {
        rate: randomInRange(preset.rate[0], preset.rate[1]),
        pitch: randomInRange(preset.pitch[0], preset.pitch[1]),
        volume: randomInRange(preset.volume[0], preset.volume[1]),
      }
    },

    /**
     * 获取指定段落类型后的停顿时长（ms）
     * @param {string} segmentType
     * @returns {number}
     */
    getPauseAfter(segmentType) {
      const preset = getPreset(segmentType)
      return preset.pauseAfter
    },

    /**
     * 获取指定段落类型对应的语音角色
     * @param {string} segmentType
     * @returns {'primary' | 'secondary'}
     */
    getVoiceRole(segmentType) {
      return PRIMARY_ROLES.has(segmentType) ? 'primary' : 'secondary'
    },
  }
}

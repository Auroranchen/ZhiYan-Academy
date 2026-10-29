/**
 * \u64AD\u653E\u5668\u8F85\u52A9\u51FD\u6570 - \u7528\u4E8E\u89C6\u9891\u8BB2\u89E3\u6A21\u5F0F
 */

/**
 * \u6839\u636E\u8BB2\u89E3\u6BB5\u843D\u7C7B\u578B\u6620\u5C04\u52A8\u753B\u8001\u5E08\u52A8\u4F5C
 * \u6682\u505C\u65F6\u59CB\u7EC8\u8FD4\u56DE 'idle'
 * @param {string} segmentType - \u5F53\u524D\u8BB2\u89E3\u6BB5\u843D\u7C7B\u578B
 * @param {boolean} isPaused - \u662F\u5426\u5904\u4E8E\u6682\u505C\u72B6\u6001
 * @returns {string} AnimatedTeacher \u7EC4\u4EF6\u7684\u52A8\u4F5C\u540D\u79F0
 */
export function getTeacherAction(segmentType, isPaused) {
  if (isPaused) return 'idle'
  const actionMap = {
    intro: 'teaching',
    question: 'teaching',
    error_analysis: 'thinking',
    step: 'writing',
    keypoint: 'pointing',
    answer_reveal: 'encouraging',
    ask: 'teaching',
    code_explain: 'writing',
    transition: 'encouraging',
    summary: 'encouraging',
  }
  return actionMap[segmentType] || 'idle'
}

/**
 * 判断是否应显示代码面板
 * 当作业类型为文档上传（type=2）且语言不为 plaintext 时显示，
 * 或者当题目包含参考代码时也显示
 * @param {number} homeworkType - 作业类型（1=在线测验, 2=文档上传）
 * @param {string} language - 映射后的语言标识符
 * @param {boolean} hasReferenceCode - 是否有任何题目包含参考代码
 * @returns {boolean}
 */
export function shouldShowCode(homeworkType, language, hasReferenceCode) {
  if (hasReferenceCode) return true
  return homeworkType === 2 && language !== 'plaintext'
}

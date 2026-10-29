/**
 * 题型元数据
 *
 * 题型的中文名与配色原先在答题页、考试管理、作业编辑等处各写一份对象字面量，
 * 新增题型时总有地方漏改。这里作为唯一来源。
 */

/** 后端可能返回的各种别名，统一映射到内部题型 */
const TYPE_ALIASES = {
  single_choice: 'single',
  choice: 'single',
  multi_choice: 'multi',
  multiple: 'multi',
  true_false: 'judge',
  boolean: 'judge',
  tf: 'judge',
  fill_blank: 'blank',
  fill: 'blank',
  filling: 'blank',
  short_answer: 'short',
  essay: 'short',
  subjective: 'short'
}

/** label 为完整名称，short 为空间紧张时的简称，tone 对应 el-tag 的类型 */
export const QUESTION_TYPES = {
  single: { label: '单选题', short: '单选', tone: '' },
  multi: { label: '多选题', short: '多选', tone: 'warning' },
  judge: { label: '判断题', short: '判断', tone: 'success' },
  blank: { label: '填空题', short: '填空', tone: 'info' },
  short: { label: '简答题', short: '简答', tone: 'danger' },
  prog_blank: { label: '程序填空题', short: '程序填空', tone: 'info' },
  prog: { label: '程序题', short: '程序题', tone: 'danger' },
  code_explain: { label: '代码解释题', short: '代码解释', tone: 'warning' }
}

/** 可自动判分的题型 */
export const OBJECTIVE_TYPES = ['single', 'multi', 'judge', 'blank', 'prog_blank']

/** 有选项的题型 */
export const CHOICE_TYPES = ['single', 'multi']

/** 大模型返回的题型大小写不定，统一转小写后再查别名表 */
export const normalizeQuestionType = (type) => {
  const key = typeof type === 'string' ? type.trim().toLowerCase() : ''
  if (!key) return 'single'
  return TYPE_ALIASES[key] || key
}

export const questionTypeLabel = (type) => QUESTION_TYPES[type]?.label || type
export const questionTypeShort = (type) => QUESTION_TYPES[type]?.short || type
export const questionTypeTone = (type) => QUESTION_TYPES[type]?.tone ?? ''

export const isObjectiveType = (type) => OBJECTIVE_TYPES.includes(type)
export const isChoiceType = (type) => CHOICE_TYPES.includes(type)

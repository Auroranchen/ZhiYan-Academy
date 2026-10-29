<template>
  <el-dialog
    :model-value="modelValue"
    :title="`评阅 — ${student.studentname || ''}（${student.studentno || ''}）`"
    width="900px"
    top="3vh"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div v-if="!questions.length" v-loading="true" class="placeholder"></div>

    <template v-else>
      <article
        v-for="(q, index) in questions"
        :key="q.id"
        class="question"
        :class="{ 'is-subjective': q.subjective }"
      >
        <header class="question__head">
          <span class="question__no">{{ index + 1 }}</span>
          <el-tag size="small" :type="questionTypeTone(q.type)" disable-transitions>
            {{ questionTypeShort(q.type) }}
          </el-tag>
          <span class="question__score">{{ q.score }} 分</span>

          <el-tag v-if="q.subjective" type="warning" size="small" effect="plain" disable-transitions>
            待评阅
          </el-tag>
          <el-tag
            v-else-if="q.correct !== null"
            :type="q.correct ? 'success' : 'danger'"
            size="small"
            effect="plain"
            disable-transitions
          >
            {{ q.correct ? '正确' : '错误' }}
          </el-tag>
        </header>

        <p class="question__title">{{ q.title }}</p>

        <pre v-if="q.code" class="code" v-safe-html="highlightCode(q.code)"></pre>

        <div v-if="q.options?.length" class="options">
          <div
            v-for="(option, oi) in q.options"
            :key="oi"
            class="options__row"
            :class="optionClass(q, oi)"
          >
            <span class="options__letter">{{ getOptionLabel(oi) }}</span>
            <span>{{ option }}</span>
            <span v-if="q.studentOptionIndexes?.includes(oi)" class="options__mark">学生所选</span>
          </div>
        </div>

        <div v-if="q.subjective || q.type === 'prog_blank'" class="answer">
          <p class="answer__label">学生作答</p>

          <template v-if="q.type === 'prog_blank'">
            <div v-for="(value, bi) in asArray(q.studentAnswer)" :key="bi" class="blank">
              <span class="blank__no">空 {{ bi + 1 }}：</span>
              <code>{{ value || '(未填)' }}</code>
              <span v-if="Array.isArray(q.correctAnswer) && q.correctAnswer[bi]" class="blank__ref">
                正确答案：<code>{{ q.correctAnswer[bi] }}</code>
              </span>
            </div>
          </template>

          <pre
            v-else-if="q.type === 'prog'"
            class="code"
            v-safe-html="highlightCode(String(q.studentAnswer || ''))"
          ></pre>

          <p v-else class="answer__text">{{ q.studentAnswer || '(未作答)' }}</p>

          <template v-if="q.subjective && q.correctAnswer">
            <p class="answer__label">参考答案</p>
            <pre
              v-if="q.type === 'prog'"
              class="code"
              v-safe-html="highlightCode(String(q.correctAnswer))"
            ></pre>
            <p v-else class="answer__text is-reference">{{ q.correctAnswer }}</p>
          </template>

          <div v-if="q.subjective" class="ai">
            <el-button size="small" plain :loading="q.aiLoading" @click="askAi(q)">
              AI 辅助评阅
            </el-button>
            <MarkdownView v-if="q.aiResult" :content="q.aiResult" size="compact" class="ai__result" />
          </div>
        </div>
      </article>

      <el-divider />

      <div class="grading">
        <span class="grading__objective">客观题得分 <b>{{ objectiveScore }}</b> 分</span>
        <span class="grading__label">总分</span>
        <el-input-number v-model="finalScore" :min="0" :max="totalScore" :step="1" class="grading__input" />
        <span class="grading__label">/ {{ totalScore }} 分</span>
      </div>

      <el-input
        v-model="comment"
        type="textarea"
        :rows="2"
        placeholder="教师评语（选填）"
        class="grading__comment"
      />
    </template>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交评阅</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 考试评阅弹窗（教师端）
 *
 * 客观题由系统判分并直接展示对错，主观题列出学生作答与参考答案，
 * 教师可调用 AI 辅助后给出总分。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { MarkdownView } from '@/components/ui'
import { aiReviewQuestion, getExam, getExamResults, reviewExamRecord } from '@/api/exam'
import { checkAnswerCorrect, getOptionLabel } from '@/utils/exam'
import { questionTypeShort, questionTypeTone } from '@/utils/questionTypes'
import { highlightCode } from '@/utils/codeHighlight'

const DEFAULT_TOTAL_SCORE = 100

/** 这几类必须人工过目，其余客观题系统直接判 */
const SUBJECTIVE_TYPES = ['short', 'prog', 'code_explain']

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 成绩列表里的一条作答记录 */
  record: { type: Object, default: () => ({}) },
  /** 默认值不能引用外部常量，defineProps 在编译期求值 */
  examTotalScore: { type: Number, default: 100 }
})

const emit = defineEmits(['update:modelValue', 'reviewed'])

const student = computed(() => props.record || {})
const totalScore = computed(() => props.examTotalScore || DEFAULT_TOTAL_SCORE)

const questions = ref([])
const objectiveScore = ref(0)
const finalScore = ref(0)
const comment = ref('')
const submitting = ref(false)

const asArray = (value) => (Array.isArray(value) ? value : [value])

/** 选中且正确 / 选中但错误 / 未选但是正确答案，三种状态各自配色 */
const optionClass = (question, index) => {
  const chosen = question.studentOptionIndexes?.includes(index)
  const correct = question.correctOptionIndexes?.includes(index)

  return {
    'is-right': chosen && correct,
    'is-wrong': chosen && !correct,
    'is-answer': !chosen && correct
  }
}

const buildQuestions = (exam, record) => {
  let paper = []
  try {
    paper = JSON.parse(exam.content || '{}').questions || []
  } catch {
    paper = []
  }

  let answers = {}
  try {
    answers = JSON.parse(record.answers || '{}')
  } catch {
    // 作答数据损坏时按未作答展示，不影响教师继续评阅
  }

  let objective = 0

  const rows = paper.map((q) => {
    const studentAnswer = answers[q.id]
    const subjective = SUBJECTIVE_TYPES.includes(q.type)
    const correct = subjective ? null : checkAnswerCorrect(q, studentAnswer)
    if (correct === true) objective += Number(q.score) || 0

    const toIndexes = (value) => {
      if (Array.isArray(value)) return value.map(Number)
      return value != null ? [Number(value)] : []
    }

    const hasOptions = q.type === 'single' || q.type === 'multi'

    return {
      ...q,
      studentAnswer,
      subjective,
      correct,
      studentOptionIndexes: hasOptions ? toIndexes(studentAnswer) : null,
      correctOptionIndexes: hasOptions ? toIndexes(q.answer) : null,
      correctAnswer: q.answer,
      aiLoading: false,
      aiResult: null
    }
  })

  return { rows, objective }
}

const load = async () => {
  questions.value = []
  comment.value = props.record.aiComment || ''

  const res = await getExam(props.record.examid).catch(() => null)
  if (!res?.data) {
    ElMessage.error('加载考试详情失败')
    emit('update:modelValue', false)
    return
  }

  const { rows, objective } = buildQuestions(res.data, props.record)
  questions.value = rows
  objectiveScore.value = objective
  // 已评过的沿用原分数，未评过的以客观题得分打底
  finalScore.value = Number(props.record.score) || objective
}

watch(
  () => [props.modelValue, props.record?.id],
  ([visible]) => {
    if (visible && props.record?.examid) load()
  },
  { immediate: true }
)

const askAi = async (question) => {
  question.aiLoading = true
  try {
    const asText = (value) => (typeof value === 'string' ? value : JSON.stringify(value ?? ''))
    const res = await aiReviewQuestion({
      title: question.title,
      type: question.type,
      code: question.code || '',
      refAnswer: asText(question.correctAnswer),
      studentAnswer: asText(question.studentAnswer),
      maxScore: String(question.score || 10)
    })

    question.aiResult = res?.data || '（AI 无返回结果）'

    // AI 会在文末给出建议分数，抽出来提示教师，但不自动填入
    const suggested = question.aiResult.match(/建议得分[：:]\s*(\d+(?:\.\d+)?)\s*分/)
    if (suggested) ElMessage.info(`AI 建议该题得分：${suggested[1]} 分`)
  } catch (e) {
    question.aiResult = `AI 评阅暂时不可用：${e?.msg || e?.message || '未知错误'}`
  } finally {
    question.aiLoading = false
  }
}

const submit = async () => {
  submitting.value = true
  try {
    await reviewExamRecord(props.record.id, {
      score: finalScore.value,
      comment: comment.value || null
    })
    ElMessage.success('评阅成功')
    emit('update:modelValue', false)

    const refreshed = await getExamResults(props.record.examid).catch(() => null)
    emit('reviewed', refreshed?.data || null)
  } catch (e) {
    ElMessage.error(`评阅失败：${e?.msg || e?.message || '请稍后重试'}`)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.placeholder {
  height: 200px;
}

.question {
  padding: var(--zy-space-4);
  margin-bottom: var(--zy-space-3);
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-md);
}

.question.is-subjective {
  background: var(--zy-warning-soft);
  border-color: var(--zy-warning-border);
}

.question__head {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
}

.question__no {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  font-size: var(--zy-font-size-xs);
  font-weight: var(--zy-font-weight-bold);
  color: var(--zy-text-inverse);
  background: var(--zy-brand-base);
  border-radius: var(--zy-radius-full);
}

.question__score {
  margin-right: auto;
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.question__title {
  margin: var(--zy-space-2) 0;
  font-size: var(--zy-font-size-base);
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-primary);
  white-space: pre-wrap;
}

.code {
  margin: var(--zy-space-2) 0;
  padding: var(--zy-space-3);
  overflow-x: auto;
  font-family: var(--zy-font-mono);
  font-size: var(--zy-font-size-sm);
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-palette-neutral-100);
  background: var(--zy-palette-neutral-950);
  border-radius: var(--zy-radius-md);
}

/* ===== 选项 ===== */
.options {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-1);
}

.options__row {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  padding: var(--zy-space-1) var(--zy-space-2);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
  border-radius: var(--zy-radius-sm);
}

.options__row.is-right {
  color: var(--zy-success-base);
  background: var(--zy-success-soft);
}

.options__row.is-wrong {
  color: var(--zy-danger-base);
  background: var(--zy-danger-soft);
}

.options__row.is-answer {
  color: var(--zy-success-base);
}

.options__letter {
  font-weight: var(--zy-font-weight-semibold);
}

.options__mark {
  margin-left: auto;
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

/* ===== 作答 ===== */
.answer {
  margin-top: var(--zy-space-3);
}

.answer__label {
  margin: var(--zy-space-2) 0 var(--zy-space-1);
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.answer__text {
  margin: 0;
  padding: var(--zy-space-3);
  font-size: var(--zy-font-size-sm);
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-primary);
  background: var(--zy-bg-surface);
  border-radius: var(--zy-radius-md);
  white-space: pre-wrap;
}

.answer__text.is-reference {
  color: var(--zy-text-secondary);
  background: var(--zy-success-soft);
}

.blank {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--zy-space-2);
  margin-bottom: var(--zy-space-1);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.blank__no {
  color: var(--zy-text-tertiary);
}

.blank__ref {
  margin-left: auto;
  color: var(--zy-success-base);
}

.ai {
  margin-top: var(--zy-space-3);
}

.ai__result {
  margin-top: var(--zy-space-2);
  padding: var(--zy-space-3);
  background: var(--zy-bg-surface);
  border-radius: var(--zy-radius-md);
}

/* ===== 打分 ===== */
.grading {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--zy-space-2);
}

.grading__objective {
  margin-right: var(--zy-space-5);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.grading__objective b {
  font-size: var(--zy-font-size-lg);
  color: var(--zy-brand-base);
}

.grading__label {
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-tertiary);
}

.grading__input {
  width: 130px;
}

.grading__comment {
  margin-top: var(--zy-space-3);
}
</style>

<template>
  <PageLayout title="AI 智能练习" description="AI 根据你的错题薄弱点定制练习，完成可得积分。">
    <template #actions>
      <el-select v-model="courseid" placeholder="按课程筛选错题" clearable class="filter-course">
        <el-option :value="null" label="全部错题" />
        <el-option
          v-for="c in courses"
          :key="c.courseid"
          :label="`${c.coursename} (${c.count})`"
          :value="c.courseid"
        />
      </el-select>
      <el-select v-model="count" class="filter-count">
        <el-option v-for="n in COUNT_OPTIONS" :key="n" :value="n" :label="`${n} 题`" />
      </el-select>
      <el-button type="primary" :loading="generating" @click="generate">
        {{ questions.length ? '换一套' : '开始练习' }}
      </el-button>
    </template>

    <el-empty
      v-if="!questions.length && !generating"
      description="点击「开始练习」，AI 将针对你的薄弱点出题"
    />

    <div v-loading="generating" element-loading-text="AI 正在为你出题…">
      <div ref="quizRef" class="quiz">
        <h2 v-if="submitted" class="quiz__title">
          AI 智能练习 · 成绩 {{ correctCount }}/{{ questions.length }}
        </h2>

        <el-alert
          v-if="questions.length && !basedOnWrong"
          type="info"
          :closable="false"
          show-icon
          title="你暂时没有错题记录，本次为综合基础练习"
          class="quiz__alert"
        />

        <article
          v-for="(q, index) in questions"
          :key="index"
          class="question"
          :class="submitted && (results[index] ? 'is-right' : 'is-wrong')"
        >
          <header class="question__head">
            <span class="question__index">第 {{ index + 1 }} 题</span>
            <el-tag size="small" effect="plain" disable-transitions>
              {{ TYPE_LABEL[q.type] || '题目' }}
            </el-tag>
            <el-tag v-if="q.knowledge" size="small" type="info" effect="plain" disable-transitions>
              {{ q.knowledge }}
            </el-tag>
            <el-tag v-if="q.sourceWrongId" size="small" type="warning" effect="plain" disable-transitions>
              源自错题
            </el-tag>
            <el-tag
              v-if="submitted"
              size="small"
              :type="results[index] ? 'success' : 'danger'"
              disable-transitions
            >
              {{ results[index] ? '正确' : '错误' }}
            </el-tag>
          </header>

          <p class="question__title">{{ q.title }}</p>

          <el-radio-group
            v-if="q.type === 'single'"
            v-model="answers[index]"
            :disabled="submitted"
            class="options"
          >
            <el-radio v-for="(option, oi) in q.options" :key="oi" :value="oi" class="options__item">
              {{ getOptionLabel(oi) }}. {{ option }}
            </el-radio>
          </el-radio-group>

          <el-checkbox-group
            v-else-if="q.type === 'multi'"
            v-model="answers[index]"
            :disabled="submitted"
            class="options"
          >
            <el-checkbox v-for="(option, oi) in q.options" :key="oi" :value="oi" class="options__item">
              {{ getOptionLabel(oi) }}. {{ option }}
            </el-checkbox>
          </el-checkbox-group>

          <el-radio-group
            v-else-if="q.type === 'judge'"
            v-model="answers[index]"
            :disabled="submitted"
            class="options"
          >
            <el-radio :value="true" class="options__item">正确</el-radio>
            <el-radio :value="false" class="options__item">错误</el-radio>
          </el-radio-group>

          <el-input
            v-else
            v-model="answers[index]"
            :disabled="submitted"
            placeholder="请输入答案"
            class="options__blank"
          />

          <div v-if="submitted" class="explain">
            <p class="explain__answer">
              参考答案：<b>{{ formatAnswerText(q, q.answer, ANSWER_LABELS) }}</b>
            </p>
            <p v-if="q.analysis" class="explain__text">解析：{{ q.analysis }}</p>
          </div>
        </article>
      </div>

      <footer v-if="questions.length" class="actions">
        <el-button v-if="!submitted" type="primary" size="large" @click="submit">提交答案</el-button>

        <template v-else>
          <span class="actions__score">
            本次得分 <b>{{ correctCount }}</b> / {{ questions.length }}
          </span>
          <span v-if="archivedCount" class="actions__archived">
            已自动攻克 {{ archivedCount }} 道错题
          </span>
          <el-button :loading="exporting" @click="exportPdf">导出本套</el-button>
          <el-button type="primary" plain @click="generate">再练一套</el-button>
        </template>
      </footer>
    </div>
  </PageLayout>
</template>

<script setup>
/** AI 智能练习（学生端） */
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { PageLayout } from '@/components/ui'
import {
  listPracticeCourses,
  generateSmartPractice,
  reportPracticeResult
} from '@/api/practice'
import { checkAnswerCorrect, formatAnswerText, getOptionLabel } from '@/utils/exam'

const COUNT_OPTIONS = [3, 5, 8, 10]
const TYPE_LABEL = { single: '单选题', multi: '多选题', judge: '判断题', blank: '填空题' }
const ANSWER_LABELS = { trueLabel: '正确', falseLabel: '错误', emptyLabel: '—' }

/** AI 生成的填空答案用词较随意，判分放宽到 loose */
const PRACTICE_BLANK_POLICY = 'loose'

const route = useRoute()

const courses = ref([])
const courseid = ref(null)
const count = ref(5)

const questions = ref([])
const answers = ref([])
const results = ref([])
const basedOnWrong = ref(true)

const generating = ref(false)
const exporting = ref(false)
const submitted = ref(false)
const correctCount = ref(0)
const archivedCount = ref(0)
const quizRef = ref(null)

onMounted(async () => {
  const res = await listPracticeCourses().catch(() => null)
  courses.value = res?.data || []

  // 错题本的「一键 AI 智能练习」会带上课程并要求直接出题
  if (route.query.courseid) courseid.value = Number(route.query.courseid)
  if (route.query.auto === '1') generate()
})

/** 不同题型的空答案形态不同，多选是数组、填空是字符串 */
const emptyAnswer = (type) => (type === 'multi' ? [] : type === 'blank' ? '' : null)

const generate = async () => {
  generating.value = true
  submitted.value = false
  archivedCount.value = 0

  try {
    const res = await generateSmartPractice(count.value, courseid.value)
    questions.value = res?.data?.questions || []
    basedOnWrong.value = res?.data?.basedOnWrong !== false
    answers.value = questions.value.map((q) => emptyAnswer(q.type))
    results.value = []
    if (!questions.value.length) ElMessage.warning('未能生成题目，请重试')
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    generating.value = false
  }
}

const submit = async () => {
  results.value = questions.value.map(
    (q, i) => checkAnswerCorrect({ ...q, gradingPolicy: PRACTICE_BLANK_POLICY }, answers.value[i]) === true
  )

  correctCount.value = results.value.filter(Boolean).length
  submitted.value = true

  const masteredWrongIds = questions.value
    .filter((q, i) => results.value[i] && q.sourceWrongId != null)
    .map((q) => q.sourceWrongId)

  // 积分与错题归档失败不影响本地成绩展示，静默降级
  const res = await reportPracticeResult({
    correct: correctCount.value,
    total: questions.value.length,
    masteredWrongIds
  }).catch(() => null)

  archivedCount.value = res?.data?.archived || 0

  ElMessage.success(
    archivedCount.value
      ? `答对 ${correctCount.value}/${questions.value.length} 题，积分已发放，攻克 ${archivedCount.value} 道错题`
      : `答对 ${correctCount.value}/${questions.value.length} 题，积分已发放`
  )
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const exportPdf = async () => {
  if (!quizRef.value) return
  exporting.value = true

  try {
    const html2pdf = (await import('html2pdf.js')).default
    await html2pdf()
      .set({
        margin: 10,
        filename: 'AI智能练习.pdf',
        image: { type: 'jpeg', quality: 0.95 },
        html2canvas: { scale: 2, useCORS: true },
        jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' },
        pagebreak: { mode: ['avoid-all', 'css'] }
      })
      .from(quizRef.value)
      .save()
  } catch {
    ElMessage.error('导出失败，请重试')
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped>
.filter-course {
  width: 200px;
}

.filter-count {
  width: 110px;
}

.quiz__title {
  margin: 0 0 var(--zy-space-4);
  font-size: var(--zy-font-size-xl);
  font-weight: var(--zy-font-weight-bold);
  color: var(--zy-text-primary);
}

.quiz__alert {
  margin-bottom: var(--zy-space-4);
}

.question {
  padding: var(--zy-space-4);
  margin-bottom: var(--zy-space-3);
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-lg);
  transition: border-color var(--zy-duration-normal), background var(--zy-duration-normal);
  page-break-inside: avoid;
}

.question.is-right {
  background: var(--zy-success-soft);
  border-color: var(--zy-success-border);
}

.question.is-wrong {
  background: var(--zy-danger-soft);
  border-color: var(--zy-danger-border);
}

.question__head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--zy-space-2);
  margin-bottom: var(--zy-space-2);
}

.question__index {
  font-weight: var(--zy-font-weight-bold);
  color: var(--zy-text-primary);
}

.question__title {
  margin: 0 0 var(--zy-space-3);
  font-size: var(--zy-font-size-md);
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-primary);
  white-space: pre-wrap;
}

.options {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-1);
}

.options__item {
  height: auto;
  margin: 0;
  align-items: flex-start;
  white-space: normal;
}

.options__blank {
  max-width: 420px;
}

.explain {
  margin-top: var(--zy-space-3);
  padding: var(--zy-space-3);
  background: var(--zy-bg-surface);
  border: 1px dashed var(--zy-border-strong);
  border-radius: var(--zy-radius-md);
}

.explain__answer {
  margin: 0;
  font-size: var(--zy-font-size-base);
  color: var(--zy-text-secondary);
}

.explain__answer b {
  color: var(--zy-success-base);
}

.explain__text {
  margin: var(--zy-space-1) 0 0;
  font-size: var(--zy-font-size-sm);
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-secondary);
}

.actions {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: var(--zy-space-4);
  margin-top: var(--zy-space-5);
  padding-top: var(--zy-space-4);
  border-top: 1px solid var(--zy-border-subtle);
}

.actions__score {
  font-size: var(--zy-font-size-lg);
  color: var(--zy-text-secondary);
}

.actions__score b {
  font-size: var(--zy-font-size-3xl);
  color: var(--zy-brand-base);
}

.actions__archived {
  padding: var(--zy-space-1) var(--zy-space-3);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-success-base);
  background: var(--zy-success-soft);
  border: 1px solid var(--zy-success-border);
  border-radius: var(--zy-radius-full);
}

@media screen and (max-width: 768px) {
  .filter-course,
  .filter-count {
    width: 100%;
  }
}
</style>

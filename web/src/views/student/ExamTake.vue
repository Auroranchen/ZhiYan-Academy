<template>
  <div class="exam-take" :class="{ blurred: warning }">
    <!-- 已交卷 → 成绩视图 -->
    <div v-if="finished" class="result-view">
      <el-result :icon="resultIcon" :title="`得分 ${result.score} / ${result.examTotalScore || exam.totalScore}`" :sub-title="result.aiComment">
        <template #extra>
          <div class="result-meta">
            <span>用时：{{ formatDuration(result.usedSeconds) }}</span>
            <span v-if="result.switchCount > 0" class="cheat">切屏 {{ result.switchCount }} 次</span>
            <span v-if="result.autoSubmit === 1" class="cheat">超时自动交卷</span>
          </div>
          <el-button type="primary" @click="goBack">返回考试列表</el-button>
        </template>
      </el-result>
    </div>

    <!-- 答题视图 -->
    <template v-else-if="loaded">
      <div class="exam-top">
        <div class="et-left">
          <h2>{{ exam.title }}</h2>
          <div class="et-info">
            <span class="et-course">{{ exam.coursename }}</span>
            <span class="et-sep">·</span>
            <span>共 <b>{{ questions.length }}</b> 题</span>
            <span class="et-sep">·</span>
            <span>满分 <b>{{ exam.totalScore }}</b> 分</span>
          </div>
        </div>
        <div class="et-right">
          <div class="timer-box" :class="{ urgent: isUrgent }">
            <Icon icon="ph:timer-duotone" :width="20" :height="20" />
            <span class="timer-val">{{ formatClock(remaining) }}</span>
          </div>
          <div v-if="antiCheat" class="cheat-badge">
            <Icon icon="ph:shield-warning-duotone" :width="14" :height="14" />
            <span>监考中 · 切屏 {{ switchCount }} 次</span>
          </div>
        </div>
      </div>

      <el-alert v-if="antiCheat" type="warning" :closable="false" show-icon class="anti-tip">
        本场考试已开启防作弊监考：切换标签页 / 最小化窗口将被记录，请勿离开答题页面。
      </el-alert>

      <!-- 答题卡快捷导航 -->
      <div class="answer-sheet">
        <span class="as-label">答题卡</span>
        <div class="as-dots">
          <span v-for="(q, idx) in questions" :key="q.id" class="as-dot" :class="{ done: isAnswered(q) }" @click="scrollToQuestion(idx)">
            {{ idx + 1 }}
          </span>
        </div>
        <span class="as-count">{{ answeredCount }}/{{ questions.length }}</span>
      </div>

      <div class="questions">
        <div v-for="(q, idx) in questions" :key="q.id" class="q-card" :id="`q-${idx}`" :style="{ animationDelay: Math.min(idx, 10) * 0.05 + 's' }">
          <div class="q-rail"></div>
          <div class="q-head">
            <span class="q-index">{{ idx + 1 }}</span>
            <span class="q-type-tag" :class="'t-' + q.type">{{ typeLabel(q.type) }}</span>
            <span class="q-score">{{ q.score }} 分</span>
            <span v-if="isAnswered(q)" class="q-done-tag">已答</span>
          </div>
          <div class="q-title">{{ q.title }}</div>

          <!-- 代码块（程序填空/程序题/代码解释题） -->
          <pre v-if="q.code" class="q-code">{{ q.code }}</pre>

          <div class="q-body">
            <el-radio-group v-if="q.type === 'single'" v-model="answers[q.id]" class="q-opt-list">
              <label v-for="(op, oi) in q.options" :key="oi" class="q-opt-item" :class="{ active: answers[q.id] === oi }" @click="answers[q.id] = oi">
                <span class="opt-badge">{{ letter(oi) }}</span>
                <span class="opt-text">{{ op }}</span>
              </label>
            </el-radio-group>

            <el-checkbox-group v-else-if="q.type === 'multi'" v-model="answers[q.id]" class="q-opt-list">
              <label v-for="(op, oi) in q.options" :key="oi" class="q-opt-item multi" :class="{ active: Array.isArray(answers[q.id]) && answers[q.id].includes(oi) }" @click.prevent="toggleMulti(q.id, oi)">
                <span class="opt-badge">{{ letter(oi) }}</span>
                <span class="opt-text">{{ op }}</span>
              </label>
            </el-checkbox-group>

            <div v-else-if="q.type === 'judge'" class="q-judge-row">
              <label class="judge-btn" :class="{ active: answers[q.id] === true }" @click="answers[q.id] = true">
                <Icon icon="ph:check-circle-duotone" :width="20" /> 正确
              </label>
              <label class="judge-btn wrong" :class="{ active: answers[q.id] === false }" @click="answers[q.id] = false">
                <Icon icon="ph:x-circle-duotone" :width="20" /> 错误
              </label>
            </div>

            <el-input v-else-if="q.type === 'blank'" v-model="answers[q.id]" placeholder="请输入答案" class="blank-input" />

            <!-- 程序填空题：多个空 -->
            <div v-else-if="q.type === 'prog_blank'" class="prog-blank-list">
              <div v-for="(_, bi) in (Array.isArray(answers[q.id]) ? answers[q.id] : [])" :key="bi" class="prog-blank-row">
                <span class="pb-tag">空 {{ bi + 1 }}</span>
                <el-input v-model="answers[q.id][bi]" placeholder="请填写此空答案" class="code-input" />
              </div>
            </div>

            <!-- 程序题：写代码 -->
            <el-input v-else-if="q.type === 'prog'" v-model="answers[q.id]" type="textarea" :rows="10" placeholder="请在此编写代码..." class="code-input short-input" />

            <!-- 代码解释题：文字解释 -->
            <el-input v-else-if="q.type === 'code_explain'" v-model="answers[q.id]" type="textarea" :rows="6" placeholder="请解释上述代码的功能、执行流程或输出结果..." class="short-input" />

            <el-input v-else v-model="answers[q.id]" type="textarea" :rows="5" placeholder="请在此作答..." class="short-input" />
          </div>
        </div>
      </div>

      <div class="exam-bottom">
        <div class="eb-progress">
          <div class="eb-bar">
            <div class="eb-fill" :style="{ width: progressPercent + '%' }"></div>
          </div>
          <span class="eb-text">已答 <b>{{ answeredCount }}</b> / {{ questions.length }}</span>
        </div>
        <el-button type="primary" size="large" :loading="submitting" @click="confirmSubmit" round>
          <Icon icon="ph:paper-plane-tilt-duotone" :width="18" style="margin-right: 4px" /> 交卷
        </el-button>
      </div>
    </template>

    <div v-else class="loading-block" v-loading="true" element-loading-text="加载试卷中..."></div>
  </div>
</template>

<script setup>
/** 学生在线答题 */
import { computed, onMounted } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Icon } from '@iconify/vue'
import { useExamSession } from '@/composables/useExamSession'
import { confirmOrAbort } from '@/utils/confirm'
import { getOptionLabel } from '@/utils/exam'
import { questionTypeShort } from '@/utils/questionTypes'

/** 达到满分的六成显示成功图标，否则用警示色 */
const PASS_RATIO = 0.6
const URGENT_SECONDS = 60
const DEFAULT_TOTAL_SCORE = 100

const route = useRoute()
const router = useRouter()
const examid = Number(route.query.examid)

const goBack = () => router.push('/student/exam')

const {
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
  isSubmitted
} = useExamSession(examid)

const letter = (index) => getOptionLabel(index)
const typeLabel = questionTypeShort

const isUrgent = computed(() => remaining.value <= URGENT_SECONDS)

const progressPercent = computed(
  () => (answeredCount.value / Math.max(questions.value.length, 1)) * 100
)

const resultIcon = computed(() => {
  const total = result.examTotalScore || exam.totalScore || DEFAULT_TOTAL_SCORE
  return result.score >= total * PASS_RATIO ? 'success' : 'warning'
})

const formatClock = (seconds) => {
  const minutes = Math.floor(seconds / 60)
  return `${String(minutes).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}

const formatDuration = (seconds) => {
  if (!seconds) return '—'
  return `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`
}

const scrollToQuestion = (index) => {
  document.getElementById(`q-${index}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

const confirmSubmit = async () => {
  const unanswered = questions.value.length - answeredCount.value
  const ok = await confirmOrAbort(
    unanswered > 0 ? `还有 ${unanswered} 题未作答，确定交卷吗？` : '确定交卷吗？',
    '交卷确认'
  )
  if (ok) submit(false)
}

onMounted(async () => {
  if (!examid) {
    ElMessage.error('缺少考试参数')
    goBack()
    return
  }
  const ok = await start()
  if (ok === false) setTimeout(goBack, 1500)
})

onBeforeRouteLeave(async () => {
  if (finished.value || !loaded.value || isSubmitted()) return true

  const ok = await confirmOrAbort('离开将自动交卷，确定离开吗？', '离开考试')
  if (!ok) return false

  await submit(false)
  return true
})
</script>

<style scoped src="./styles/exam-take.css"></style>

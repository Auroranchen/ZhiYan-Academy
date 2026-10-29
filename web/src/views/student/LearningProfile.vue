<template>
  <PageLayout
    title="对话式学习画像"
    description="和「小枢」自然聊聊，AI 自动构建你的 6 维动态画像，随学随新。"
  >
    <template #actions>
      <el-tag size="small" type="info" effect="plain" round disable-transitions>
        AI 生成内容，仅供参考
      </el-tag>
      <el-button @click="reset">重置画像</el-button>
    </template>

    <div class="layout">
      <!-- 对话区 -->
      <section class="chat">
        <div ref="messagesRef" class="chat__messages">
          <div v-for="(m, i) in messages" :key="i" class="message" :class="`is-${m.role}`">
            <div class="message__avatar">{{ m.role === 'assistant' ? '衍' : '我' }}</div>
            <div class="message__bubble">
              <MarkdownView v-if="m.role === 'assistant'" :content="m.content" size="compact" />
              <span v-else>{{ m.content }}</span>
            </div>
          </div>

          <div v-if="sending" class="message is-assistant">
            <div class="message__avatar">衍</div>
            <div class="message__bubble typing">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>

        <div class="chat__quick">
          <el-tag
            v-for="q in QUICK_ASKS"
            :key="q"
            class="chat__quick-item"
            effect="plain"
            disable-transitions
            @click="send(q)"
          >
            {{ q }}
          </el-tag>
        </div>

        <div class="chat__input">
          <el-input
            v-model="input"
            type="textarea"
            :rows="2"
            resize="none"
            :placeholder="
              recognizing
                ? '正在聆听，请说话…'
                : '例如：我是大二学生，主修计算机，想加强数据结构，平时偏好看视频学习…'
            "
            @keyup.enter.exact.prevent="send()"
          />
          <VoiceButton
            :supported="speechSupported"
            :recording="recognizing"
            :disabled="sending"
            @toggle="toggleVoiceInput"
          />
          <el-button type="primary" :loading="sending" @click="send()">发送</el-button>
        </div>
      </section>

      <!-- 画像区 -->
      <section class="profile">
        <p class="profile__summary">
          <Icon icon="ph:identification-card-duotone" />
          <span>{{ summary || '画像将随对话逐步完善…' }}</span>
        </p>

        <div ref="radarRef" class="profile__radar"></div>

        <div class="dimensions">
          <div v-for="d in dimensions" :key="d.key" class="dimension">
            <div class="dimension__head">
              <span class="dimension__name">{{ d.name }}</span>
              <span class="dimension__score">{{ d.score }}</span>
            </div>
            <el-progress :percentage="d.score" :stroke-width="6" :show-text="false" />
            <p class="dimension__label">{{ d.label }}</p>
            <p v-if="d.note" class="dimension__note">{{ d.note }}</p>
          </div>
        </div>

        <el-divider content-position="left">学习效果评估</el-divider>

        <div class="evaluate">
          <el-select v-model="evalCourse" placeholder="全部课程" clearable class="evaluate__select">
            <el-option
              v-for="c in courses"
              :key="c.courseid"
              :label="c.coursename"
              :value="c.courseid"
            />
          </el-select>
          <el-button type="primary" plain :loading="evaluating" @click="runEvaluate">
            评估并动态调整
          </el-button>
        </div>

        <div v-if="evalResult" class="result">
          <div class="result__kpis">
            <div class="kpi">
              <p class="kpi__value">{{ evalResult.mastery ?? '—' }}</p>
              <p class="kpi__label">整体掌握度</p>
            </div>
            <div class="kpi">
              <p class="kpi__value">{{ evalResult.trend || '—' }}</p>
              <p class="kpi__label">学习趋势</p>
            </div>
          </div>

          <p class="result__summary">{{ evalResult.summary }}</p>

          <div v-if="evalResult.focusPoints?.length" class="block">
            <h4 class="block__title">下一步重点</h4>
            <div class="block__tags">
              <el-tag
                v-for="(point, i) in evalResult.focusPoints"
                :key="i"
                type="warning"
                effect="light"
                disable-transitions
              >
                {{ point }}
              </el-tag>
            </div>
          </div>

          <div v-if="evalResult.actions?.length" class="block">
            <h4 class="block__title">推荐学习动作</h4>
            <ul class="block__actions">
              <li v-for="(a, i) in evalResult.actions" :key="i">
                <el-tag size="small" effect="plain" disable-transitions>{{ a.resourceType }}</el-tag>
                {{ a.action }}
              </li>
            </ul>
          </div>

          <el-button type="primary" link @click="goAgent">据此生成个性化资源包 →</el-button>
        </div>

        <div v-if="evalHistory.length" class="block history">
          <h4 class="block__title">掌握度变化记录</h4>
          <div class="history__track">
            <div v-for="item in reversedHistory" :key="item.id" class="history__point">
              <span class="history__score">{{ item.mastery ?? 0 }}%</span>
              <div class="history__bar">
                <i :style="{ height: `${Math.max(6, item.mastery || 0)}%` }"></i>
              </div>
              <small>{{ item.sourceType === 'resource_quiz' ? '资源练习' : '综合评估' }}</small>
            </div>
          </div>
        </div>

        <el-divider content-position="left">练习记录</el-divider>

        <div class="block">
          <div class="block__head">
            <h4 class="block__title">资源包练习作答历史</h4>
            <el-button link :loading="practice.loading" @click="loadPractice">刷新</el-button>
          </div>

          <el-empty
            v-if="!practice.loading && !practice.rows.length"
            description="暂无练习记录"
            :image-size="56"
          />

          <div v-else v-loading="practice.loading" class="attempts">
            <div v-for="row in practice.rows" :key="row.id" class="attempt">
              <p class="attempt__title">{{ row.resourceTitle || `资源 #${row.resourceId}` }}</p>
              <div class="attempt__meta">
                <el-tag size="small" effect="plain" disable-transitions>
                  {{ row.courseName || (row.courseId ? `课程 ${row.courseId}` : '未关联课程') }}
                </el-tag>
                <span>得分 {{ row.score ?? '—' }}</span>
                <span>{{ row.correctCount ?? 0 }}/{{ row.totalCount ?? 0 }}</span>
                <span class="attempt__time">{{ formatDate(row.createTime, 'minute') }}</span>
              </div>
            </div>
          </div>

          <el-pagination
            v-if="practice.total > PRACTICE_PAGE_SIZE"
            v-model:current-page="practice.page"
            small
            layout="prev, pager, next"
            :total="practice.total"
            :page-size="PRACTICE_PAGE_SIZE"
            class="attempts__pager"
            @current-change="loadPractice"
          />
        </div>
      </section>
    </div>
  </PageLayout>
</template>

<script setup>
/** 对话式学习画像（学生端） */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Icon } from '@iconify/vue'
import { MarkdownView, PageLayout, VoiceButton } from '@/components/ui'
import { useChart } from '@/composables/useChart'
import { listStudentInsightCourses } from '@/api/aiInsight'
import {
  chatWithProfileAssistant,
  evaluateLearning,
  getLearningProfile,
  listEvaluations,
  listPracticeAttempts,
  resetLearningProfile
} from '@/api/learningProfile'
import { formatDate } from '@/utils/format'
import { confirmOrAbort } from '@/utils/confirm'
import { useVoiceInput } from '@/utils/useVoiceInput'

const PRACTICE_PAGE_SIZE = 10

const DIMENSIONS = [
  { key: 'knowledgeBase', name: '知识基础' },
  { key: 'cognitiveStyle', name: '认知风格' },
  { key: 'errorPreference', name: '易错点偏好' },
  { key: 'learningGoal', name: '学习目标' },
  { key: 'learningPace', name: '学习节奏' },
  { key: 'interest', name: '兴趣方向' }
]

const GREETING = {
  role: 'assistant',
  content:
    '你好呀～我是你的**学习画像助手 · 小枢** 👋\n\n简单聊几句，我就能帮你建立专属学习画像。先说说看：**你是什么专业、几年级，最近最想提升哪门课？** 平时更喜欢看视频、读文档，还是动手敲代码？'
}

const QUICK_ASKS = [
  '我主修计算机，大二，想加强数据结构和算法',
  '我偏好看视频学习，做题容易在边界条件出错',
  '我每天能投入约 1 小时，目标是期末拿高分'
]

const router = useRouter()

const messages = ref([GREETING])
const input = ref('')
const sending = ref(false)
const messagesRef = ref(null)

const summary = ref('')
const scores = reactive({})

const { speechSupported, recognizing, toggleVoiceInput, stopVoiceInput } = useVoiceInput(input)

const dimensions = computed(() =>
  DIMENSIONS.map(({ key, name }) => {
    const value = scores[key] || {}
    return {
      key,
      name,
      score: Number(value.score) || 0,
      label: value.label || '待完善',
      note: value.note || ''
    }
  })
)

/* ---------- 雷达图 ---------- */

const radarRef = ref(null)

useChart(radarRef, (palette) => ({
  radar: {
    indicator: DIMENSIONS.map(({ name }) => ({ name, max: 100 })),
    radius: '66%',
    splitNumber: 4,
    axisName: { color: palette.textMuted, fontSize: 12 },
    splitLine: { lineStyle: { color: palette.split } },
    splitArea: { areaStyle: { color: [palette.surface, 'transparent'] } },
    axisLine: { lineStyle: { color: palette.split } }
  },
  series: [
    {
      type: 'radar',
      symbol: 'circle',
      symbolSize: 5,
      itemStyle: { color: palette.brand },
      lineStyle: { color: palette.brand, width: 2 },
      areaStyle: { opacity: 0.18, color: palette.brand },
      data: [{ value: dimensions.value.map((d) => d.score) }]
    }
  ]
}))

/* ---------- 画像 ---------- */

const applyProfile = (data) => {
  if (data?.dimensions) {
    DIMENSIONS.forEach(({ key }) => {
      scores[key] = data.dimensions[key] || scores[key] || {}
    })
  }
  if (data?.summary != null) summary.value = data.summary
}

const loadProfile = async () => {
  const res = await getLearningProfile().catch(() => null)
  applyProfile(res?.data)
}

const scrollToBottom = () =>
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })

const send = async (preset) => {
  const text = (preset || input.value).trim()
  if (!text || sending.value) return

  if (recognizing.value) stopVoiceInput()

  messages.value.push({ role: 'student', content: text })
  input.value = ''
  scrollToBottom()
  sending.value = true

  try {
    // 刚推入的这条要单独作为 message 传，历史里不能重复带上
    const history = messages.value.slice(0, -1).map((m) => ({ role: m.role, content: m.content }))
    const res = await chatWithProfileAssistant(history, text)
    messages.value.push({ role: 'assistant', content: res?.data?.reply || '我们继续聊聊吧～' })
    applyProfile(res?.data)
  } catch {
    messages.value.push({ role: 'assistant', content: '抱歉，我刚走神了，请再说一遍～' })
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

const reset = async () => {
  const ok = await confirmOrAbort('确定清空当前学习画像？对话也会重新开始。', '重置画像')
  if (!ok) return

  await resetLearningProfile()
  DIMENSIONS.forEach(({ key }) => {
    scores[key] = {}
  })
  summary.value = ''
  evalResult.value = null
  messages.value = [GREETING]
  ElMessage.success('已重置')
}

/* ---------- 学习效果评估 ---------- */

const courses = ref([])
const evalCourse = ref(null)
const evaluating = ref(false)
const evalResult = ref(null)
const evalHistory = ref([])

const reversedHistory = computed(() => [...evalHistory.value].reverse())

const loadEvalHistory = async () => {
  const res = await listEvaluations(evalCourse.value).catch(() => null)
  evalHistory.value = res?.data || []
}

const runEvaluate = async () => {
  evaluating.value = true
  try {
    const res = await evaluateLearning(evalCourse.value)
    evalResult.value = res?.data || null
    // 评估会动态回写画像维度，需要重新拉一次
    await Promise.all([loadProfile(), loadEvalHistory()])
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    evaluating.value = false
  }
}

const goAgent = () => {
  const focusPoints = evalResult.value?.focusPoints || []
  router.push({
    path: '/student/agent',
    query: {
      ...(evalCourse.value ? { courseid: evalCourse.value } : {}),
      ...(focusPoints.length ? { focusPoints: focusPoints.join('|') } : {}),
      source: 'profile'
    }
  })
}

/* ---------- 练习记录 ---------- */

const practice = reactive({ rows: [], total: 0, page: 1, loading: false })

const loadPractice = async () => {
  practice.loading = true
  try {
    const res = await listPracticeAttempts({
      page: practice.page,
      pageSize: PRACTICE_PAGE_SIZE
    }).catch(() => null)
    practice.rows = res?.data || []
    practice.total = Number(res?.total || 0)
  } finally {
    practice.loading = false
  }
}

onMounted(() => {
  loadProfile()
  loadEvalHistory()
  loadPractice()
  listStudentInsightCourses()
    .then((res) => {
      courses.value = res?.data || []
    })
    .catch(() => {})
})
</script>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  gap: var(--zy-space-4);
}

/* ===== 对话 ===== */
.chat {
  display: flex;
  flex-direction: column;
  height: 64vh;
  overflow: hidden;
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-surface);
}

.chat__messages {
  flex: 1;
  padding: var(--zy-space-4);
  overflow-y: auto;
  background: var(--zy-bg-subtle);
}

.message {
  display: flex;
  gap: var(--zy-space-2);
  margin-bottom: var(--zy-space-3);
}

.message.is-student {
  flex-direction: row-reverse;
}

.message__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  font-size: var(--zy-font-size-sm);
  font-weight: var(--zy-font-weight-bold);
  color: var(--zy-text-inverse);
  background: var(--zy-brand-base);
  border-radius: var(--zy-radius-md);
}

.message.is-student .message__avatar {
  background: var(--zy-palette-brand-800);
}

.message__bubble {
  max-width: 78%;
  padding: var(--zy-space-2) var(--zy-space-3);
  font-size: var(--zy-font-size-base);
  line-height: var(--zy-line-height-relaxed);
  border-radius: var(--zy-radius-lg);
}

.message.is-assistant .message__bubble {
  color: var(--zy-text-primary);
  background: var(--zy-bg-surface);
  border: 1px solid var(--zy-border-subtle);
  border-top-left-radius: var(--zy-radius-xs);
}

.message.is-student .message__bubble {
  color: var(--zy-text-inverse);
  background: var(--zy-brand-base);
  border-top-right-radius: var(--zy-radius-xs);
}

.typing {
  display: flex;
  align-items: center;
  gap: var(--zy-space-1);
}

.typing span {
  width: 6px;
  height: 6px;
  background: var(--zy-brand-base);
  border-radius: var(--zy-radius-full);
  animation: typing-bounce 1s infinite;
}

.typing span:nth-child(2) {
  animation-delay: 0.15s;
}

.typing span:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes typing-bounce {
  0%,
  80%,
  100% {
    transform: translateY(0);
    opacity: 0.5;
  }

  40% {
    transform: translateY(-5px);
    opacity: 1;
  }
}

.chat__quick {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zy-space-1);
  padding: var(--zy-space-2) var(--zy-space-3);
  border-top: 1px solid var(--zy-border-subtle);
}

.chat__quick-item {
  cursor: pointer;
}

.chat__input {
  display: flex;
  align-items: flex-end;
  gap: var(--zy-space-2);
  padding: var(--zy-space-3);
  border-top: 1px solid var(--zy-border-subtle);
}

.chat__input :deep(.el-textarea) {
  flex: 1;
}

/* ===== 画像 ===== */
.profile {
  height: 64vh;
  padding: var(--zy-space-4);
  overflow-y: auto;
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-surface);
}

.profile__summary {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  margin: 0;
  padding: var(--zy-space-3);
  font-size: var(--zy-font-size-sm);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-brand-base);
  background: var(--zy-brand-soft);
  border-radius: var(--zy-radius-md);
}

.profile__summary svg {
  flex-shrink: 0;
  font-size: 20px;
}

.profile__radar {
  width: 100%;
  height: 260px;
}

.dimensions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--zy-space-2) var(--zy-space-4);
}

.dimension__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.dimension__name {
  font-size: var(--zy-font-size-sm);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-secondary);
}

.dimension__score {
  font-size: var(--zy-font-size-sm);
  font-weight: var(--zy-font-weight-bold);
  color: var(--zy-brand-base);
  font-variant-numeric: tabular-nums;
}

.dimension__label {
  margin: var(--zy-space-1) 0 0;
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-secondary);
}

.dimension__note {
  margin: 2px 0 0;
  font-size: var(--zy-font-size-xs);
  line-height: var(--zy-line-height-normal);
  color: var(--zy-text-tertiary);
}

/* ===== 评估 ===== */
.evaluate {
  display: flex;
  gap: var(--zy-space-2);
}

.evaluate__select {
  width: 180px;
}

.result {
  margin-top: var(--zy-space-3);
}

.result__kpis {
  display: flex;
  gap: var(--zy-space-3);
  margin-bottom: var(--zy-space-3);
}

.kpi {
  flex: 1;
  padding: var(--zy-space-3);
  text-align: center;
  background: var(--zy-bg-sunken);
  border-radius: var(--zy-radius-md);
}

.kpi__value {
  margin: 0;
  font-size: var(--zy-font-size-2xl);
  font-weight: var(--zy-font-weight-bold);
  color: var(--zy-success-base);
}

.kpi__label {
  margin: 2px 0 0;
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.result__summary {
  margin: 0 0 var(--zy-space-3);
  font-size: var(--zy-font-size-sm);
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-secondary);
}

.block {
  margin-bottom: var(--zy-space-3);
}

.block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.block__title {
  margin: 0 0 var(--zy-space-2);
  font-size: var(--zy-font-size-sm);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.block__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zy-space-1);
}

.block__actions {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-1);
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: var(--zy-font-size-sm);
  line-height: var(--zy-line-height-normal);
  color: var(--zy-text-secondary);
}

/* ===== 掌握度变化 ===== */
.history {
  padding-top: var(--zy-space-3);
  border-top: 1px dashed var(--zy-border-default);
}

.history__track {
  display: flex;
  align-items: flex-end;
  gap: var(--zy-space-2);
  height: 130px;
  overflow-x: auto;
}

.history__point {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: var(--zy-space-1);
  min-width: 48px;
  height: 100%;
}

.history__score {
  font-size: var(--zy-font-size-xs);
  font-weight: var(--zy-font-weight-bold);
  color: var(--zy-brand-base);
}

.history__bar {
  display: flex;
  align-items: flex-end;
  width: 20px;
  height: 82px;
  overflow: hidden;
  background: var(--zy-brand-soft);
  border-radius: var(--zy-radius-sm);
}

.history__bar i {
  display: block;
  width: 100%;
  background: var(--zy-brand-base);
  border-radius: var(--zy-radius-sm) var(--zy-radius-sm) 0 0;
}

.history__point small {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
  white-space: nowrap;
}

/* ===== 练习记录 ===== */
.attempts {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-2);
}

.attempt {
  padding: var(--zy-space-3);
  background: var(--zy-bg-sunken);
  border-radius: var(--zy-radius-md);
}

.attempt__title {
  margin: 0 0 var(--zy-space-1);
  font-size: var(--zy-font-size-sm);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.attempt__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--zy-space-1) var(--zy-space-3);
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.attempt__time {
  margin-left: auto;
}

.attempts__pager {
  justify-content: flex-end;
  margin-top: var(--zy-space-2);
}

@media screen and (max-width: 992px) {
  .layout {
    grid-template-columns: 1fr;
  }

  .chat,
  .profile {
    height: auto;
  }

  .chat__messages {
    height: 46vh;
  }

  .dimensions {
    grid-template-columns: 1fr;
  }
}
</style>

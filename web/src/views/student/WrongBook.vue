<template>
  <PageLayout title="我的错题本" description="归集作业与考试中的错题，按课程分析掌握度并针对性练习。">
    <template #actions>
      <el-button :disabled="!rows.length" @click="exportCsv">导出错题</el-button>
      <el-button :loading="collecting" @click="collect">重新归集</el-button>
      <el-button type="primary" :disabled="!wrongCount" @click="goPractice">AI 智能练习</el-button>
    </template>

    <template #summary>
      <StatTile label="待攻克错题" :value="wrongCount" :tone="wrongCount ? 'danger' : 'success'" />
      <StatTile label="已掌握" :value="masteredCount" tone="success" />
      <StatTile label="整体掌握率" :value="masteryRate" unit="%" tone="brand" />
    </template>

    <SectionCard v-if="heatmap.length" title="掌握度热力图">
      <template #action>
        <span class="hint">颜色越红越薄弱</span>
      </template>
      <div class="heat">
        <div v-for="row in heatmap" :key="row.courseid" class="heat__row">
          <span class="heat__course" :title="row.coursename">{{ row.coursename || '未知课程' }}</span>

          <span class="heat__cell" :style="heatStyle(row.accuracy)">
            <b>{{ row.accuracy }}%</b><small>掌握度</small>
          </span>

          <span class="heat__cell" :class="{ 'is-zero': !row.wrongUnmastered }">
            <b>{{ row.wrongUnmastered || 0 }}</b><small>待攻克</small>
          </span>

          <span class="heat__chips">
            <el-tooltip
              v-for="(item, i) in row.topWrong"
              :key="i"
              :content="`${item.title}（错 ${item.cnt} 次）`"
              placement="top"
            >
              <span class="heat__chip">{{ truncate(item.title) }}</span>
            </el-tooltip>
            <span v-if="!(row.topWrong || []).length" class="hint">暂无高频错题</span>
          </span>

          <el-button
            size="small"
            type="danger"
            plain
            :disabled="!row.wrongUnmastered"
            @click="goPractice(row.courseid)"
          >
            针对练习
          </el-button>
        </div>
      </div>
    </SectionCard>

    <div class="layout">
      <SectionCard
        title="知识点掌握画像"
        :empty="!mastery.length"
        empty-text="暂无可分析的客观题数据"
        empty-icon="ph:chart-pie-slice-duotone"
      >
        <div ref="radarRef" class="chart"></div>
      </SectionCard>

      <SectionCard title="错题清单" :count="rows.length || null">
        <template #action>
          <el-button
            link
            type="success"
            :disabled="!unmasteredCount"
            @click="masterAll"
          >
            全部标记已掌握
          </el-button>
        </template>

        <div class="filters">
          <el-select v-model="filters.courseid" placeholder="全部课程" clearable size="small" @change="load">
            <el-option v-for="c in courseOptions" :key="c.courseid" :label="c.coursename" :value="c.courseid" />
          </el-select>
          <el-select v-model="filters.mastered" placeholder="全部状态" clearable size="small" @change="load">
            <el-option label="待攻克" :value="0" />
            <el-option label="已掌握" :value="1" />
          </el-select>
          <el-select v-model="sortBy" size="small">
            <el-option v-for="s in SORTS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </div>

        <div v-loading="loading" class="questions">
          <el-empty v-if="!loading && !sortedRows.length" description="太棒了，没有错题" :image-size="80" />

          <article
            v-for="item in sortedRows"
            :key="item.id"
            class="question"
            :class="{ 'is-mastered': item.mastered === 1 }"
          >
            <header class="question__head">
              <el-tag size="small" :type="TYPE_TONE[item.questionType] || 'info'" disable-transitions>
                {{ TYPE_LABEL[item.questionType] || item.questionType }}
              </el-tag>
              <span class="question__course">{{ item.coursename || '未知课程' }}</span>
              <el-tag v-if="item.mastered === 1" size="small" type="success" effect="dark" disable-transitions>
                已掌握
              </el-tag>
            </header>

            <p class="question__title">{{ item.questionTitle }}</p>

            <ul v-if="item.optionsArr.length" class="question__options">
              <li v-for="(option, i) in item.optionsArr" :key="i">
                {{ String.fromCharCode(65 + i) }}. {{ option }}
              </li>
            </ul>

            <div v-if="!item.practiceMode" class="question__answers">
              <p class="answer answer--wrong">
                <span class="answer__label">你的答案</span>{{ item.studentAnswer || '未作答' }}
              </p>
              <p class="answer answer--right">
                <span class="answer__label">正确答案</span>{{ item.correctAnswer }}
              </p>
            </div>
            <el-input
              v-else
              v-model="item.practiceInput"
              size="small"
              placeholder="重新作答后点「对照答案」"
            />

            <footer class="question__actions">
              <el-button link type="primary" @click="togglePractice(item)">
                {{ item.practiceMode ? '对照答案' : '重练此题' }}
              </el-button>
              <el-button v-if="item.mastered !== 1" link type="success" @click="master(item)">标记已掌握</el-button>
              <el-button v-else link type="warning" @click="unmaster(item)">取消掌握</el-button>
              <el-button link type="danger" @click="remove(item)">移除</el-button>
            </footer>
          </article>
        </div>
      </SectionCard>
    </div>
  </PageLayout>
</template>

<script setup>
/** 错题本与知识点掌握画像（学生端） */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PageLayout, SectionCard, StatTile } from '@/components/ui'
import { useChart } from '@/composables/useChart'
import {
  collectWrongQuestions,
  getMasteryHeatmap,
  getMasteryProfile,
  listWrongQuestions,
  markMastered,
  markUnmastered,
  removeWrongQuestion
} from '@/api/wrongBook'
import { downloadCsv } from '@/utils/excel'
import { formatDate, toDate } from '@/utils/format'

const TYPE_LABEL = { single: '单选', multi: '多选', judge: '判断', blank: '填空' }
const TYPE_TONE = { single: 'primary', multi: 'warning', judge: 'success', blank: 'info' }

const SORTS = [
  { value: 'time', label: '最近错误' },
  { value: 'type', label: '按题型' },
  { value: 'course', label: '按课程' }
]

const router = useRouter()

const rows = ref([])
const mastery = ref([])
const heatmap = ref([])
const wrongCount = ref(0)
const masteredCount = ref(0)

const loading = ref(false)
const collecting = ref(false)
const sortBy = ref('time')
const filters = reactive({ courseid: null, mastered: null })

const masteryRate = computed(() => {
  const total = wrongCount.value + masteredCount.value
  return total > 0 ? Math.round((masteredCount.value * 100) / total) : 0
})

const unmasteredCount = computed(() => rows.value.filter((i) => i.mastered !== 1).length)

const sortedRows = computed(() => {
  const list = [...rows.value]
  if (sortBy.value === 'type') {
    return list.sort((a, b) => (a.questionType || '').localeCompare(b.questionType || ''))
  }
  if (sortBy.value === 'course') {
    return list.sort((a, b) => (a.coursename || '').localeCompare(b.coursename || ''))
  }
  return list.sort((a, b) => (toDate(b.lastWrongTime)?.getTime() || 0) - (toDate(a.lastWrongTime)?.getTime() || 0))
})

/** 课程下拉同时取自错题与掌握画像，避免筛掉后选项消失 */
const courseOptions = computed(() => {
  const map = new Map()
  ;[...rows.value, ...mastery.value].forEach((i) => {
    if (i.courseid) map.set(i.courseid, i.coursename)
  })
  return [...map].map(([courseid, coursename]) => ({ courseid, coursename }))
})

const truncate = (text, max = 14) =>
  String(text || '').length > max ? `${String(text).slice(0, max)}…` : String(text || '')

/** 掌握度 0→120 映射为红到绿 */
const heatStyle = (accuracy) => {
  const value = Math.max(0, Math.min(100, Number(accuracy) || 0))
  const hue = Math.round(value * 1.2)
  return { background: `hsla(${hue}, 70%, 92%, 1)`, color: `hsl(${hue}, 60%, 28%)` }
}

/** 选项以 JSON 字符串存储，历史数据可能不合法 */
const parseItem = (raw) => {
  let options = []
  try {
    options = raw.optionsJson ? JSON.parse(raw.optionsJson) : []
  } catch {
    options = []
  }
  return {
    ...raw,
    optionsArr: Array.isArray(options) ? options : [],
    practiceMode: false,
    practiceInput: ''
  }
}

const load = async () => {
  loading.value = true
  try {
    const params = {}
    if (filters.courseid) params.courseid = filters.courseid
    if (filters.mastered !== null && filters.mastered !== '') params.mastered = filters.mastered

    const res = await listWrongQuestions(params).catch(() => null)
    const data = res?.data || {}
    rows.value = (data.list || []).map(parseItem)
    wrongCount.value = data.wrongCount || 0
    masteredCount.value = data.masteredCount || 0
  } finally {
    loading.value = false
  }
}

const refreshAll = async () => {
  await load()
  const [masteryRes, heatmapRes] = await Promise.all([
    getMasteryProfile().catch(() => null),
    getMasteryHeatmap().catch(() => null)
  ])
  mastery.value = masteryRes?.data || []
  heatmap.value = heatmapRes?.data || []
}

onMounted(refreshAll)

/* ---------- 雷达图 ---------- */

const radarRef = ref(null)

useChart(radarRef, (palette) => ({
  tooltip: {},
  radar: {
    indicator: mastery.value.map((m) => ({ name: m.coursename || '课程', max: 100 })),
    radius: '62%',
    axisName: { color: palette.textMuted, fontSize: 11 },
    splitLine: { lineStyle: { color: palette.split } },
    axisLine: { lineStyle: { color: palette.split } },
    splitArea: { areaStyle: { color: [palette.surface, palette.brand + '14'] } }
  },
  series: [
    {
      type: 'radar',
      areaStyle: { opacity: 0.25 },
      lineStyle: { color: palette.brand },
      itemStyle: { color: palette.brand },
      data: [{ value: mastery.value.map((m) => m.accuracy), name: '正确率(%)' }]
    }
  ]
}))

/* ---------- 操作 ---------- */

const collect = async () => {
  collecting.value = true
  try {
    const res = await collectWrongQuestions()
    ElMessage.success(res?.msg || '归集完成')
    await refreshAll()
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    collecting.value = false
  }
}

/** 收起练习模式时顺手对照一次答案，给个即时反馈 */
const togglePractice = (item) => {
  item.practiceMode = !item.practiceMode
  if (item.practiceMode || !item.practiceInput) return

  const answered = item.practiceInput.trim().toLowerCase()
  const correct = (item.correctAnswer || '').toLowerCase()
  if (answered && correct.includes(answered)) ElMessage.success('回答正确，可以标记为已掌握')
  else ElMessage.warning('再想想，对照正确答案巩固一下')
}

const master = async (item) => {
  try {
    const res = await markMastered(item.id)
    ElMessage.success(res?.msg || '已掌握')
    await refreshAll()
  } catch {
    // 失败提示由 http 拦截器统一处理
  }
}

const unmaster = async (item) => {
  try {
    await markUnmastered(item.id)
    await refreshAll()
  } catch {
    // 失败提示由 http 拦截器统一处理
  }
}

const remove = async (item) => {
  try {
    await removeWrongQuestion(item.id)
    ElMessage.success('已移除')
    await refreshAll()
  } catch {
    // 失败提示由 http 拦截器统一处理
  }
}

const masterAll = async () => {
  const targets = rows.value.filter((i) => i.mastered !== 1)
  if (!targets.length) return

  try {
    await ElMessageBox.confirm(
      `确定把当前 ${targets.length} 道错题全部标记为已掌握吗？`,
      '批量操作',
      { type: 'warning' }
    )
  } catch {
    return
  }

  // 逐条上报，个别失败不影响其余；如实回报结果而非一律报成功
  const results = await Promise.allSettled(
    targets.map((i) => markMastered(i.id, { suppressMessage: true }))
  )
  const failed = results.filter((r) => r.status === 'rejected').length

  if (failed) ElMessage.warning(`${targets.length - failed} 道已标记，${failed} 道失败`)
  else ElMessage.success(`已标记 ${targets.length} 道为掌握`)

  await refreshAll()
}

const goPractice = (courseid) => {
  if (!wrongCount.value) {
    ElMessage.info('当前没有待攻克的错题')
    return
  }
  const query = { auto: '1' }
  // 热力图的「针对练习」会传课程；顶部按钮沿用当前筛选
  const target = typeof courseid === 'number' ? courseid : filters.courseid
  if (target) query.courseid = target
  router.push({ path: '/student/ai-practice', query })
}

const exportCsv = () => {
  downloadCsv({
    headers: ['课程', '题型', '题干', '正确答案', '你的答案', '状态', '最近错误时间'],
    rows: sortedRows.value.map((i) => [
      i.coursename || '',
      TYPE_LABEL[i.questionType] || i.questionType,
      i.questionTitle || '',
      i.correctAnswer || '',
      i.studentAnswer || '',
      i.mastered === 1 ? '已掌握' : '待攻克',
      formatDate(i.lastWrongTime, 'minute')
    ]),
    fileName: '我的错题本.csv'
  })
}
</script>

<style scoped>
.hint {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

/* ===== 热力图 ===== */
.heat {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-2);
}

.heat__row {
  display: grid;
  grid-template-columns: minmax(120px, 1.2fr) 92px 92px minmax(0, 2fr) auto;
  align-items: center;
  gap: var(--zy-space-3);
}

.heat__course {
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.heat__cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--zy-space-2);
  border-radius: var(--zy-radius-md);
  background: var(--zy-bg-sunken);
  color: var(--zy-text-secondary);
  line-height: 1.2;
}

.heat__cell b {
  font-size: var(--zy-font-size-md);
  font-variant-numeric: tabular-nums;
}

.heat__cell small {
  font-size: 11px;
  opacity: 0.8;
}

.heat__cell.is-zero {
  opacity: 0.5;
}

.heat__chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zy-space-1);
  min-width: 0;
}

.heat__chip {
  padding: 2px var(--zy-space-2);
  border-radius: var(--zy-radius-full);
  background: var(--zy-danger-soft);
  color: var(--zy-danger-base);
  font-size: var(--zy-font-size-xs);
}

/* ===== 主体两列 ===== */
.layout {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 3fr);
  gap: var(--zy-stack-gap);
  align-items: start;
}

.chart {
  height: 320px;
}

.filters {
  display: flex;
  gap: var(--zy-space-2);
  margin-bottom: var(--zy-space-3);
  flex-wrap: wrap;
}

.filters :deep(.el-select) {
  width: 130px;
}

/* ===== 错题条目 ===== */
.questions {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-3);
  min-height: 160px;
  max-height: 640px;
  overflow-y: auto;
}

.question {
  padding: var(--zy-space-3);
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-md);
  background: var(--zy-bg-subtle);
}

.question.is-mastered {
  opacity: 0.72;
}

.question__head {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  margin-bottom: var(--zy-space-2);
}

.question__course {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.question__title {
  margin: 0;
  font-size: var(--zy-font-size-base);
  line-height: var(--zy-line-height-normal);
  color: var(--zy-text-primary);
}

.question__options {
  margin: var(--zy-space-2) 0 0;
  padding-left: var(--zy-space-4);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.question__answers {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zy-space-4);
  margin-top: var(--zy-space-2);
}

.answer {
  margin: 0;
  font-size: var(--zy-font-size-sm);
}

.answer__label {
  margin-right: var(--zy-space-2);
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.answer--wrong {
  color: var(--zy-danger-base);
}

.answer--right {
  color: var(--zy-success-base);
}

.question__actions {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  margin-top: var(--zy-space-2);
}

@media screen and (max-width: 1024px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .heat__row {
    grid-template-columns: 1fr 1fr;
    row-gap: var(--zy-space-2);
  }
}
</style>

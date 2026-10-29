<template>
  <PageLayout
    title="统计报表"
    description="按学期与课程查看作业完成情况、成绩分布、错题率与课程评价。"
    :breadcrumb="[{ label: '教学管理' }, { label: '统计报表' }]"
  >
    <template #actions>
      <el-select v-model="semester" placeholder="选择学期" class="picker">
        <el-option v-for="s in SEMESTERS" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <el-select v-model="courseId" placeholder="全部课程" clearable class="picker">
        <el-option v-for="c in semesterCourses" :key="c.courseid" :label="c.coursename" :value="c.courseid" />
      </el-select>
      <el-button @click="printReport">打印</el-button>
      <el-button :disabled="!evaluations.length" @click="exportEvaluations">导出评价</el-button>
    </template>

    <template #summary>
      <StatTile label="覆盖学生" :value="kpi.studentCount" icon="ph:users-three-duotone" />
      <StatTile label="作业总数" :value="kpi.homeworkCount" icon="ph:files-duotone" />
      <StatTile label="作业提交率" :value="kpi.submitRate" unit="%" icon="ph:paper-plane-tilt-duotone" tone="success" />
      <StatTile label="批改平均分" :value="kpi.avgScore" icon="ph:medal-duotone" />
      <StatTile
        label="待批改"
        :value="kpi.pendingGrade"
        icon="ph:pencil-line-duotone"
        :tone="kpi.pendingGrade ? 'danger' : 'default'"
        to="/teacher/studenthomework"
      />
    </template>

    <SectionCard
      title="成绩趋势（按作业）"
      :empty="!trend.length"
      empty-text="暂无已批改的作业成绩"
      empty-icon="ph:chart-line-duotone"
    >
      <template #action>
        <span v-if="trend.length" class="hint">最近 {{ trend.length }} 次已批改作业</span>
      </template>
      <div ref="trendChartRef" class="chart"></div>
    </SectionCard>

    <div class="grid">
      <SectionCard title="成绩区间分布">
        <div ref="rangeChartRef" class="chart"></div>
      </SectionCard>

      <SectionCard title="作业错题率分析">
        <template #action>
          <span v-if="overallErrorRate" class="hint">总体错误率 {{ overallErrorRate }}</span>
        </template>
        <el-select v-model="homeworkId" placeholder="选择作业" clearable class="hw-picker">
          <el-option v-for="hw in courseHomeworks" :key="hw.homeworkid" :label="hw.title" :value="hw.homeworkid" />
        </el-select>
        <div ref="errorChartRef" class="chart"></div>
      </SectionCard>
    </div>

    <SectionCard
      title="课程评价"
      :count="evaluationStats.totalCount || null"
      :empty="!courseId"
      empty-text="请选择课程后查看评价"
      empty-icon="ph:star-duotone"
    >
      <div class="eval-summary">
        <StatTile label="教学态度" :value="formatScore(evaluationStats.avgTeaching)" />
        <StatTile label="内容质量" :value="formatScore(evaluationStats.avgContent)" />
        <StatTile label="难度适中" :value="formatScore(evaluationStats.avgDifficulty)" />
        <StatTile label="综合评分" :value="formatScore(evaluationStats.avgOverall)" tone="brand" />
        <StatTile label="评价人数" :value="evaluationStats.totalCount" />
      </div>

      <DataTable
        v-model:page="evaluationPage"
        v-model:page-size="evaluationPageSize"
        :columns="evaluationColumns"
        :data="evaluations"
        :loading="evaluationLoading"
        :total="evaluationTotal"
        empty-text="该课程还没有学生评价"
        @change="loadEvaluations"
      >
        <template #time="{ row }">{{ formatDate(row.createtime, 'minute') }}</template>
      </DataTable>
    </SectionCard>
  </PageLayout>
</template>

<script setup>
/** 教师统计报表 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useStore } from 'vuex'
import { useRoute } from 'vue-router'
import { DataTable, PageLayout, SectionCard, StatTile } from '@/components/ui'
import { useChart } from '@/composables/useChart'
import { getErrorRate, getScoreRangeStatistics, getTeacherDashboard } from '@/api/statistics'
import { getCourseEvaluationStats, listCourseEvaluations, listCoursesByTeacher } from '@/api/course'
import { getHomework, listHomeworkByCourse } from '@/api/homework'
import { downloadCsv } from '@/utils/excel'
import { formatDate, formatScore } from '@/utils/format'
import { getCurrentSemester } from '@/utils/semester'

const SEMESTERS = [
  { label: '全部学期', value: 'ALL' },
  ...['2026-2027', '2025-2026', '2024-2025', '2023-2024', '2022-2023'].flatMap((year) => [
    { label: `${year} 第二学期`, value: `${year}-2` },
    { label: `${year} 第一学期`, value: `${year}-1` }
  ])
]

const evaluationColumns = [
  { prop: 'studentname', label: '学生', width: 140 },
  { prop: 'overallscore', label: '综合评分', width: 110, align: 'right' },
  { prop: 'teachingscore', label: '教学态度', width: 110, align: 'right' },
  { prop: 'contentscore', label: '内容质量', width: 110, align: 'right' },
  { prop: 'difficultyscore', label: '难度适中', width: 110, align: 'right' },
  { prop: 'comment', label: '文字意见', minWidth: 220 },
  { label: '时间', width: 170, slot: 'time' }
]

const store = useStore()
const route = useRoute()
const teacherid = store.state.user?.teacherid

const semester = ref(getCurrentSemester())
const courseId = ref(null)
const homeworkId = ref(null)

const courses = ref([])
const homeworks = ref([])

const semesterCourses = computed(() => {
  if (!semester.value || semester.value === 'ALL') return courses.value
  return courses.value.filter((c) => c.semester === semester.value)
})

/** 历史作业可能没有 semester 字段，此时回退到所属课程的学期 */
const courseHomeworks = computed(() => {
  if (!courseId.value) return []
  const list = homeworks.value.filter((h) => h.courseid === courseId.value)
  if (!semester.value || semester.value === 'ALL') return list

  const courseSemester = courses.value.find((c) => c.courseid === courseId.value)?.semester
  return list.filter((h) => (h.semester ? h.semester === semester.value : courseSemester === semester.value))
})

/* ---------- 指标与图表数据 ---------- */

const kpi = reactive({ studentCount: 0, homeworkCount: 0, submitRate: 0, avgScore: 0, pendingGrade: 0 })
const trend = ref([])
const scoreRange = ref([])
const errorQuestions = ref([])
const overallErrorRate = ref('')

const statsParams = computed(() => {
  const params = { teacherid }
  if (courseId.value) params.courseid = courseId.value
  if (semester.value && semester.value !== 'ALL') params.semester = semester.value
  return params
})

const loadDashboard = async () => {
  if (!teacherid) return
  const res = await getTeacherDashboard(statsParams.value).catch(() => null)
  const data = res?.data || {}

  kpi.studentCount = data.studentCount || 0
  kpi.homeworkCount = data.homeworkCount || 0
  kpi.submitRate = Number(data.submitRate || 0)
  kpi.avgScore = Number(data.avgScore || 0)
  kpi.pendingGrade = data.pendingGrade || 0
  trend.value = Array.isArray(data.trend) ? data.trend : []
}

const loadScoreRange = async () => {
  const res = await getScoreRangeStatistics(statsParams.value).catch(() => null)
  scoreRange.value = res?.data || []
}

const loadHomeworks = async () => {
  if (!courseId.value) {
    homeworks.value = []
    return
  }
  const res = await listHomeworkByCourse(courseId.value).catch(() => null)
  homeworks.value = res?.data || []
}

const loadErrorRate = async () => {
  if (!homeworkId.value) {
    errorQuestions.value = []
    overallErrorRate.value = ''
    return
  }
  const res = await getErrorRate(homeworkId.value).catch(() => null)
  const data = res?.data || {}
  errorQuestions.value = Array.isArray(data.questions) ? data.questions : []
  overallErrorRate.value = data.overallErrorRate != null ? `${data.overallErrorRate}%` : ''
}

/* ---------- 课程评价 ---------- */

const evaluations = ref([])
const evaluationTotal = ref(0)
const evaluationLoading = ref(false)
const evaluationPage = ref(1)
const evaluationPageSize = ref(10)
const evaluationStats = reactive({
  avgTeaching: 0,
  avgContent: 0,
  avgDifficulty: 0,
  avgOverall: 0,
  totalCount: 0
})

const resetEvaluations = () => {
  evaluations.value = []
  evaluationTotal.value = 0
  Object.assign(evaluationStats, {
    avgTeaching: 0,
    avgContent: 0,
    avgDifficulty: 0,
    avgOverall: 0,
    totalCount: 0
  })
}

const loadEvaluations = async () => {
  if (!courseId.value) {
    resetEvaluations()
    return
  }

  evaluationLoading.value = true
  try {
    const [statsRes, listRes] = await Promise.all([
      getCourseEvaluationStats(courseId.value).catch(() => null),
      listCourseEvaluations(courseId.value, {
        page: evaluationPage.value,
        pageSize: evaluationPageSize.value
      }).catch(() => null)
    ])

    Object.assign(evaluationStats, {
      avgTeaching: statsRes?.data?.avgTeaching || 0,
      avgContent: statsRes?.data?.avgContent || 0,
      avgDifficulty: statsRes?.data?.avgDifficulty || 0,
      avgOverall: statsRes?.data?.avgOverall || 0,
      totalCount: statsRes?.data?.totalCount || 0
    })

    evaluations.value = listRes?.data || []
    evaluationTotal.value = listRes?.total || 0
  } finally {
    evaluationLoading.value = false
  }
}

/* ---------- 联动 ---------- */
/* 原实现把加载顺序写成层层嵌套的调用链，改课程和改学期各维护一份几乎重复的流程。
   这里改为按依赖声明监听：谁变了就只重跑依赖它的那部分。 */

watch(semester, () => {
  courseId.value = semesterCourses.value[0]?.courseid || null
  loadDashboard()
  loadScoreRange()
})

watch(courseId, async () => {
  homeworkId.value = null
  evaluationPage.value = 1
  await Promise.all([loadDashboard(), loadScoreRange(), loadHomeworks(), loadEvaluations()])
  homeworkId.value = courseHomeworks.value[0]?.homeworkid || null
})

watch(homeworkId, loadErrorRate)

onMounted(async () => {
  const res = await listCoursesByTeacher(teacherid).catch(() => null)
  courses.value = res?.data || []

  // 从首页「查看作业统计」跳转过来时，按作业反推学期与课程
  const targetHomeworkId = route.query?.homeworkid ? Number(route.query.homeworkid) : null
  if (targetHomeworkId) {
    const hw = await getHomework(targetHomeworkId).catch(() => null)
    if (hw?.data?.courseid) {
      if (hw.data.semester) semester.value = hw.data.semester
      courseId.value = hw.data.courseid
      await loadHomeworks()
      homeworkId.value = targetHomeworkId
      return
    }
  }

  courseId.value = semesterCourses.value[0]?.courseid || null
  if (!courseId.value) {
    await Promise.all([loadDashboard(), loadScoreRange()])
  }
})

/* ---------- 图表 ---------- */

const trendChartRef = ref(null)
const rangeChartRef = ref(null)
const errorChartRef = ref(null)

useChart(trendChartRef, (palette) => {
  const list = trend.value
  return {
    tooltip: {
      trigger: 'axis',
      formatter: (points) => {
        const item = list[points[0].dataIndex] || {}
        const lines = points.map((p) => `${p.marker}${p.seriesName}：${p.data}`)
        return `${item.title || ''}<br/>${item.coursename || ''} · 已批 ${item.gradedCount || 0} 份<br/>${lines.join('<br/>')}`
      }
    },
    legend: { top: 0, textStyle: { color: palette.textMuted } },
    grid: { left: 48, right: 20, bottom: 70, top: 36 },
    xAxis: {
      type: 'category',
      data: list.map((t) => t.title || ''),
      axisLabel: {
        interval: 0,
        rotate: list.length > 5 ? 28 : 0,
        color: palette.textMuted,
        formatter: (v) => (v.length > 9 ? `${v.slice(0, 9)}…` : v)
      },
      axisLine: { lineStyle: { color: palette.axis } }
    },
    yAxis: {
      type: 'value',
      max: 100,
      name: '分数',
      nameTextStyle: { color: palette.textMuted },
      axisLabel: { color: palette.textMuted },
      splitLine: { lineStyle: { color: palette.split } }
    },
    series: [
      {
        name: '平均分',
        type: 'line',
        smooth: true,
        symbolSize: 8,
        data: list.map((t) => Number(t.avg)),
        itemStyle: { color: palette.brand },
        lineStyle: { width: 3 },
        areaStyle: { opacity: 0.1 }
      },
      {
        name: '最高分',
        type: 'line',
        smooth: true,
        data: list.map((t) => Number(t.max)),
        itemStyle: { color: palette.success },
        lineStyle: { type: 'dashed' }
      },
      {
        name: '最低分',
        type: 'line',
        smooth: true,
        data: list.map((t) => Number(t.min)),
        itemStyle: { color: palette.warning },
        lineStyle: { type: 'dashed' }
      }
    ]
  }
})

useChart(rangeChartRef, (palette) => ({
  color: palette.series,
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { orient: 'vertical', left: 'left', textStyle: { color: palette.textMuted } },
  series: [
    {
      name: '成绩分布',
      type: 'pie',
      radius: '62%',
      data: scoreRange.value,
      itemStyle: { borderColor: palette.surface, borderWidth: 2 },
      label: { color: palette.text }
    }
  ]
}))

useChart(errorChartRef, (palette) => ({
  tooltip: {
    trigger: 'axis',
    formatter: (points) => `${points[0].axisValue}<br/>错误率：${points[0].data}%`
  },
  grid: { left: 48, right: 20, bottom: 30, top: 30 },
  xAxis: {
    type: 'category',
    data: errorQuestions.value.map((_, i) => `第 ${i + 1} 题`),
    axisLabel: { color: palette.textMuted },
    axisLine: { lineStyle: { color: palette.axis } }
  },
  yAxis: {
    type: 'value',
    name: '错误率(%)',
    max: 100,
    nameTextStyle: { color: palette.textMuted },
    axisLabel: { color: palette.textMuted },
    splitLine: { lineStyle: { color: palette.split } }
  },
  series: [
    {
      name: '错误率',
      type: 'bar',
      data: errorQuestions.value.map((q) => Number(q.errorRate || 0)),
      itemStyle: { color: palette.danger }
    }
  ]
}))

/* ---------- 导出 ---------- */

const printReport = () => window.print()

const exportEvaluations = () => {
  downloadCsv({
    headers: ['学生', '综合评分', '教学态度', '内容质量', '难度适中', '文字意见', '时间'],
    rows: evaluations.value.map((r) => [
      r.studentname,
      r.overallscore,
      r.teachingscore,
      r.contentscore,
      r.difficultyscore,
      r.comment || '',
      formatDate(r.createtime, 'minute')
    ]),
    fileName: `课程评价_${formatDate(new Date(), 'date')}.csv`
  })
}
</script>

<style scoped>
.picker {
  width: 190px;
}

.hw-picker {
  width: 260px;
  margin-bottom: var(--zy-space-3);
}

.hint {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.chart {
  height: 340px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
  gap: var(--zy-stack-gap);
}

.eval-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--zy-space-3);
  margin-bottom: var(--zy-space-4);
}

@media screen and (max-width: 768px) {
  .picker,
  .hw-picker {
    width: 100%;
  }

  .chart {
    height: 260px;
  }

  .eval-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

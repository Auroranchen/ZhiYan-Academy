<template>
  <PageLayout title="统计报表" description="全校作业量、成绩分布与课程评价的汇总分析。">
    <SectionCard title="课程作业统计">
      <div ref="homeworkChartRef" class="chart chart--tall"></div>
    </SectionCard>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <SectionCard title="学生成绩排名（前 20 名）">
          <template #action>
            <el-select v-model="scoreCourseid" placeholder="全部课程" clearable size="small" class="picker">
              <el-option
                v-for="c in courses"
                :key="c.courseid"
                :label="c.coursename"
                :value="c.courseid"
              />
            </el-select>
          </template>
          <div ref="scoreChartRef" class="chart"></div>
        </SectionCard>
      </el-col>

      <el-col :xs="24" :lg="12">
        <SectionCard title="成绩区间分布">
          <template #action>
            <el-select v-model="rangeCourseid" placeholder="全部课程" clearable size="small" class="picker">
              <el-option
                v-for="c in courses"
                :key="c.courseid"
                :label="c.coursename"
                :value="c.courseid"
              />
            </el-select>
          </template>
          <div ref="rangeChartRef" class="chart"></div>
        </SectionCard>
      </el-col>
    </el-row>

    <SectionCard title="课程评价统计">
      <template #action>
        <el-select v-model="evalCourseid" placeholder="请选择课程" clearable class="picker picker--wide">
          <el-option
            v-for="c in courses"
            :key="c.courseid"
            :label="c.coursename"
            :value="c.courseid"
          />
        </el-select>
      </template>

      <el-empty v-if="!evalCourseid" description="请选择课程查看评价" :image-size="80" />

      <template v-else>
        <div class="eval-stats">
          <StatTile
            v-for="tile in evalTiles"
            :key="tile.label"
            :label="tile.label"
            :value="tile.value"
            :tone="tile.tone"
          />
        </div>

        <DataTable
          :columns="evalColumns"
          :data="evaluations.rows"
          :loading="evaluations.loading"
          :total="evaluations.total"
          v-model:page="evaluations.page"
          v-model:page-size="evaluations.pageSize"
          row-key="id"
          empty-text="该课程暂无评价"
          @change="loadEvaluationList"
        >
          <template #time="{ row }">{{ formatDate(row.createtime, 'minute') }}</template>
        </DataTable>
      </template>
    </SectionCard>
  </PageLayout>
</template>

<script setup>
/** 统计报表（管理端） */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { DataTable, PageLayout, SectionCard, StatTile } from '@/components/ui'
import { useChart } from '@/composables/useChart'
import { getCourseEvaluationStats, listAllCourses, listCourseEvaluations } from '@/api/course'
import { getHomeworkStatistics, getScoreRangeStatistics, getScoreStatistics } from '@/api/statistics'
import { formatDate, formatScore } from '@/utils/format'

const evalColumns = [
  { prop: 'studentname', label: '学生', width: 140 },
  { prop: 'overallscore', label: '综合评分', width: 100, align: 'right' },
  { prop: 'teachingscore', label: '教学态度', width: 100, align: 'right' },
  { prop: 'contentscore', label: '内容质量', width: 100, align: 'right' },
  { prop: 'difficultyscore', label: '难度适中', width: 100, align: 'right' },
  { prop: 'comment', label: '文字意见', minWidth: 220 },
  { label: '时间', width: 170, slot: 'time' }
]

const courses = ref([])

const homeworkStats = ref([])
const scoreStats = ref([])
const rangeStats = ref([])

const scoreCourseid = ref(null)
const rangeCourseid = ref(null)
const evalCourseid = ref(null)

const homeworkChartRef = ref(null)
const scoreChartRef = ref(null)
const rangeChartRef = ref(null)

/* ---------- 图表 ---------- */

useChart(homeworkChartRef, (palette) => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 48, right: 24, top: 32, bottom: 72 },
  xAxis: {
    type: 'category',
    data: homeworkStats.value.map((item) => item.coursename),
    axisLabel: { rotate: 30, color: palette.textMuted },
    axisLine: { lineStyle: { color: palette.axis } }
  },
  yAxis: {
    type: 'value',
    name: '作业数量',
    axisLabel: { color: palette.textMuted },
    splitLine: { lineStyle: { color: palette.split } }
  },
  series: [
    {
      name: '作业数量',
      type: 'bar',
      data: homeworkStats.value.map((item) => item.homeworkCount),
      itemStyle: { color: palette.series[0], borderRadius: [4, 4, 0, 0] }
    }
  ]
}))

useChart(scoreChartRef, (palette) => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 100, right: 24, top: 24, bottom: 40 },
  xAxis: {
    type: 'value',
    name: '平均分',
    max: 100,
    axisLabel: { color: palette.textMuted },
    splitLine: { lineStyle: { color: palette.split } }
  },
  yAxis: {
    type: 'category',
    // 后端按分数降序返回，ECharts 的 y 轴自下而上，反转后第一名才在顶部
    data: scoreStats.value.map((item) => item.studentname).reverse(),
    axisLabel: { color: palette.textMuted },
    axisLine: { lineStyle: { color: palette.axis } }
  },
  series: [
    {
      name: '平均分',
      type: 'bar',
      data: scoreStats.value.map((item) => item.avgScore).reverse(),
      itemStyle: { color: palette.success, borderRadius: [0, 4, 4, 0] }
    }
  ]
}))

useChart(rangeChartRef, (palette) => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { orient: 'vertical', left: 'left', textStyle: { color: palette.text } },
  color: palette.series,
  series: [
    {
      name: '成绩分布',
      type: 'pie',
      radius: ['42%', '68%'],
      data: rangeStats.value,
      label: { color: palette.text },
      emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0, 0, 0, 0.2)' } }
    }
  ]
}))

/* ---------- 数据加载 ---------- */

const loadScoreStats = async () => {
  const res = await getScoreStatistics({ courseid: scoreCourseid.value || undefined }).catch(() => null)
  scoreStats.value = res?.data || []
}

const loadRangeStats = async () => {
  const res = await getScoreRangeStatistics({ courseid: rangeCourseid.value || undefined }).catch(
    () => null
  )
  rangeStats.value = res?.data || []
}

watch(scoreCourseid, loadScoreStats)
watch(rangeCourseid, loadRangeStats)

onMounted(async () => {
  const [courseRes, homeworkRes] = await Promise.all([
    listAllCourses().catch(() => null),
    getHomeworkStatistics().catch(() => null)
  ])
  courses.value = courseRes?.data || []
  homeworkStats.value = homeworkRes?.data || []

  loadScoreStats()
  loadRangeStats()
})

/* ---------- 课程评价 ---------- */

const evalStats = reactive({
  avgTeaching: 0,
  avgContent: 0,
  avgDifficulty: 0,
  avgOverall: 0,
  totalCount: 0
})

const evaluations = reactive({ rows: [], total: 0, page: 1, pageSize: 10, loading: false })

const evalTiles = computed(() => [
  { label: '教学态度均分', value: formatScore(evalStats.avgTeaching), tone: 'brand' },
  { label: '内容质量均分', value: formatScore(evalStats.avgContent), tone: 'brand' },
  { label: '难度适中均分', value: formatScore(evalStats.avgDifficulty), tone: 'warning' },
  { label: '综合评分均分', value: formatScore(evalStats.avgOverall), tone: 'success' },
  { label: '评价人数', value: evalStats.totalCount, tone: 'default' }
])

const loadEvaluationList = async () => {
  if (!evalCourseid.value) return

  evaluations.loading = true
  try {
    const res = await listCourseEvaluations(evalCourseid.value, {
      page: evaluations.page,
      pageSize: evaluations.pageSize
    }).catch(() => null)
    evaluations.rows = res?.data || []
    evaluations.total = Number(res?.total || 0)
  } finally {
    evaluations.loading = false
  }
}

watch(evalCourseid, async (courseid) => {
  evaluations.page = 1
  evaluations.rows = []
  evaluations.total = 0
  Object.assign(evalStats, {
    avgTeaching: 0,
    avgContent: 0,
    avgDifficulty: 0,
    avgOverall: 0,
    totalCount: 0
  })

  if (!courseid) return

  const [statsRes] = await Promise.all([
    getCourseEvaluationStats(courseid).catch(() => null),
    loadEvaluationList()
  ])
  Object.assign(evalStats, statsRes?.data || {})
})
</script>

<style scoped>
.chart {
  width: 100%;
  height: 320px;
}

.chart--tall {
  height: 360px;
}

.picker {
  width: 160px;
}

.picker--wide {
  width: 220px;
}

.eval-stats {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--zy-space-3);
  margin-bottom: var(--zy-space-4);
}

@media screen and (max-width: 1200px) {
  .eval-stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media screen and (max-width: 768px) {
  .chart,
  .chart--tall {
    height: 260px;
  }

  .eval-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .picker,
  .picker--wide {
    width: 100%;
  }
}
</style>

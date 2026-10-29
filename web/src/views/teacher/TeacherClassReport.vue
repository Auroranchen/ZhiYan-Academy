<template>
  <PageLayout
    title="AI 班级学情周报"
    description="选择课程后由 AI 汇总整体学情、共性薄弱点与教学建议。"
    :breadcrumb="[{ label: '教学管理' }, { label: '班级学情周报' }]"
  >
    <template #actions>
      <el-select v-model="courseid" placeholder="选择课程" filterable class="picker">
        <el-option v-for="c in courses" :key="c.courseid" :label="c.coursename" :value="c.courseid" />
      </el-select>
      <el-button v-if="report.content" :loading="exporting" @click="exportPdf">导出 PDF</el-button>
      <el-button type="primary" :disabled="!courseid" :loading="generating" @click="generate">
        {{ report.content ? '重新生成' : '生成周报' }}
      </el-button>
    </template>

    <el-empty v-if="!courseid" description="请先选择一门课程" />

    <div v-else v-loading="generating" element-loading-text="AI 正在分析班级学情…">
      <el-empty v-if="!report.content && !generating" description="该课程还没有生成学情周报">
        <el-button type="primary" @click="generate">立即生成</el-button>
      </el-empty>

      <div v-if="report.content" ref="reportRef" class="report">
        <header class="report__head">
          <h2 class="report__title">{{ report.title }}</h2>
          <p class="report__meta">生成时间：{{ report.generatedAt }}</p>
        </header>

        <div v-if="report.stats" class="report__stats">
          <StatTile
            label="作业平均分"
            :value="report.stats.homeworkAvg"
            :hint="`提交 ${report.stats.submitCount} 次`"
            tone="brand"
          />
          <StatTile
            label="考试平均分"
            :value="report.stats.examAvg"
            :hint="`${report.stats.examCount} 人次`"
          />
          <StatTile
            label="有成绩学生"
            :value="report.stats.studentCount"
            hint="参与统计"
            tone="success"
          />
          <StatTile
            label="需关注学生"
            :value="lowPerformers.length"
            hint="均分低于 70"
            :tone="lowPerformers.length ? 'danger' : 'default'"
          />
        </div>

        <div class="report__charts">
          <SectionCard title="成绩分布">
            <div v-show="!printing" ref="distChartRef" class="chart"></div>
            <img v-show="printing && chartSnapshot" :src="chartSnapshot" class="chart" alt="成绩分布" />
          </SectionCard>

          <SectionCard
            title="需重点关注学生"
            :empty="!lowPerformers.length"
            empty-text="暂无成绩偏低的学生"
            empty-icon="ph:confetti-duotone"
          >
            <DataTable
              :columns="lowPerformerColumns"
              :data="lowPerformers"
              :show-pagination="false"
              max-height="240"
              row-key="studentname"
            />
          </SectionCard>
        </div>

        <MarkdownView :content="report.content" class="markdown" />
      </div>
    </div>
  </PageLayout>
</template>

<script setup>
/** AI 班级学情周报（教师端） */
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { DataTable, MarkdownView, PageLayout, SectionCard, StatTile } from '@/components/ui'
import { useChart, chartPalette } from '@/composables/useChart'
import { listCoursesByTeacher } from '@/api/course'
import { generateClassReport, getCachedClassReport } from '@/api/aiInsight'

const lowPerformerColumns = [
  { prop: 'studentname', label: '姓名', minWidth: 120 },
  { prop: 'avg', label: '作业均分', width: 110, align: 'right' }
]

const store = useStore()
const teacherid = store.state.user?.teacherid

const courses = ref([])
const courseid = ref(null)
const report = ref({})

const generating = ref(false)
const exporting = ref(false)
const printing = ref(false)
const chartSnapshot = ref('')

const reportRef = ref(null)
const distChartRef = ref(null)

const lowPerformers = computed(() => report.value.stats?.lowPerformers || [])

onMounted(async () => {
  if (!teacherid) return
  const res = await listCoursesByTeacher(teacherid).catch(() => null)
  courses.value = res?.data || []
})

/** 换课程时先取缓存，没有缓存就留空等用户手动生成 */
watch(courseid, async (id) => {
  report.value = {}
  if (!id) return
  const res = await getCachedClassReport(id).catch(() => null)
  if (res?.data) report.value = res.data
})

const generate = async () => {
  generating.value = true
  try {
    const res = await generateClassReport(courseid.value)
    report.value = res?.data || {}
    ElMessage.success('周报已生成')
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    generating.value = false
  }
}

const distribution = computed(() => report.value.stats?.distribution || null)

useChart(distChartRef, (palette) => {
  const d = distribution.value
  const buckets = d ? [d.lt60, d.b60, d.b70, d.b80, d.b90] : []
  const tones = [palette.danger, palette.warning, palette.series[2], palette.brand, palette.success]

  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 44, right: 16, top: 20, bottom: 30 },
    xAxis: {
      type: 'category',
      data: ['<60', '60-69', '70-79', '80-89', '90-100'],
      axisLabel: { color: palette.textMuted },
      axisLine: { lineStyle: { color: palette.axis } }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: palette.textMuted },
      splitLine: { lineStyle: { color: palette.split } }
    },
    series: [
      {
        type: 'bar',
        barWidth: '50%',
        data: buckets.map((value, i) => ({ value, itemStyle: { color: tones[i] } }))
      }
    ]
  }
})

const exportPdf = async () => {
  exporting.value = true
  try {
    // html2canvas 抓不到 canvas 里的图表，先转成静态图替换掉
    const instance = distChartRef.value && echarts.getInstanceByDom(distChartRef.value)
    if (instance) {
      chartSnapshot.value = instance.getDataURL({
        type: 'png',
        pixelRatio: 2,
        backgroundColor: chartPalette().surface
      })
      printing.value = true
      await nextTick()
    }

    const html2pdf = (await import('html2pdf.js')).default
    await html2pdf()
      .set({
        margin: 10,
        filename: `${report.value.title || '班级学情周报'}.pdf`,
        image: { type: 'jpeg', quality: 0.95 },
        html2canvas: { scale: 2, useCORS: true },
        jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' },
        pagebreak: { mode: ['avoid-all', 'css'] }
      })
      .from(reportRef.value)
      .save()
  } catch {
    ElMessage.error('导出失败')
  } finally {
    printing.value = false
    exporting.value = false
  }
}
</script>

<style scoped>
.picker {
  width: 220px;
}

.report {
  display: flex;
  flex-direction: column;
  gap: var(--zy-stack-gap);
}

.report__title {
  margin: 0;
  font-size: var(--zy-font-size-2xl);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.report__meta {
  margin: var(--zy-space-1) 0 0;
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.report__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--zy-space-3);
}

.report__charts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--zy-stack-gap);
}

.chart {
  width: 100%;
  height: 240px;
  object-fit: contain;
}

.markdown {
  padding: var(--zy-space-5);
  background: var(--zy-bg-surface);
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-surface);
}

@media screen and (max-width: 1024px) {
  .report__stats,
  .report__charts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media screen and (max-width: 768px) {
  .picker {
    width: 100%;
  }

  .report__charts {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>

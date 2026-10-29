<template>
  <PageLayout
    title="AI 学习周报"
    description="基于作业、考试、错题与积分数据，AI 生成个性化成长报告。"
  >
    <template #actions>
      <el-select v-model="courseid" placeholder="全部课程" clearable class="course-select">
        <el-option :value="null" label="全部课程（总览）" />
        <el-option
          v-for="c in courses"
          :key="c.courseid"
          :label="c.coursename"
          :value="c.courseid"
        />
      </el-select>
      <el-button v-if="report.content" :loading="exporting" @click="exportPdf">导出 PDF</el-button>
      <el-button type="primary" :loading="generating" @click="generate">
        {{ report.content ? '重新生成' : '生成周报' }}
      </el-button>
    </template>

    <el-empty v-if="!report.content && !generating" description="还没有生成过学习周报">
      <el-button type="primary" @click="generate">立即生成</el-button>
    </el-empty>

    <div v-loading="generating" element-loading-text="AI 正在分析你的学习数据…">
      <article v-if="report.content" ref="reportRef" class="report">
        <header class="report__head">
          <h2 class="report__title">{{ report.title }}</h2>
          <p class="report__meta">生成时间：{{ formatDate(report.generatedAt, 'minute') }}</p>
        </header>

        <div v-if="report.stats" class="report__stats">
          <StatTile
            v-for="tile in tiles"
            :key="tile.label"
            :label="tile.label"
            :value="tile.value"
            :hint="tile.hint"
            :tone="tile.tone"
            :icon="tile.icon"
          />
        </div>

        <MarkdownView :content="report.content" />
      </article>
    </div>
  </PageLayout>
</template>

<script setup>
/** AI 学习周报（学生端） */
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { MarkdownView, PageLayout, StatTile } from '@/components/ui'
import {
  generateStudentReport,
  getCachedStudentReport,
  listStudentInsightCourses
} from '@/api/aiInsight'
import { formatDate, formatScore } from '@/utils/format'

const courses = ref([])
const courseid = ref(null)
const report = ref({})

const generating = ref(false)
const exporting = ref(false)
const reportRef = ref(null)

const tiles = computed(() => {
  const s = report.value.stats || {}
  const total = Number(s.wrongTotal) || 0
  const mastered = Number(s.wrongMastered) || 0

  return [
    {
      label: '作业平均分',
      value: formatScore(s.homeworkAvg),
      hint: `已交 ${s.homeworkCount || 0} 次`,
      tone: 'brand',
      icon: 'ph:notebook-duotone'
    },
    {
      label: '考试平均分',
      value: formatScore(s.examAvg),
      hint: `参加 ${s.examCount || 0} 场`,
      tone: 'brand',
      icon: 'ph:exam-duotone'
    },
    {
      label: '错题已掌握',
      value: `${mastered}/${total}`,
      hint: `掌握率 ${total ? Math.round((mastered / total) * 100) : 0}%`,
      tone: 'warning',
      icon: 'ph:bookmark-simple-duotone'
    },
    {
      label: '累计积分',
      value: s.points || 0,
      hint: `连续打卡 ${s.checkinDays || 0} 天`,
      tone: 'success',
      icon: 'ph:medal-duotone'
    }
  ]
})

onMounted(async () => {
  const res = await listStudentInsightCourses().catch(() => null)
  courses.value = res?.data || []
  loadCached()
})

const loadCached = async () => {
  const res = await getCachedStudentReport().catch(() => null)
  if (res?.data) report.value = res.data
}

// 切换课程后旧报告不再对应当前范围，先清空；总览有缓存则回显
watch(courseid, (value) => {
  report.value = {}
  if (!value) loadCached()
})

const generate = async () => {
  generating.value = true
  try {
    const res = await generateStudentReport(courseid.value)
    report.value = res?.data || {}
    ElMessage.success('学习周报已生成')
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    generating.value = false
  }
}

const exportPdf = async () => {
  exporting.value = true
  try {
    const html2pdf = (await import('html2pdf.js')).default
    await html2pdf()
      .set({
        margin: 10,
        filename: `${report.value.title || '学习周报'}.pdf`,
        image: { type: 'jpeg', quality: 0.95 },
        html2canvas: { scale: 2, useCORS: true },
        jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' }
      })
      .from(reportRef.value)
      .save()
  } catch {
    ElMessage.error('导出失败，请重试')
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped>
.course-select {
  width: 200px;
}

.report__head {
  margin-bottom: var(--zy-space-5);
}

.report__title {
  margin: 0;
  font-size: var(--zy-font-size-2xl);
  font-weight: var(--zy-font-weight-bold);
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
  margin-bottom: var(--zy-space-6);
}

@media screen and (max-width: 1024px) {
  .report__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media screen and (max-width: 768px) {
  .course-select {
    width: 100%;
  }
}
</style>

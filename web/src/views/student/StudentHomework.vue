<template>
  <PageLayout title="我的作业" description="查看已提交作业的批改结果、评语与答疑。">
    <template #actions>
      <el-radio-group v-model="filter">
        <el-radio-button v-for="f in FILTERS" :key="f.value" :value="f.value">
          {{ f.label }}
        </el-radio-button>
      </el-radio-group>

      <el-select v-model="courseid" placeholder="全部课程" clearable class="course-select">
        <el-option
          v-for="c in courses"
          :key="c.courseid"
          :label="c.coursename"
          :value="c.courseid"
        />
      </el-select>

      <el-input v-model="keyword" placeholder="搜索作业标题" clearable class="search" />
    </template>

    <template v-if="stats.count" #summary>
      <StatTile label="已批改" :value="stats.count" unit="份" icon="ph:check-circle-duotone" />
      <StatTile
        label="平均分"
        :value="stats.average"
        :tone="stats.average >= PASS_SCORE ? 'success' : 'danger'"
        icon="ph:chart-line-duotone"
      />
      <StatTile label="最高分" :value="stats.max" tone="success" icon="ph:trend-up-duotone" />
      <StatTile label="最低分" :value="stats.min" tone="danger" icon="ph:trend-down-duotone" />
    </template>

    <div v-loading="loading" class="grid">
      <HomeworkCard
        v-for="item in pageRows"
        :key="item.id"
        :homework="item"
        :mode="item.status >= 1 ? 'completed' : 'pending'"
        @view="openDetail"
        @submit="goSubmit"
        @export="exportPdf"
        @analyze="goExplain"
      />
      <el-empty v-if="!loading && !filtered.length" :description="emptyText" />
    </div>

    <Pagination v-model:page="page" v-model:size="pageSize" :total="filtered.length" />

    <HomeworkDetailDialog
      v-model="detailVisible"
      :submission-id="detailId"
      @graded="load"
    />
  </PageLayout>
</template>

<script setup>
/** 我的作业（学生端） */
import { computed, onMounted, ref, watch } from 'vue'
import { useStore } from 'vuex'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/Pagination.vue'
import HomeworkCard from '@/components/HomeworkCard.vue'
import { PageLayout, StatTile } from '@/components/ui'
import HomeworkDetailDialog from './components/HomeworkDetailDialog.vue'
import { getStudentHomework, listStudentHomeworkByStudent } from '@/api/homework'
import { listCoursesByClass } from '@/api/course'
import { getCurrentSemester } from '@/utils/semester'
import { mergeStudentAnswers, renderExamResultHtml } from '@/utils/exam'
import { buildHomeworkPdfHtml } from '@/utils/pdf'
import { sanitizeHtml } from '@/utils/safeHtml'
import { formatDate, formatScore } from '@/utils/format'

const PASS_SCORE = 60

/** 学生的作业总量有限，一次取完便于在前端筛选与统计 */
const BULK_PAGE = { page: 1, pageSize: 1000 }

const FILTERS = [
  { value: 'all', label: '全部' },
  { value: 'pending', label: '未完成' },
  { value: 'completed', label: '已完成' }
]

const EMPTY_TEXT = {
  all: '暂无作业',
  pending: '没有未完成的作业',
  completed: '还没有已完成的作业'
}

const PDF_LABELS = {
  answerLabel: '我的答案',
  correctAnswerLabel: '正确答案',
  analysisLabel: '解析',
  trueLabel: '正确',
  falseLabel: '错误',
  emptyLabel: '未作答'
}

const store = useStore()
const route = useRoute()
const router = useRouter()

const submissions = ref([])
const courses = ref([])
const loading = ref(false)

const filter = ref('all')
const courseid = ref(null)
const keyword = ref('')

const page = ref(1)
const pageSize = ref(10)

/* ---------- 筛选与统计 ---------- */

const filtered = computed(() => {
  const text = keyword.value.trim().toLowerCase()

  return submissions.value.filter((row) => {
    if (filter.value === 'completed' && row.status < 1) return false
    if (filter.value === 'pending' && row.status !== 0) return false
    if (courseid.value && row.courseid !== courseid.value) return false
    if (text && !String(row.homeworktitle || '').toLowerCase().includes(text)) return false
    return true
  })
})

const pageRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filtered.value.slice(start, start + pageSize.value)
})

const emptyText = computed(() =>
  keyword.value.trim() ? '没有匹配的作业' : EMPTY_TEXT[filter.value]
)

/** 统计只看已打分的，未批改的不该拉低平均分 */
const stats = computed(() => {
  const scores = filtered.value
    .map((row) => Number(row.score))
    .filter((score) => Number.isFinite(score))

  if (!scores.length) return { count: 0, average: 0, max: 0, min: 0 }

  const sum = scores.reduce((total, score) => total + score, 0)
  return {
    count: scores.length,
    average: formatScore(sum / scores.length),
    max: formatScore(Math.max(...scores)),
    min: formatScore(Math.min(...scores))
  }
})

// 筛选条件变了，当前页码多半越界
watch([filter, courseid, keyword], () => {
  page.value = 1
})

/* ---------- 数据加载 ---------- */

const load = async () => {
  const studentid = store.state.user?.studentid
  if (!studentid) return

  loading.value = true
  try {
    const res = await listStudentHomeworkByStudent(studentid, BULK_PAGE).catch(() => null)
    submissions.value = (res?.data || []).sort(
      (a, b) =>
        new Date(b.submittime || b.createtime || 0) - new Date(a.submittime || a.createtime || 0)
    )
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  const classid = store.state.user?.classid
  const [courseRes] = await Promise.all([
    classid
      ? listCoursesByClass(classid, { semester: getCurrentSemester() }).catch(() => null)
      : null,
    load()
  ])
  courses.value = courseRes?.data || []

  // 从通知或首页跳过来时直接展开对应作业
  const homeworkid = Number(route.query.homeworkid)
  if (!Number.isFinite(homeworkid)) return

  const target = submissions.value.find((row) => Number(row.homeworkid) === homeworkid)
  if (target) openDetail(target)
})

/* ---------- 交互 ---------- */

const detailVisible = ref(false)
const detailId = ref(null)

const openDetail = (row) => {
  detailId.value = row.id
  detailVisible.value = true
}

const goSubmit = (row) =>
  router.push({ path: '/student/homework/submit', query: { id: row.homeworkid || row.id } })

const goExplain = (row) =>
  router.push({ path: '/student/homework/explain', query: { id: row.id } })

const exportPdf = async (row) => {
  try {
    const res = await getStudentHomework(row.id)
    const data = res?.data
    if (!data) return

    const isExam = data.homeworktype === 1
    let answerHtml = data.content || '-'

    if (isExam) {
      const merged = mergeStudentAnswers(data.content, data.homeworkcontent)
      if (merged) {
        answerHtml = renderExamResultHtml(merged.questions, merged.answers, null, {
          ...PDF_LABELS,
          showAnalysisButton: false
        })
      }
    }

    const html = buildHomeworkPdfHtml({
      title: '我的作业成绩单',
      metaItems: [
        { label: '导出时间', value: formatDate(new Date(), 'minute') },
        { label: '状态', value: data.score != null ? '已评分' : '待批改' }
      ],
      infoTitle: '基本信息',
      infoRows: [
        { label: '作业标题', value: data.homeworktitle || '-' },
        { label: '课程', value: data.coursename || '-' },
        { label: '作业类型', value: isExam ? '在线答题' : '文档上传' },
        { label: '提交时间', value: formatDate(data.submittime, 'minute') }
      ],
      answerTitle: isExam ? '答题详情' : '文档内容',
      answerHtml,
      isCodeContent: !isExam,
      scoreTitle: '评分信息',
      scoreRows: [
        { label: '成绩', value: data.score ?? '-' },
        { label: '评语', value: data.comment || '暂无评语' }
      ],
      footerText: `批改老师：${data.teachername || '-'}`
    })

    // html2pdf 需要元素真实挂载在文档里才能测量尺寸
    const container = document.createElement('div')
    container.innerHTML = sanitizeHtml(html)
    document.body.appendChild(container)

    try {
      const html2pdf = (await import('html2pdf.js')).default
      await html2pdf()
        .set({
          margin: 10,
          filename: `我的作业_${data.homeworktitle}.pdf`,
          image: { type: 'jpeg', quality: 0.98 },
          html2canvas: { scale: 2, useCORS: true },
          jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' }
        })
        .from(container)
        .save()
      ElMessage.success('PDF 导出成功')
    } finally {
      document.body.removeChild(container)
    }
  } catch {
    ElMessage.error('导出 PDF 失败，请重试')
  }
}
</script>

<style scoped>
.course-select {
  width: 200px;
}

.search {
  width: 180px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--zy-space-3);
  min-height: 200px;
}

@media screen and (max-width: 768px) {
  .course-select,
  .search {
    width: 100%;
  }

  .grid {
    grid-template-columns: 1fr;
  }
}
</style>

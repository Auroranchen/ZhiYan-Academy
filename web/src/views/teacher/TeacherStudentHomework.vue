<template>
  <PageLayout title="学生作业批改" description="按课程、班级与作业筛选提交记录，批改并回复答疑。">
    <template #actions>
      <el-button :disabled="!rows.length" @click="exportGrades">导出成绩</el-button>
    </template>

    <template v-if="rows.length" #summary>
      <StatTile label="本页记录" :value="rows.length" icon="ph:list-checks-duotone" />
      <StatTile label="待批改" :value="counts.pending" tone="warning" icon="ph:clock-duotone" />
      <StatTile label="已批改" :value="counts.graded" tone="success" icon="ph:check-circle-duotone" />
      <StatTile label="未提交" :value="counts.unsubmitted" icon="ph:minus-circle-duotone" />
    </template>

    <FilterBar :loading="loading" @search="search" @reset="reset">
      <el-select v-model="query.semester" placeholder="全部学期" clearable>
        <el-option v-for="s in semesterOptions" :key="s" :label="s" :value="s" />
      </el-select>

      <el-select v-model="query.courseid" placeholder="全部课程" clearable>
        <el-option
          v-for="c in courseOptions"
          :key="c.courseid"
          :label="c.coursename"
          :value="c.courseid"
        />
      </el-select>

      <el-select v-model="query.classid" placeholder="全部班级" clearable>
        <el-option
          v-for="c in classOptions"
          :key="c.classid"
          :label="c.classname"
          :value="c.classid"
        />
      </el-select>

      <el-select v-model="query.homeworkid" placeholder="全部作业" clearable class="homework-select">
        <el-option
          v-for="h in homeworkOptions"
          :key="h.homeworkid"
          :label="`${h.title} · ${h.coursename}`"
          :value="h.homeworkid"
        />
      </el-select>

      <el-input v-model="query.keyword" placeholder="搜索学号 / 姓名" clearable />
    </FilterBar>

    <DataTable
      :columns="columns"
      :data="rows"
      :loading="loading"
      :total="total"
      v-model:page="page"
      v-model:page-size="pageSize"
      row-key="id"
      empty-text="没有符合条件的提交记录"
      empty-hint="可以放宽筛选条件，或确认该作业已发布给对应班级"
      actions-width="230"
      @change="load"
    >
      <template #status="{ row }">
        <el-tag :type="STATUS_TONE[row.status] || 'info'" size="small" disable-transitions>
          {{ STATUS_TEXT[row.status] || '—' }}
        </el-tag>
      </template>

      <template #submittime="{ row }">{{ formatDate(row.submittime, 'minute') }}</template>

      <template #actions="{ row }">
        <template v-if="row.status >= 1 && row.status !== 3">
          <el-button link type="primary" @click="openGrade(row)">
            {{ row.status === 2 ? '查看' : '批改' }}
          </el-button>
          <el-button link @click="returnToStudent(row)">打回</el-button>
        </template>
        <template v-if="row.status >= 1">
          <el-button link @click="exportPdf(row)">导出 PDF</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </template>
    </DataTable>

    <GradeDialog v-model="gradeVisible" :submission-id="gradeId" @graded="load" />
  </PageLayout>
</template>

<script setup>
/** 学生作业批改（教师端） */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useStore } from 'vuex'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DataTable, FilterBar, PageLayout, StatTile } from '@/components/ui'
import GradeDialog from './components/GradeDialog.vue'
import {
  deleteStudentHomework,
  getStudentHomework,
  listHomeworkByTeacher,
  listStudentHomeworkByTeacher,
  returnStudentHomework
} from '@/api/homework'
import { listCoursesByTeacher } from '@/api/course'
import { getCurrentSemester } from '@/utils/semester'
import { mergeStudentAnswers, renderExamResultHtml } from '@/utils/exam'
import { buildHomeworkPdfHtml } from '@/utils/pdf'
import { sanitizeHtml } from '@/utils/safeHtml'
import { downloadCsv } from '@/utils/excel'
import { confirmDelete, confirmOrAbort } from '@/utils/confirm'
import { formatDate } from '@/utils/format'

const BULK_PAGE = { page: 1, pageSize: 1000 }

const STATUS_TEXT = { 0: '未提交', 1: '已提交', 2: '已批改', 3: '已打回' }
const STATUS_TONE = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }

const PDF_LABELS = {
  answerLabel: '学生答案',
  correctAnswerLabel: '正确答案',
  trueLabel: '正确',
  falseLabel: '错误',
  emptyLabel: '未作答'
}

const columns = [
  { prop: 'homeworktitle', label: '作业标题', minWidth: 160 },
  { prop: 'coursename', label: '课程', minWidth: 120 },
  { prop: 'classname', label: '班级', width: 110 },
  { prop: 'studentno', label: '学号', width: 110 },
  { prop: 'studentname', label: '姓名', width: 90 },
  { label: '状态', width: 90, slot: 'status' },
  { prop: 'score', label: '成绩', width: 80, align: 'right' },
  { label: '提交时间', width: 160, slot: 'submittime' }
]

const store = useStore()
const route = useRoute()
const teacherid = store.state.user?.teacherid

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)

const query = reactive({
  semester: getCurrentSemester(),
  courseid: null,
  classid: null,
  homeworkid: null,
  keyword: ''
})

const courses = ref([])
const homeworks = ref([])
const classes = ref([])

/* ---------- 下拉选项 ---------- */

const semesterOptions = computed(() =>
  [...new Set(homeworks.value.map((h) => h.semester).filter(Boolean))].sort()
)

const courseOptions = computed(() =>
  query.semester ? courses.value.filter((c) => c.semester === query.semester) : courses.value
)

const homeworkOptions = computed(() =>
  homeworks.value.filter(
    (h) =>
      (!query.courseid || h.courseid === query.courseid) &&
      (!query.semester || h.semester === query.semester)
  )
)

/**
 * 班级取自教师的全部作业记录而不是当前页，
 * 否则翻页时下拉里的可选班级会跟着变。
 */
const classOptions = computed(() => {
  const map = new Map()
  classes.value.forEach((row) => {
    if (row.classid && row.classname && !map.has(row.classid)) {
      map.set(row.classid, { classid: row.classid, classname: row.classname })
    }
  })
  return [...map.values()]
})

// 换学期后原课程多半不在新学期里，一并清掉避免查出空列表
watch(
  () => query.semester,
  () => {
    query.courseid = null
    query.homeworkid = null
  }
)

watch(
  () => query.courseid,
  () => {
    query.homeworkid = null
  }
)

/* ---------- 数据加载 ---------- */

const load = async () => {
  if (!teacherid) return

  loading.value = true
  try {
    const res = await listStudentHomeworkByTeacher(teacherid, {
      ...query,
      page: page.value,
      pageSize: pageSize.value
    }).catch(() => null)
    rows.value = res?.data || []
    total.value = Number(res?.total || 0)
  } finally {
    loading.value = false
  }
}

const search = () => {
  page.value = 1
  load()
}

const reset = () => {
  Object.assign(query, {
    semester: getCurrentSemester(),
    courseid: null,
    classid: null,
    homeworkid: null,
    keyword: ''
  })
  search()
}

onMounted(async () => {
  // 从「我的课程」跳过来时会带上定位参数
  const { courseid, homeworkid, classid } = route.query
  if (Number(courseid) > 0) query.courseid = Number(courseid)
  if (Number(homeworkid) > 0) query.homeworkid = Number(homeworkid)
  if (Number(classid) > 0) query.classid = Number(classid)

  const [courseRes, homeworkRes, rosterRes] = await Promise.all([
    teacherid ? listCoursesByTeacher(teacherid).catch(() => null) : null,
    teacherid ? listHomeworkByTeacher(teacherid, BULK_PAGE).catch(() => null) : null,
    teacherid ? listStudentHomeworkByTeacher(teacherid, BULK_PAGE).catch(() => null) : null
  ])

  courses.value = courseRes?.data || []
  homeworks.value = homeworkRes?.data || []
  classes.value = rosterRes?.data || []

  load()
})

const counts = computed(() => ({
  pending: rows.value.filter((r) => Number(r.status) === 1).length,
  graded: rows.value.filter((r) => Number(r.status) === 2).length,
  unsubmitted: rows.value.filter((r) => Number(r.status) === 0).length
}))

/* ---------- 操作 ---------- */

const gradeVisible = ref(false)
const gradeId = ref(null)

const openGrade = (row) => {
  gradeId.value = row.id
  gradeVisible.value = true
}

const returnToStudent = async (row) => {
  const ok = await confirmOrAbort(
    `确定打回 ${row.studentname} 的作业吗？打回后需要学生重新提交。`,
    '打回作业'
  )
  if (!ok) return

  await returnStudentHomework(row.id)
  ElMessage.success('已打回')
  load()
}

const remove = async (row) => {
  const ok = await confirmDelete(`确定删除 ${row.studentname} 的作业提交记录吗？删除后无法恢复。`)
  if (!ok) return

  await deleteStudentHomework(row.id)
  ElMessage.success('删除成功')
  load()
}

const exportGrades = () =>
  downloadCsv({
    fileName: `成绩记录_${formatDate(new Date())}.csv`,
    headers: ['学号', '姓名', '班级', '课程', '作业', '成绩', '状态', '提交时间'],
    rows: rows.value.map((r) => [
      r.studentno,
      r.studentname,
      r.classname,
      r.coursename,
      r.homeworktitle,
      r.score ?? '',
      STATUS_TEXT[r.status] || '—',
      formatDate(r.submittime, 'minute')
    ])
  })

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
        answerHtml = renderExamResultHtml(merged.questions, merged.answers, null, PDF_LABELS)
      }
    }

    const html = buildHomeworkPdfHtml({
      title: '学生作业成绩单',
      metaItems: [
        { label: '导出时间', value: formatDate(new Date(), 'minute') },
        { label: '状态', value: STATUS_TEXT[data.status] || '—' }
      ],
      infoTitle: '基本信息',
      infoRows: [
        { label: '学生姓名', value: data.studentname || '-' },
        { label: '学号', value: data.studentno || '-' },
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
        { label: '评语', value: data.comment || '-' }
      ],
      footerText: `批改老师：${data.teachername || store.state.user?.teachername || '-'}`
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
          filename: `${data.studentname}_${data.homeworktitle}.pdf`,
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
.homework-select {
  min-width: 260px;
}

.homework-select :deep(.el-select) {
  width: 100%;
}
</style>

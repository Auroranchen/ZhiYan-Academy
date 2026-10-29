<template>
  <PageLayout
    title="学生作业"
    description="查看全校学生的作业提交与批改结果，可导出成绩单。"
    :breadcrumb="[{ label: '作业管理' }, { label: '学生作业' }]"
  >
    <FilterBar :loading="loading" @search="search" @reset="reset">
      <el-input v-model="query.keyword" placeholder="学生姓名或作业标题" clearable />
    </FilterBar>

    <DataTable
      v-model:page="query.page"
      v-model:page-size="query.pageSize"
      :columns="columns"
      :data="rows"
      :loading="loading"
      :total="total"
      row-key="id"
      empty-text="没有匹配的提交记录"
      actions-width="190"
      @change="load"
    >
      <template #type="{ row }">
        <el-tag :type="row.homeworktype === 1 ? 'success' : 'warning'" size="small" disable-transitions>
          {{ row.homeworktype === 1 ? '在线答题' : '文档上传' }}
        </el-tag>
      </template>

      <template #status="{ row }">
        <el-tag :type="STATUS_TONE[row.status] || 'info'" size="small" disable-transitions>
          {{ STATUS_TEXT[row.status] || '—' }}
        </el-tag>
      </template>

      <template #score="{ row }">{{ formatScore(row.score) }}</template>

      <template #time="{ row }">{{ formatDate(row.submittime, 'minute') }}</template>

      <template #actions="{ row }">
        <el-button link type="primary" @click="openDetail(row)">查看</el-button>
        <el-button link :loading="exportingId === row.id" @click="exportPdf(row)">导出 PDF</el-button>
        <el-button link type="danger" @click="remove(row)">删除</el-button>
      </template>
    </DataTable>

    <el-dialog
      v-model="detailVisible"
      title="学生作业详情"
      width="760px"
      @opened="bindAiAnalysisButtons()"
    >
      <el-descriptions :column="2" border>
        <el-descriptions-item label="学生姓名">{{ detail.studentname || '—' }}</el-descriptions-item>
        <el-descriptions-item label="学号">{{ detail.studentno || '—' }}</el-descriptions-item>
        <el-descriptions-item label="作业标题" :span="2">{{ detail.homeworktitle || '—' }}</el-descriptions-item>
        <el-descriptions-item label="课程">{{ detail.coursename || '—' }}</el-descriptions-item>
        <el-descriptions-item label="作业类型">
          {{ detail.homeworktype === 1 ? '在线答题' : '文档上传' }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">{{ STATUS_TEXT[detail.status] || '—' }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ formatDate(detail.submittime, 'minute') }}</el-descriptions-item>

        <el-descriptions-item v-if="detail.homeworktype === 1" label="答题详情" :span="2">
          <div v-safe-html="renderedContent"></div>
        </el-descriptions-item>

        <el-descriptions-item v-if="detail.homeworktype === 2" label="作业文件" :span="2">
          <el-button type="primary" size="small" @click="downloadFile">下载文件</el-button>
        </el-descriptions-item>

        <el-descriptions-item
          v-if="detail.homeworktype === 2 && detail.content"
          label="文档内容"
          :span="2"
        >
          <div class="doc-content">{{ detail.content }}</div>
        </el-descriptions-item>

        <el-descriptions-item label="成绩" :span="2">{{ formatScore(detail.score) }}</el-descriptions-item>
        <el-descriptions-item label="评语" :span="2">
          <el-tag v-if="isAiGraded" size="small" type="primary" class="ai-tag" disable-transitions>
            AI 评分
          </el-tag>
          {{ detail.comment || '—' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </PageLayout>
</template>

<script setup>
/** 学生作业（管理端查看、导出与删除） */
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import html2pdf from 'html2pdf.js'
import { DataTable, FilterBar, PageLayout } from '@/components/ui'
import { useCrudTable } from '@/composables/useCrudTable'
import {
  deleteStudentHomework,
  getStudentHomework,
  listStudentHomework
} from '@/api/homework'
import { mergeStudentAnswers, renderExamResultHtml } from '@/utils/exam'
import { bindAiAnalysisButtons } from '@/utils/aiAnalysis'
import { buildHomeworkPdfHtml } from '@/utils/pdf'
import { sanitizeHtml } from '@/utils/safeHtml'
import { formatDate, formatScore } from '@/utils/format'

const STATUS_TEXT = ['未提交', '已提交', '已批改']
const STATUS_TONE = ['info', 'warning', 'success']

const RESULT_LABELS = {
  answerLabel: '学生答案',
  correctAnswerLabel: '正确答案',
  trueLabel: '正确',
  falseLabel: '错误',
  emptyLabel: '未作答'
}

const columns = [
  { prop: 'studentname', label: '学生', width: 100 },
  { prop: 'studentno', label: '学号', width: 130 },
  { prop: 'classname', label: '班级', width: 130 },
  { prop: 'homeworktitle', label: '作业标题', minWidth: 200 },
  { prop: 'coursename', label: '课程', width: 130 },
  { label: '类型', width: 110, slot: 'type' },
  { label: '状态', width: 90, slot: 'status' },
  { label: '成绩', width: 80, align: 'right', slot: 'score' },
  { label: '提交时间', width: 170, slot: 'time' }
]

const { rows, total, loading, query, load, search, reset, remove } = useCrudTable({
  fetch: listStudentHomework,
  remove: (row) => deleteStudentHomework(row.id),
  initialQuery: { keyword: '' },
  confirmText: (row) => `确定要删除「${row.studentname}」提交的「${row.homeworktitle}」吗？`
})

/* ---------- 详情 ---------- */

const detailVisible = ref(false)
const detail = ref({})

const isAiGraded = computed(() => Boolean(detail.value.comment?.startsWith('[AI自动评分]')))

const renderedContent = computed(() => {
  const current = detail.value
  if (current.homeworktype !== 1) return current.content || ''

  const merged = mergeStudentAnswers(current.content, current.homeworkcontent)
  if (!merged) return current.content || ''

  return renderExamResultHtml(merged.questions, merged.answers, null, {
    ...RESULT_LABELS,
    showAnalysisButton: true
  })
})

const openDetail = async (row) => {
  const res = await getStudentHomework(row.id).catch(() => null)
  if (!res?.data) return
  detail.value = res.data
  detailVisible.value = true
}

const downloadFile = () => {
  window.open(`/ZhiYanAcademy/studenthomework/download/${detail.value.id}`, '_blank')
}

/* ---------- 导出 PDF ---------- */

const exportingId = ref(null)

const exportPdf = async (row) => {
  exportingId.value = row.id
  let host = null

  try {
    const res = await getStudentHomework(row.id)
    const data = res.data

    const isDocument = data.homeworktype !== 1
    let answerHtml = data.content || '—'

    if (!isDocument) {
      const merged = mergeStudentAnswers(data.content, data.homeworkcontent)
      if (merged) {
        answerHtml = renderExamResultHtml(merged.questions, merged.answers, null, RESULT_LABELS)
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
        { label: '学生姓名', value: data.studentname || '—' },
        { label: '学号', value: data.studentno || '—' },
        { label: '作业标题', value: data.homeworktitle || '—' },
        { label: '课程', value: data.coursename || '—' },
        { label: '作业类型', value: isDocument ? '文档上传' : '在线答题' },
        { label: '提交时间', value: formatDate(data.submittime, 'minute') }
      ],
      answerTitle: isDocument ? '文档内容' : '答题详情',
      answerHtml,
      isCodeContent: isDocument,
      scoreTitle: '评分信息',
      scoreRows: [
        { label: '成绩', value: formatScore(data.score) },
        { label: '评语', value: data.comment || '—' }
      ],
      footerText: `批改老师：${data.teachername || '—'}`
    })

    // html2pdf 需要真实布局，元素必须先入文档，移到视口外避免闪屏
    host = document.createElement('div')
    host.style.cssText = 'position:fixed;left:-9999px;top:0;'
    host.innerHTML = sanitizeHtml(html)
    document.body.appendChild(host)

    await html2pdf()
      .set({
        margin: 10,
        filename: `${data.studentname}_${data.homeworktitle}.pdf`,
        image: { type: 'jpeg', quality: 0.98 },
        html2canvas: { scale: 2, useCORS: true },
        jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' }
      })
      .from(host)
      .save()

    ElMessage.success('PDF 已导出')
  } catch {
    ElMessage.error('导出 PDF 失败')
  } finally {
    if (host?.parentNode) host.parentNode.removeChild(host)
    exportingId.value = null
  }
}
</script>

<style scoped>
.doc-content {
  max-height: 220px;
  overflow-y: auto;
  white-space: pre-wrap;
  line-height: var(--zy-line-height-relaxed);
}

.ai-tag {
  margin-right: var(--zy-space-2);
}
</style>

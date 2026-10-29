<template>
  <PageLayout
    title="作业管理"
    description="查看全校教师发布的作业及提交情况。"
    :breadcrumb="[{ label: '作业管理' }, { label: '作业列表' }]"
  >
    <FilterBar :loading="loading" @search="search" @reset="reset">
      <el-input v-model="query.keyword" placeholder="作业标题" clearable />
    </FilterBar>

    <DataTable
      v-model:page="query.page"
      v-model:page-size="query.pageSize"
      :columns="columns"
      :data="rows"
      :loading="loading"
      :total="total"
      row-key="homeworkid"
      empty-text="没有匹配的作业"
      empty-hint="作业由任课教师在教师端发布"
      actions-width="150"
      @change="load"
    >
      <template #type="{ row }">
        <el-tag :type="row.type === 1 ? 'success' : 'warning'" size="small" disable-transitions>
          {{ row.type === 1 ? '在线答题' : '文档上传' }}
        </el-tag>
      </template>

      <template #deadline="{ row }">
        {{ formatDate(row.deadline, 'minute') }}
      </template>

      <template #actions="{ row }">
        <el-button link type="primary" @click="openDetail(row)">查看</el-button>
        <el-button link type="danger" @click="remove(row)">删除</el-button>
      </template>
    </DataTable>

    <el-dialog v-model="detailVisible" title="作业详情" width="720px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="作业标题" :span="2">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="所属课程">{{ detail.coursename || '—' }}</el-descriptions-item>
        <el-descriptions-item label="发布教师">{{ detail.teachername || '—' }}</el-descriptions-item>
        <el-descriptions-item label="作业类型">
          {{ detail.type === 1 ? '在线答题' : '文档上传' }}
        </el-descriptions-item>
        <el-descriptions-item label="截止时间">
          {{ formatDate(detail.deadline, 'minute') }}
        </el-descriptions-item>
        <el-descriptions-item label="作业内容" :span="2">
          <div v-safe-html="renderedContent"></div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </PageLayout>
</template>

<script setup>
/** 作业管理（管理端只读查看与删除） */
import { computed, ref } from 'vue'
import { DataTable, FilterBar, PageLayout } from '@/components/ui'
import { useCrudTable } from '@/composables/useCrudTable'
import { deleteHomework, getHomework, listHomework } from '@/api/homework'
import { formatDate } from '@/utils/format'
import { parseHomeworkContent, renderExamHtml } from '@/utils/exam'

const columns = [
  { prop: 'title', label: '作业标题', minWidth: 220 },
  { prop: 'coursename', label: '所属课程', width: 160 },
  { prop: 'teachername', label: '发布教师', width: 110 },
  { label: '类型', width: 110, slot: 'type' },
  { label: '截止时间', width: 170, slot: 'deadline' },
  { prop: 'submitCount', label: '已提交', width: 90, align: 'right' }
]

const { rows, total, loading, query, load, search, reset, remove } = useCrudTable({
  fetch: listHomework,
  remove: (row) => deleteHomework(row.homeworkid),
  initialQuery: { keyword: '' },
  confirmText: (row) => `确定要删除作业「${row.title}」吗？学生已提交的内容会一并删除。`
})

const detailVisible = ref(false)
const detail = ref({})

/** 在线答题类作业的内容是结构化题目，需要渲染成题卡而非原始 JSON */
const renderedContent = computed(() => {
  const current = detail.value
  if (current.type !== 1) return current.content || ''
  const parsed = parseHomeworkContent(current.content)
  return parsed ? renderExamHtml(parsed.questions, null, { scoreSuffix: '分' }) : current.content || ''
})

const openDetail = async (row) => {
  const res = await getHomework(row.homeworkid).catch(() => null)
  if (!res?.data) return
  detail.value = res.data
  detailVisible.value = true
}
</script>

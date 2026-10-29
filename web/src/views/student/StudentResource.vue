<template>
  <PageLayout
    title="课程资源"
    description="下载任课教师上传的课件、代码与实验材料。"
  >
    <template #actions>
      <el-button @click="openAiLibrary">AI 个性化资源包</el-button>
    </template>

    <FilterBar :loading="loading" @search="search" @reset="reset">
      <el-select v-model="semester" placeholder="全部学期" clearable>
        <el-option v-for="s in SEMESTERS" :key="s" :label="s" :value="s" />
      </el-select>
      <el-select v-model="courseid" placeholder="全部课程" clearable>
        <el-option v-for="c in courses" :key="c.courseid" :label="c.coursename" :value="c.courseid" />
      </el-select>
      <el-select v-model="fileType" placeholder="全部类型" clearable>
        <el-option v-for="t in FILE_TYPES" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>
      <el-input v-model="keyword" placeholder="标题或说明" clearable />
    </FilterBar>

    <DataTable
      v-model:page="page"
      v-model:page-size="pageSize"
      :columns="columns"
      :data="visibleRows"
      :loading="loading"
      :total="total"
      row-key="id"
      empty-text="没有匹配的资源"
      empty-hint="换个课程或类型试试，也可以看看 AI 个性化资源包"
      actions-width="100"
      @change="load"
    >
      <template #type="{ row }">
        <el-tag size="small" disable-transitions>{{ row.filetype || '其他' }}</el-tag>
      </template>
      <template #size="{ row }">{{ formatFileSize(row.filesize) }}</template>
      <template #time="{ row }">{{ formatDate(row.createtime, 'minute') }}</template>
      <template #actions="{ row }">
        <el-button link type="primary" @click="download(row)">下载</el-button>
      </template>
    </DataTable>
  </PageLayout>
</template>

<script setup>
/** 课程资源（学生端） */
import { computed, onMounted, ref, watch } from 'vue'
import { useStore } from 'vuex'
import { useRouter } from 'vue-router'
import { DataTable, FilterBar, PageLayout } from '@/components/ui'
import { listCoursesByClass } from '@/api/course'
import { listResourcesByCourse, resourceDownloadUrl } from '@/api/courseResource'
import { formatDate, formatFileSize } from '@/utils/format'
import { getCurrentSemester } from '@/utils/semester'

const PAGE_SIZE = 10

const SEMESTERS = [
  '2026-2027-2', '2026-2027-1',
  '2025-2026-2', '2025-2026-1',
  '2024-2025-2', '2024-2025-1',
  '2023-2024-2', '2023-2024-1'
]

const FILE_TYPES = [
  { value: 'PPT', label: 'PPT' },
  { value: 'PDF', label: 'PDF' },
  { value: 'CODE', label: '代码' },
  { value: 'DOC', label: '文档' },
  { value: 'OTHER', label: '其他' }
]

const columns = [
  { prop: 'title', label: '标题', minWidth: 180 },
  { prop: 'description', label: '说明', minWidth: 160 },
  { prop: 'coursename', label: '课程', width: 140 },
  { prop: 'teachername', label: '教师', width: 100 },
  { label: '类型', width: 100, align: 'center', slot: 'type' },
  { label: '大小', width: 100, align: 'right', slot: 'size' },
  { prop: 'downloadcount', label: '下载次数', width: 100, align: 'right' },
  { label: '上传时间', width: 170, slot: 'time' }
]

const store = useStore()
const router = useRouter()
const classid = store.state.user?.classid

const loading = ref(false)
const courses = ref([])
const rows = ref([])
const total = ref(0)

const page = ref(1)
const pageSize = ref(PAGE_SIZE)
const semester = ref(getCurrentSemester())
const courseid = ref(null)
const fileType = ref(null)
const keyword = ref('')

// 类型与关键词后端不支持，只能在当前页内过滤
const visibleRows = computed(() => {
  let list = rows.value
  if (fileType.value) list = list.filter((r) => r.filetype === fileType.value)

  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return list
  return list.filter(
    (r) =>
      (r.title || '').toLowerCase().includes(kw) ||
      (r.description || '').toLowerCase().includes(kw)
  )
})

const loadCourses = async () => {
  if (!classid) return
  const params = semester.value ? { semester: semester.value } : {}
  const res = await listCoursesByClass(classid, params).catch(() => null)
  courses.value = res?.data || []
}

/**
 * 未选课程时要看到本班全部课程的资源，但后端只提供按课程查询，
 * 因此并发取回后在前端合并、按时间倒序再分页。
 */
const loadAllCourseResources = async () => {
  if (!courses.value.length) return { rows: [], total: 0 }

  const perCourse = await Promise.all(
    courses.value.map((c) =>
      listResourcesByCourse(c.courseid, { page: 1, pageSize: 1000 })
        .then((res) => res?.data || [])
        .catch(() => [])
    )
  )

  const merged = perCourse
    .flat()
    .sort((a, b) => new Date(b.createtime) - new Date(a.createtime))

  const start = (page.value - 1) * pageSize.value
  return { rows: merged.slice(start, start + pageSize.value), total: merged.length }
}

const load = async () => {
  loading.value = true
  try {
    if (courseid.value) {
      const res = await listResourcesByCourse(courseid.value, {
        page: page.value,
        pageSize: pageSize.value
      }).catch(() => null)
      rows.value = res?.data || []
      total.value = res?.total || 0
    } else {
      const merged = await loadAllCourseResources()
      rows.value = merged.rows
      total.value = merged.total
    }
  } finally {
    loading.value = false
  }
}

const search = () => {
  page.value = 1
  return load()
}

const reset = () => {
  semester.value = getCurrentSemester()
  courseid.value = null
  fileType.value = null
  keyword.value = ''
  page.value = 1
  return loadCourses().then(load)
}

// 换学期后原课程可能不属于新学期，清掉再按新课程列表重查
watch(semester, async () => {
  courseid.value = null
  page.value = 1
  await loadCourses()
  await load()
})

watch(courseid, () => {
  page.value = 1
  load()
})

onMounted(async () => {
  await loadCourses()
  await load()
})

const download = (row) => window.open(resourceDownloadUrl(row.id), '_blank')
const openAiLibrary = () => router.push({ path: '/student/agent', query: { library: '1' } })
</script>

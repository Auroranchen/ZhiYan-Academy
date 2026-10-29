<template>
  <PageLayout title="我的考勤" description="查看各门课程的出勤记录与出勤率。">
    <template #actions>
      <el-button :disabled="!rows.length" @click="exportCsv">导出记录</el-button>
      <el-button type="primary" @click="$router.push('/student/checkin')">课堂签到</el-button>
    </template>

    <template #summary>
      <StatTile label="总次数" :value="stats.total" />
      <StatTile label="出勤" :value="stats.present" tone="success" />
      <StatTile label="迟到" :value="stats.late" :tone="stats.late ? 'warning' : 'default'" />
      <StatTile label="缺勤" :value="stats.absent" :tone="stats.absent ? 'danger' : 'default'" />
      <StatTile label="请假" :value="stats.leave" />
      <StatTile label="出勤率" :value="attendRate" unit="%" tone="brand" />
    </template>

    <el-alert
      v-for="s in activeSessions"
      :key="s.id"
      type="warning"
      show-icon
      :closable="false"
      class="active"
      @click="$router.push('/student/checkin')"
    >
      {{ s.coursename }} 正在签到中，截止 {{ formatDate(s.endTime, 'minute') }}
    </el-alert>

    <FilterBar :loading="loading" @search="search" @reset="reset">
      <el-select v-model="filters.courseid" placeholder="全部课程" clearable>
        <el-option v-for="c in courses" :key="c.courseid" :label="c.coursename" :value="c.courseid" />
      </el-select>
      <el-date-picker
        v-model="filters.dateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
      />
    </FilterBar>

    <DataTable
      v-model:page="page"
      v-model:page-size="pageSize"
      :columns="columns"
      :data="rows"
      :loading="loading"
      :total="total"
      empty-text="没有匹配的考勤记录"
      @change="loadRecords"
    >
      <template #status="{ row }">
        <el-tag :type="STATUS_TONE[row.status] || 'info'" size="small" disable-transitions>
          {{ STATUS_TEXT[row.status] || '—' }}
        </el-tag>
      </template>
      <template #time="{ row }">{{ formatDate(row.createtime, 'minute') }}</template>
    </DataTable>
  </PageLayout>
</template>

<script setup>
/** 我的考勤（学生端） */
import { computed, onMounted, reactive, ref } from 'vue'
import { useStore } from 'vuex'
import { DataTable, FilterBar, PageLayout, StatTile } from '@/components/ui'
import { listActiveCheckinSessions, listAttendanceByStudent } from '@/api/attendance'
import { listCoursesByClass } from '@/api/course'
import { downloadCsv } from '@/utils/excel'
import { formatDate } from '@/utils/format'

const STATUS_TEXT = { 1: '出勤', 2: '迟到', 3: '早退', 4: '缺勤', 5: '请假' }
const STATUS_TONE = { 1: 'success', 2: 'warning', 3: 'warning', 4: 'danger', 5: 'info' }

const columns = [
  { prop: 'coursename', label: '课程', minWidth: 180 },
  { prop: 'attenddate', label: '日期', width: 130 },
  { label: '状态', width: 100, slot: 'status' },
  { prop: 'remark', label: '备注', minWidth: 160 },
  { label: '记录时间', width: 170, slot: 'time' }
]

const store = useStore()
const studentid = store.state.user?.studentid
const classid = store.state.user?.classid

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const courses = ref([])
const activeSessions = ref([])
const filters = reactive({ courseid: null, dateRange: [] })

const stats = reactive({ total: 0, present: 0, late: 0, absent: 0, leave: 0 })

const attendRate = computed(() => {
  if (!stats.total) return '0.0'
  return (((stats.present + stats.late) / stats.total) * 100).toFixed(1)
})

const filterParams = () => {
  const params = {}
  if (filters.courseid) params.courseid = filters.courseid
  if (filters.dateRange?.length === 2) {
    params.startDate = filters.dateRange[0]
    params.endDate = filters.dateRange[1]
  }
  return params
}

const loadRecords = async () => {
  if (!studentid) return
  loading.value = true
  try {
    const res = await listAttendanceByStudent(studentid, {
      ...filterParams(),
      page: page.value,
      pageSize: pageSize.value
    }).catch(() => null)
    rows.value = res?.data || []
    total.value = res?.total ?? rows.value.length
  } finally {
    loading.value = false
  }
}

/**
 * 统计要覆盖全部符合条件的记录，只能整表拉。
 * 它只随筛选条件变化，翻页时不必重算 —— 原实现每翻一页都会重拉一万条。
 */
const loadStats = async () => {
  if (!studentid) return
  const res = await listAttendanceByStudent(studentid, {
    ...filterParams(),
    page: 1,
    pageSize: 10000
  }).catch(() => null)

  const all = res?.data || []
  stats.total = all.length
  stats.present = all.filter((r) => r.status === 1).length
  stats.late = all.filter((r) => r.status === 2).length
  stats.absent = all.filter((r) => r.status === 4).length
  stats.leave = all.filter((r) => r.status === 5).length
}

const search = () => {
  page.value = 1
  return Promise.all([loadRecords(), loadStats()])
}

const reset = () => {
  filters.courseid = null
  filters.dateRange = []
  return search()
}

onMounted(async () => {
  if (classid) {
    const [courseRes, sessionRes] = await Promise.all([
      listCoursesByClass(classid).catch(() => null),
      listActiveCheckinSessions(classid).catch(() => null)
    ])
    courses.value = courseRes?.data || []
    activeSessions.value = sessionRes?.data || []
  }
  await search()
})

const exportCsv = () => {
  downloadCsv({
    headers: ['课程', '日期', '状态', '备注', '记录时间'],
    rows: rows.value.map((r) => [
      r.coursename,
      r.attenddate,
      STATUS_TEXT[r.status] || '—',
      r.remark || '',
      formatDate(r.createtime, 'minute')
    ]),
    fileName: `我的考勤_${formatDate(new Date(), 'date')}.csv`
  })
}
</script>

<style scoped>
.active {
  cursor: pointer;
}
</style>

<template>
  <PageLayout title="学生考勤管理" description="发起课堂签到、登记与维护考勤记录。">
    <template #actions>
      <el-button type="primary" @click="checkinPanel?.open()">发起签到</el-button>
      <el-button @click="openBatch">批量考勤</el-button>
      <el-button :disabled="!records.length" @click="exportCsv">导出 CSV</el-button>
    </template>

    <CheckinPanel
      ref="checkinPanel"
      :teacherid="teacherid"
      :courses="courses"
      :resolve-classes="resolveClasses"
      @closed="loadRecords"
    />

    <FilterBar @search="search" @reset="reset">
      <el-select v-model="query.courseid" placeholder="全部课程" clearable>
        <el-option
          v-for="c in courses"
          :key="c.courseid"
          :label="c.coursename"
          :value="c.courseid"
        />
      </el-select>

      <el-select v-model="query.classid" placeholder="全部班级" clearable>
        <el-option
          v-for="c in queryClasses"
          :key="c.classid"
          :label="c.classname"
          :value="c.classid"
        />
      </el-select>

      <el-date-picker
        v-model="query.attenddate"
        type="date"
        placeholder="选择日期"
        value-format="YYYY-MM-DD"
      />
    </FilterBar>

    <div v-if="records.length" class="stats">
      <el-tag type="success" disable-transitions>出勤 {{ stats.present }}</el-tag>
      <el-tag type="warning" disable-transitions>迟到 {{ stats.late }}</el-tag>
      <el-tag type="danger" disable-transitions>缺勤 {{ stats.absent }}</el-tag>
      <el-tag type="info" disable-transitions>请假 {{ stats.leave }}</el-tag>
      <span class="stats__rate">本页出勤率 {{ stats.rate }}%</span>
    </div>

    <DataTable
      :columns="columns"
      :data="records"
      :loading="loading"
      :total="total"
      v-model:page="page"
      v-model:page-size="pageSize"
      row-key="id"
      empty-text="暂无考勤记录"
      empty-hint="可先发起签到，或用「批量考勤」直接登记"
      actions-width="140"
      @change="loadRecords"
    >
      <template #status="{ row }">
        <el-tag :type="STATUS_TONE[row.status] || 'info'" disable-transitions>
          {{ STATUS_TEXT[row.status] || '—' }}
        </el-tag>
      </template>

      <template #actions="{ row }">
        <el-button link type="primary" @click="openEdit(row)">修改</el-button>
        <el-button link type="danger" @click="remove(row)">删除</el-button>
      </template>
    </DataTable>

    <!-- 批量考勤 -->
    <el-dialog v-model="batch.visible" title="批量考勤" width="800px">
      <el-form label-width="60px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="课程">
              <el-select v-model="batch.courseid" class="full">
                <el-option
                  v-for="c in courses"
                  :key="c.courseid"
                  :label="c.coursename"
                  :value="c.courseid"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="班级">
              <el-select v-model="batch.classid" class="full">
                <el-option
                  v-for="c in batchClasses"
                  :key="c.classid"
                  :label="c.classname"
                  :value="c.classid"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="日期">
              <el-date-picker
                v-model="batch.attenddate"
                type="date"
                value-format="YYYY-MM-DD"
                class="full"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <DataTable
        :columns="batchColumns"
        :data="batch.students"
        :show-pagination="false"
        row-key="studentid"
        max-height="400"
        empty-text="请先选择课程与班级"
      >
        <template #status="{ row }">
          <el-select v-model="row.status" size="small">
            <el-option v-for="(text, value) in STATUS_TEXT" :key="value" :value="Number(value)" :label="text" />
          </el-select>
        </template>
        <template #remark="{ row }">
          <el-input v-model="row.remark" size="small" placeholder="备注" />
        </template>
      </DataTable>

      <template #footer>
        <el-button @click="batch.visible = false">取消</el-button>
        <el-button type="primary" :loading="batch.saving" @click="saveBatch">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改考勤 -->
    <FormDialog
      v-model="edit.visible"
      title="修改考勤"
      width="420px"
      :model="edit.form"
      :loading="edit.saving"
      @submit="saveEdit"
    >
      <el-form-item label="状态">
        <el-select v-model="edit.form.status" class="full">
          <el-option v-for="(text, value) in STATUS_TEXT" :key="value" :value="Number(value)" :label="text" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="edit.form.remark" placeholder="选填" />
      </el-form-item>
    </FormDialog>
  </PageLayout>
</template>

<script setup>
/** 学生考勤管理（教师端） */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { DataTable, FilterBar, FormDialog, PageLayout } from '@/components/ui'
import CheckinPanel from './components/CheckinPanel.vue'
import {
  batchAddAttendance,
  deleteAttendance,
  listAttendance,
  updateAttendance
} from '@/api/attendance'
import { listCoursesByTeacher } from '@/api/course'
import { listAllClasses } from '@/api/classes'
import { listStudents } from '@/api/student'
import { getCurrentSemester } from '@/utils/semester'
import { downloadCsv } from '@/utils/excel'
import { confirmDelete } from '@/utils/confirm'
import { formatDate } from '@/utils/format'

const STATUS_TEXT = { 1: '出勤', 2: '迟到', 3: '早退', 4: '缺勤', 5: '请假' }
const STATUS_TONE = { 1: 'success', 2: 'warning', 3: 'warning', 4: 'danger', 5: 'info' }

/** 一个班的学生数远小于此，一次取完 */
const CLASS_ROSTER_SIZE = 200

const columns = [
  { prop: 'studentno', label: '学号', width: 120 },
  { prop: 'studentname', label: '姓名', width: 100 },
  { prop: 'classname', label: '班级', width: 150 },
  { prop: 'coursename', label: '课程', minWidth: 160 },
  { prop: 'attenddate', label: '日期', width: 120 },
  { label: '状态', width: 100, slot: 'status' },
  { prop: 'remark', label: '备注', minWidth: 140 }
]

const batchColumns = [
  { prop: 'studentno', label: '学号', width: 120 },
  { prop: 'studentname', label: '姓名', width: 120 },
  { label: '状态', width: 150, slot: 'status' },
  { label: '备注', minWidth: 180, slot: 'remark' }
]

const store = useStore()
const teacherid = store.state.user?.teacherid

const courses = ref([])
const allClasses = ref([])
const checkinPanel = ref(null)

const records = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const loading = ref(false)

const query = reactive({ courseid: null, classid: null, attenddate: null })

/**
 * 课程与班级是多对多，后端返回的形式有两种：
 * 新数据带 classIds/classNames 数组，老数据只有逗号分隔的 classNamesStr。
 */
const resolveClasses = (courseid) => {
  const course = courses.value.find((c) => c.courseid === courseid)
  if (!course) return []

  if (Array.isArray(course.classIds) && course.classIds.length) {
    return course.classIds.map((id, index) => ({
      classid: id,
      classname:
        course.classNames?.[index] ||
        allClasses.value.find((c) => c.classid === id)?.classname ||
        String(id)
    }))
  }

  return String(course.classNamesStr || '')
    .split(/[，,]/)
    .map((name) => allClasses.value.find((c) => c.classname === name.trim()))
    .filter(Boolean)
    .map((c) => ({ classid: c.classid, classname: c.classname }))
}

const queryClasses = computed(() => resolveClasses(query.courseid))
const batchClasses = computed(() => resolveClasses(batch.courseid))

// 换课程后原班级多半不属于新课程，清掉避免查出空结果
watch(
  () => query.courseid,
  () => {
    if (!queryClasses.value.some((c) => c.classid === query.classid)) query.classid = null
  }
)

/* ---------- 考勤记录 ---------- */

const loadRecords = async () => {
  loading.value = true
  try {
    const res = await listAttendance({
      ...query,
      courseTeacherId: teacherid,
      page: page.value,
      pageSize: pageSize.value
    }).catch(() => null)
    records.value = res?.data || []
    total.value = Number(res?.total || 0)
  } finally {
    loading.value = false
  }
}

const search = () => {
  page.value = 1
  loadRecords()
}

const reset = () => {
  Object.assign(query, { courseid: null, classid: null, attenddate: null })
  search()
}

onMounted(async () => {
  const [courseRes, classRes] = await Promise.all([
    teacherid
      ? listCoursesByTeacher(teacherid, { semester: getCurrentSemester() }).catch(() => null)
      : null,
    listAllClasses().catch(() => null)
  ])
  courses.value = courseRes?.data || []
  allClasses.value = classRes?.data || []
  loadRecords()
})

/** 按当前页统计，标题里已注明「本页」避免误解为全量 */
const stats = computed(() => {
  const countOf = (status) => records.value.filter((r) => r.status === status).length
  const present = countOf(1)
  const late = countOf(2)
  const totalRows = records.value.length

  return {
    present,
    late,
    absent: countOf(4),
    leave: countOf(5),
    rate: totalRows ? (((present + late) / totalRows) * 100).toFixed(1) : '0.0'
  }
})

const exportCsv = () =>
  downloadCsv({
    fileName: `考勤记录_${formatDate(new Date())}.csv`,
    headers: ['学号', '姓名', '班级', '课程', '日期', '状态', '备注'],
    rows: records.value.map((r) => [
      r.studentno,
      r.studentname,
      r.classname,
      r.coursename,
      r.attenddate,
      STATUS_TEXT[r.status] || '—',
      r.remark || ''
    ])
  })

/* ---------- 批量考勤 ---------- */

const batch = reactive({
  visible: false,
  saving: false,
  courseid: null,
  classid: null,
  attenddate: null,
  students: []
})

const openBatch = () => {
  Object.assign(batch, { courseid: null, classid: null, attenddate: null, students: [] })
  batch.visible = true
}

watch(
  () => batch.courseid,
  () => {
    batch.classid = null
    batch.students = []
  }
)

watch(
  () => batch.classid,
  async (classid) => {
    if (!classid) {
      batch.students = []
      return
    }
    const res = await listStudents({ classid, page: 1, pageSize: CLASS_ROSTER_SIZE }).catch(() => null)
    // 默认全部出勤，教师只需改动少数几人
    batch.students = (res?.data || []).map((s) => ({ ...s, status: 1, remark: '' }))
  }
)

const saveBatch = async () => {
  if (!batch.courseid || !batch.classid || !batch.attenddate) {
    ElMessage.warning('请选择课程、班级与日期')
    return
  }

  batch.saving = true
  try {
    await batchAddAttendance({
      courseid: batch.courseid,
      classid: batch.classid,
      attenddate: batch.attenddate,
      teacherid,
      records: batch.students.map((s) => ({
        studentid: s.studentid,
        status: s.status,
        remark: s.remark
      }))
    })
    ElMessage.success('保存成功')
    batch.visible = false
    loadRecords()
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    batch.saving = false
  }
}

/* ---------- 单条编辑与删除 ---------- */

const edit = reactive({ visible: false, saving: false, form: { id: null, status: 1, remark: '' } })

const openEdit = (row) => {
  Object.assign(edit.form, { id: row.id, status: row.status, remark: row.remark || '' })
  edit.visible = true
}

const saveEdit = async () => {
  edit.saving = true
  try {
    await updateAttendance({ ...edit.form })
    ElMessage.success('修改成功')
    edit.visible = false
    loadRecords()
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    edit.saving = false
  }
}

const remove = async (row) => {
  const ok = await confirmDelete(`确定删除 ${row.studentname} 在 ${row.attenddate} 的考勤记录吗？`)
  if (!ok) return

  await deleteAttendance(row.id)
  ElMessage.success('删除成功')
  loadRecords()
}
</script>

<style scoped>
.full {
  width: 100%;
}

.stats {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--zy-space-2);
  padding: var(--zy-space-2) var(--zy-space-3);
  background: var(--zy-bg-sunken);
  border-radius: var(--zy-radius-md);
}

.stats__rate {
  margin-left: var(--zy-space-2);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-success-base);
}
</style>

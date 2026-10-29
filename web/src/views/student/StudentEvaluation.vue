<template>
  <PageLayout title="课程评价" description="对本学期修读的课程做出评价，评价可匿名提交。">
    <template #actions>
      <el-select v-model="semester" placeholder="选择学期" class="picker">
        <el-option v-for="s in SEMESTERS" :key="s" :label="s" :value="s" />
      </el-select>
    </template>

    <el-tabs v-model="activeTab">
      <el-tab-pane :label="`待评价 (${pendingCourses.length})`" name="pending">
        <div v-loading="loading" class="tiles">
          <el-empty v-if="!loading && !pendingCourses.length" description="本学期课程都已评价" />

          <article v-for="course in pendingCourses" :key="course.courseid" class="tile">
            <h3 class="tile__title">{{ course.coursename }}</h3>
            <p class="tile__meta">
              <span class="tile__avatar">{{ teacherInitial(course.teachername) }}</span>
              {{ course.teachername }} · {{ className }}
            </p>
            <p class="tile__desc">{{ course.description || '暂无课程描述' }}</p>
            <div class="tile__action">
              <el-button type="primary" @click="openCreate(course)">去评价</el-button>
            </div>
          </article>
        </div>
      </el-tab-pane>

      <el-tab-pane :label="`已评价 (${evaluations.length})`" name="done">
        <div v-if="evaluations.length" class="summary">
          平均综合评分 <b>{{ averageScore }}</b>
        </div>

        <DataTable
          :columns="columns"
          :data="evaluations"
          :loading="loading"
          :show-pagination="false"
          row-key="id"
          empty-text="本学期还没有评价过课程"
          actions-width="180"
        >
          <template #score="{ row }">
            <el-rate :model-value="Number(row.overallscore) || 0" disabled size="small" />
          </template>
          <template #time="{ row }">{{ formatDate(row.createtime, 'minute') }}</template>
          <template #actions="{ row }">
            <el-button link @click="openDetail(row)">查看</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </DataTable>
      </el-tab-pane>
    </el-tabs>

    <FormDialog
      v-model="formVisible"
      :title="editingId ? '修改评价' : '课程评价'"
      :model="form"
      :loading="submitting"
      width="600px"
      :confirm-text="editingId ? '保存' : '提交'"
      @submit="submit"
      @closed="resetForm"
    >
      <el-form-item label="课程">{{ currentCourse.coursename }}</el-form-item>
      <el-form-item v-for="item in RATING_FIELDS" :key="item.prop" :label="item.label">
        <el-rate v-model="form[item.prop]" :max="5" />
      </el-form-item>
      <el-form-item label="文字意见">
        <el-input v-model="form.comment" type="textarea" :rows="4" placeholder="请输入你的意见和建议" />
      </el-form-item>
      <el-form-item label="匿名评价">
        <el-switch v-model="form.anonymous" :active-value="1" :inactive-value="0" />
      </el-form-item>
    </FormDialog>

    <el-dialog v-model="detailVisible" title="评价详情" width="520px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="课程">{{ detailRow.coursename }}</el-descriptions-item>
        <el-descriptions-item label="评价时间">{{ formatDate(detailRow.createtime, 'minute') }}</el-descriptions-item>
        <el-descriptions-item v-for="item in RATING_FIELDS" :key="item.prop" :label="item.label">
          <el-rate :model-value="Number(detailRow[item.prop]) || 0" disabled />
        </el-descriptions-item>
        <el-descriptions-item label="文字意见">{{ detailRow.comment || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </PageLayout>
</template>

<script setup>
/** 课程评价（学生端） */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useStore } from 'vuex'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DataTable, FormDialog, PageLayout } from '@/components/ui'
import {
  createEvaluation,
  deleteEvaluation,
  listCoursesByClass,
  listEvaluationsByStudent,
  updateEvaluation
} from '@/api/course'
import { formatDate } from '@/utils/format'
import { getCurrentSemester } from '@/utils/semester'

const SEMESTERS = [
  '2026-2027-2', '2026-2027-1',
  '2025-2026-2', '2025-2026-1',
  '2024-2025-2', '2024-2025-1',
  '2023-2024-2', '2023-2024-1',
  '2022-2023-2', '2022-2023-1'
]

const RATING_FIELDS = [
  { prop: 'teachingscore', label: '教学态度' },
  { prop: 'contentscore', label: '内容质量' },
  { prop: 'difficultyscore', label: '难度适中' },
  { prop: 'overallscore', label: '综合评分' }
]

const columns = [
  { prop: 'coursename', label: '课程', minWidth: 200 },
  { label: '综合评分', width: 180, slot: 'score' },
  { label: '评价时间', width: 170, slot: 'time' }
]

const store = useStore()
const studentid = store.state.user?.studentid
const classid = store.state.user?.classid
const className = store.state.user?.classname || '—'

const activeTab = ref('pending')
const semester = ref(getCurrentSemester())
const loading = ref(false)

const pendingCourses = ref([])
const evaluations = ref([])

const averageScore = computed(() => {
  if (!evaluations.value.length) return '—'
  const sum = evaluations.value.reduce((total, e) => total + Number(e.overallscore || 0), 0)
  return (sum / evaluations.value.length).toFixed(1)
})

const teacherInitial = (name) => String(name || '').trim()[0] || '师'

/** 已评价列表只保留本学期的课程，否则切学期后仍会看到其他学期的记录 */
const load = async () => {
  if (!classid || !studentid) return
  loading.value = true
  try {
    const [courseRes, evalRes] = await Promise.all([
      listCoursesByClass(classid, { semester: semester.value }).catch(() => null),
      listEvaluationsByStudent(studentid).catch(() => null)
    ])

    const courses = courseRes?.data || []
    const semesterCourseIds = new Set(courses.map((c) => c.courseid))

    evaluations.value = (evalRes?.data || []).filter((e) => semesterCourseIds.has(e.courseid))

    const evaluatedIds = new Set(evaluations.value.map((e) => e.courseid))
    pendingCourses.value = courses.filter((c) => !evaluatedIds.has(c.courseid))
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(semester, load)

/* ---------- 评价表单 ---------- */

const formVisible = ref(false)
const submitting = ref(false)
const editingId = ref(null)
const currentCourse = ref({})

const emptyForm = {
  teachingscore: 5,
  contentscore: 5,
  difficultyscore: 5,
  overallscore: 5,
  comment: '',
  anonymous: 1
}

const form = reactive({ ...emptyForm })

const resetForm = () => {
  Object.assign(form, emptyForm)
  editingId.value = null
  currentCourse.value = {}
}

const openCreate = (course) => {
  resetForm()
  currentCourse.value = course
  formVisible.value = true
}

const openEdit = (row) => {
  resetForm()
  editingId.value = row.id
  currentCourse.value = { courseid: row.courseid, coursename: row.coursename }
  Object.assign(form, {
    teachingscore: row.teachingscore || 5,
    contentscore: row.contentscore || 5,
    difficultyscore: row.difficultyscore || 5,
    overallscore: row.overallscore || 5,
    comment: row.comment || '',
    anonymous: row.anonymous ?? 1
  })
  formVisible.value = true
}

const submit = async () => {
  submitting.value = true
  try {
    const payload = { ...form, courseid: currentCourse.value.courseid, studentid }
    if (editingId.value) await updateEvaluation({ ...payload, id: editingId.value })
    else await createEvaluation(payload)

    ElMessage.success(editingId.value ? '已保存' : '评价成功')
    formVisible.value = false
    await load()
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

/* ---------- 查看与删除 ---------- */

const detailVisible = ref(false)
const detailRow = ref({})

const openDetail = (row) => {
  detailRow.value = { ...row }
  detailVisible.value = true
}

const remove = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除对「${row.coursename}」的评价吗？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }

  try {
    await deleteEvaluation(row.id)
    ElMessage.success('已删除')
    if (detailRow.value?.id === row.id) detailVisible.value = false
    await load()
  } catch {
    // 失败提示由 http 拦截器统一处理
  }
}
</script>

<style scoped>
.picker {
  width: 200px;
}

.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--zy-space-4);
  min-height: 160px;
}

.tiles :deep(.el-empty) {
  grid-column: 1 / -1;
}

.tile {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-2);
  padding: var(--zy-space-4);
  background: var(--zy-bg-surface);
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-surface);
  transition: border-color var(--zy-duration-fast) var(--zy-ease-standard);
}

.tile:hover {
  border-color: var(--zy-brand-border);
}

.tile__title {
  margin: 0;
  font-size: var(--zy-font-size-lg);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tile__meta {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  margin: 0;
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.tile__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: var(--zy-radius-full);
  background: var(--zy-brand-soft);
  color: var(--zy-brand-base);
  font-size: var(--zy-font-size-xs);
  font-weight: var(--zy-font-weight-semibold);
}

.tile__desc {
  margin: 0;
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tile__action {
  margin-top: auto;
  padding-top: var(--zy-space-2);
}

.summary {
  margin-bottom: var(--zy-space-3);
  padding: var(--zy-space-2) var(--zy-space-4);
  background: var(--zy-bg-sunken);
  border-radius: var(--zy-radius-md);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.summary b {
  color: var(--zy-brand-base);
  font-size: var(--zy-font-size-md);
}

@media screen and (max-width: 768px) {
  .picker {
    width: 100%;
  }
}
</style>

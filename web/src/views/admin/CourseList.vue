<template>
  <PageLayout
    title="课程管理"
    description="维护开课信息、任课教师与开课班级，课程需归属到具体学期。"
    :breadcrumb="[{ label: '教务管理' }, { label: '课程管理' }]"
  >
    <template #actions>
      <el-button type="primary" @click="openCreate">新增课程</el-button>
    </template>

    <FilterBar :loading="loading" @search="search" @reset="reset">
      <el-input v-model="query.keyword" placeholder="课程名称" clearable />
    </FilterBar>

    <DataTable
      v-model:page="query.page"
      v-model:page-size="query.pageSize"
      :columns="columns"
      :data="rows"
      :loading="loading"
      :total="total"
      row-key="courseid"
      empty-text="没有匹配的课程"
      empty-hint="调整筛选条件，或先新增课程"
      actions-width="190"
      @change="load"
    >
      <template #classes="{ row }">
        <span v-if="row.classNamesStr">{{ row.classNamesStr }}</span>
        <el-tag v-else type="info" size="small" disable-transitions>未排班</el-tag>
      </template>

      <template #actions="{ row }">
        <el-button link @click="openDetail(row)">详情</el-button>
        <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        <el-button link type="danger" @click="remove(row)">删除</el-button>
      </template>
    </DataTable>

    <FormDialog
      v-model="formVisible"
      :title="isEdit ? '编辑课程' : '新增课程'"
      :model="form"
      :rules="rules"
      :loading="submitting"
      :columns="2"
      width="680px"
      @submit="handleSubmit"
      @closed="resetForm"
    >
      <el-form-item label="课程名称" prop="coursename" class="zy-form__full">
        <el-input v-model="form.coursename" placeholder="请输入课程名称" />
      </el-form-item>
      <el-form-item label="学期" prop="semester">
        <el-select v-model="form.semester" placeholder="请选择学期">
          <el-option v-for="s in semesterOptions" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="任课教师" prop="teacherid">
        <el-select v-model="form.teacherid" placeholder="请选择任课教师" filterable>
          <el-option v-for="t in teachers" :key="t.teacherid" :label="t.teachername" :value="t.teacherid" />
        </el-select>
      </el-form-item>
      <el-form-item label="课时" prop="hours">
        <el-input-number v-model="form.hours" :min="1" :max="200" controls-position="right" />
      </el-form-item>
      <el-form-item label="开课班级" prop="classIds">
        <el-select v-model="form.classIds" multiple collapse-tags collapse-tags-tooltip placeholder="请选择开课班级">
          <el-option v-for="c in classes" :key="c.classid" :label="c.classname" :value="c.classid" />
        </el-select>
      </el-form-item>
      <el-form-item label="课程描述" prop="description" class="zy-form__full">
        <el-input v-model="form.description" type="textarea" :rows="3" placeholder="选填" />
      </el-form-item>
    </FormDialog>

    <DetailDialog v-model="detailVisible" title="课程详情" :fields="detailFields" :data="detailRow" />
  </PageLayout>
</template>

<script setup>
/** 课程管理 */
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DataTable, DetailDialog, FilterBar, FormDialog, PageLayout } from '@/components/ui'
import { useCrudTable } from '@/composables/useCrudTable'
import {
  createCourse,
  deleteCourse,
  getCourse,
  listCourses,
  updateCourse
} from '@/api/course'
import { listTeachers } from '@/api/teacher'
import { listAllClasses } from '@/api/classes'
import { listTermOptions } from '@/api/academicTerm'
import { getSemesterOptions } from '@/utils/semester'

const columns = [
  { prop: 'coursename', label: '课程名称', minWidth: 180 },
  { prop: 'semester', label: '学期', width: 140 },
  { prop: 'coursecode', label: '课程码', width: 120 },
  { prop: 'teachername', label: '任课教师', width: 120 },
  { prop: 'hours', label: '课时', width: 80, align: 'right' },
  { label: '开课班级', minWidth: 160, slot: 'classes' }
]

const detailFields = [
  { label: '课程名称', prop: 'coursename' },
  { label: '学期', prop: 'semester' },
  { label: '课程码', prop: 'coursecode' },
  { label: '任课教师', prop: 'teachername' },
  { label: '课时', prop: 'hours' },
  { label: '开课班级', prop: 'classNamesStr' },
  { label: '课程描述', prop: 'description' }
]

const rules = {
  coursename: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  semester: [{ required: true, message: '请选择学期', trigger: 'change' }],
  teacherid: [{ required: true, message: '请选择任课教师', trigger: 'change' }],
  hours: [{ required: true, message: '请输入课时', trigger: 'blur' }]
}

const { rows, total, loading, query, load, search, reset, remove } = useCrudTable({
  fetch: listCourses,
  remove: (row) => deleteCourse(row.courseid),
  initialQuery: { keyword: '' },
  confirmText: (row) => `确定要删除课程「${row.coursename}」吗？其下的作业与资源将一并失去归属。`
})

/* ---------- 下拉选项 ---------- */

const teachers = ref([])
const classes = ref([])
const semesterOptions = ref(getSemesterOptions(5))

listTeachers({ page: 1, pageSize: 1000 })
  .then((res) => { teachers.value = res?.data || [] })
  .catch(() => { teachers.value = [] })

listAllClasses()
  .then((res) => { classes.value = res?.data || [] })
  .catch(() => { classes.value = [] })

// 学期主数据优先；升级脚本尚未执行的环境退回本地推算的学期选项
listTermOptions()
  .then((res) => {
    if (!res?.data?.length) return
    semesterOptions.value = res.data.map((item) => ({
      label: item.current ? `${item.termName}（当前）` : item.termName,
      value: item.termCode
    }))
  })
  .catch(() => {})

/* ---------- 新增与编辑 ---------- */

const formVisible = ref(false)
const submitting = ref(false)
const isEdit = ref(false)

const emptyForm = {
  courseid: null,
  coursename: '',
  semester: '',
  teacherid: null,
  hours: 32,
  classIds: [],
  description: ''
}

const form = reactive({ ...emptyForm })

const resetForm = () => Object.assign(form, emptyForm, { classIds: [] })

const openCreate = () => {
  resetForm()
  isEdit.value = false
  formVisible.value = true
}

/** 列表接口不返回 classIds，编辑前需按 id 取一次完整课程 */
const openEdit = async (row) => {
  const res = await getCourse(row.courseid).catch(() => null)
  if (!res?.data) return
  Object.assign(form, emptyForm, res.data, { classIds: res.data.classIds || [] })
  isEdit.value = true
  formVisible.value = true
}

const handleSubmit = async () => {
  submitting.value = true
  try {
    if (isEdit.value) await updateCourse(form)
    else await createCourse(form)
    ElMessage.success(isEdit.value ? '已保存' : '已新增')
    formVisible.value = false
    await load()
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

/* ---------- 详情 ---------- */

const detailVisible = ref(false)
const detailRow = ref({})

const openDetail = (row) => {
  detailRow.value = row
  detailVisible.value = true
}
</script>

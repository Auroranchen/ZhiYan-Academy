<template>
  <PageLayout
    title="学年学期管理"
    description="统一维护开课、作业、考试与统计所使用的学期主数据。"
    :breadcrumb="[{ label: '基础数据' }, { label: '学年学期管理' }]"
    :badge="currentTerm ? `当前 ${currentTerm.termName}` : ''"
    badge-type="success"
  >
    <template #actions>
      <el-button type="primary" @click="openCreate">新增学期</el-button>
    </template>

    <FilterBar :loading="loading" @search="load" @reset="resetFilters">
      <el-input v-model="filters.keyword" placeholder="学期名称或编码" clearable />
      <el-select v-model="filters.status" placeholder="全部状态" clearable>
        <el-option v-for="(text, value) in STATUS_TEXT" :key="value" :label="text" :value="value" />
      </el-select>
    </FilterBar>

    <DataTable
      :columns="columns"
      :data="rows"
      :loading="loading"
      :show-pagination="false"
      row-key="termId"
      empty-text="还没有学期"
      empty-hint="新增学期后，课程与作业才能正确归属"
      actions-width="270"
    >
      <template #range="{ row }">
        {{ row.startDate || '—' }} 至 {{ row.endDate || '—' }}
      </template>

      <template #status="{ row }">
        <el-tag :type="STATUS_TONE[row.status] || 'info'" size="small" disable-transitions>
          {{ STATUS_TEXT[row.status] || row.status }}
        </el-tag>
        <el-tag v-if="row.current" type="success" effect="dark" size="small" class="tag-gap" disable-transitions>
          当前
        </el-tag>
      </template>

      <template #actions="{ row }">
        <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        <el-button v-if="!row.current" link type="success" @click="activate(row)">设为当前</el-button>
        <el-button v-if="!row.current && row.status !== 'ARCHIVED'" link @click="archive(row)">归档</el-button>
        <el-button v-if="!row.current && !row.courseCount" link type="danger" @click="remove(row)">
          删除
        </el-button>
      </template>
    </DataTable>

    <FormDialog
      v-model="formVisible"
      :title="editingId ? '编辑学期' : '新增学期'"
      :model="form"
      :rules="rules"
      :loading="saving"
      width="520px"
      @submit="submit"
      @closed="resetForm"
    >
      <el-form-item label="学期编码" prop="termCode">
        <el-input v-model="form.termCode" placeholder="例如：2026-2027-1" :disabled="codeLocked" />
        <p v-if="codeLocked" class="tip">该学期下已有课程，编码不可修改，避免历史数据失去归属。</p>
      </el-form-item>
      <el-form-item label="学期名称" prop="termName">
        <el-input v-model="form.termName" placeholder="例如：2026-2027 学年第一学期" />
      </el-form-item>
      <el-form-item label="开始日期" prop="startDate">
        <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" placeholder="选择开始日期" />
      </el-form-item>
      <el-form-item label="结束日期" prop="endDate">
        <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" placeholder="选择结束日期" />
      </el-form-item>
    </FormDialog>
  </PageLayout>
</template>

<script setup>
/** 学年学期管理 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DataTable, FilterBar, FormDialog, PageLayout } from '@/components/ui'
import {
  activateTerm,
  archiveTerm,
  createTerm,
  deleteTerm,
  getCurrentTerm,
  listTerms,
  updateTerm
} from '@/api/academicTerm'

const STATUS_TEXT = { PLANNED: '待启用', ACTIVE: '当前学期', ARCHIVED: '已归档' }
const STATUS_TONE = { PLANNED: 'info', ACTIVE: 'success', ARCHIVED: 'warning' }

const columns = [
  { prop: 'termCode', label: '学期编码', width: 150 },
  { prop: 'termName', label: '学期名称', minWidth: 200 },
  { label: '起止日期', minWidth: 220, slot: 'range' },
  { prop: 'courseCount', label: '课程数', width: 90, align: 'right' },
  { label: '状态', width: 160, slot: 'status' }
]

const rows = ref([])
const currentTerm = ref(null)
const loading = ref(false)
const saving = ref(false)
const filters = reactive({ keyword: '', status: '' })

const load = async () => {
  loading.value = true
  try {
    const [list, current] = await Promise.all([
      listTerms({ ...filters }).catch(() => null),
      getCurrentTerm().catch(() => null)
    ])
    rows.value = list?.data || []
    currentTerm.value = current?.data || null
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  filters.keyword = ''
  filters.status = ''
  return load()
}

onMounted(load)

/* ---------- 新增与编辑 ---------- */

const formVisible = ref(false)
const editingId = ref(null)
const editingCourseCount = ref(0)

const codeLocked = computed(() => Boolean(editingId.value && editingCourseCount.value > 0))

const emptyForm = { termCode: '', termName: '', startDate: '', endDate: '' }
const form = reactive({ ...emptyForm })

const validateDateRange = (_rule, _value, callback) => {
  if (form.startDate && form.endDate && form.endDate < form.startDate) {
    callback(new Error('结束日期不能早于开始日期'))
  } else {
    callback()
  }
}

const rules = {
  termCode: [
    { required: true, message: '请输入学期编码', trigger: 'blur' },
    { pattern: /^\d{4}-\d{4}-[1-3]$/, message: '格式应为 2025-2026-1', trigger: 'blur' }
  ],
  termName: [{ required: true, message: '请输入学期名称', trigger: 'blur' }],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  endDate: [
    { required: true, message: '请选择结束日期', trigger: 'change' },
    { validator: validateDateRange, trigger: 'change' }
  ]
}

const resetForm = () => {
  editingId.value = null
  editingCourseCount.value = 0
  Object.assign(form, emptyForm)
}

const openCreate = () => {
  resetForm()
  formVisible.value = true
}

const openEdit = (row) => {
  editingId.value = row.termId
  editingCourseCount.value = row.courseCount || 0
  Object.assign(form, emptyForm, {
    termCode: row.termCode,
    termName: row.termName,
    startDate: row.startDate,
    endDate: row.endDate
  })
  formVisible.value = true
}

const submit = async () => {
  saving.value = true
  try {
    if (editingId.value) await updateTerm(editingId.value, form)
    else await createTerm(form)
    ElMessage.success(editingId.value ? '已保存' : '已新增')
    formVisible.value = false
    await load()
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    saving.value = false
  }
}

/* ---------- 状态流转 ---------- */

const confirmThen = async (message, title, action, successText) => {
  try {
    await ElMessageBox.confirm(message, title, { type: 'warning' })
  } catch {
    return
  }
  try {
    await action()
    ElMessage.success(successText)
    await load()
  } catch {
    // 失败提示由 http 拦截器统一处理
  }
}

const activate = (row) =>
  confirmThen(
    `启用「${row.termName}」后，原当前学期将自动归档。是否继续？`,
    '切换当前学期',
    () => activateTerm(row.termId),
    '当前学期已切换'
  )

const archive = (row) =>
  confirmThen(
    `确定归档「${row.termName}」吗？历史数据仍会保留。`,
    '归档学期',
    () => archiveTerm(row.termId),
    '学期已归档'
  )

const remove = (row) =>
  confirmThen(
    `确定删除「${row.termName}」吗？删除后不可恢复。`,
    '删除学期',
    () => deleteTerm(row.termId),
    '学期已删除'
  )
</script>

<style scoped>
.tip {
  margin: var(--zy-space-1) 0 0;
  font-size: var(--zy-font-size-xs);
  line-height: var(--zy-line-height-normal);
  color: var(--zy-text-tertiary);
}

.tag-gap {
  margin-left: var(--zy-space-1);
}
</style>

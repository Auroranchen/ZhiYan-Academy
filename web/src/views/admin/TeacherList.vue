<template>
  <PageLayout
    title="教师管理"
    description="维护教师账号与职称、部门信息，支持 Excel 批量导入。"
    :breadcrumb="[{ label: '教务管理' }, { label: '教师管理' }]"
  >
    <template #actions>
      <el-button @click="handleDownloadTemplate">下载模板</el-button>
      <el-button @click="importVisible = true">批量导入</el-button>
      <el-button type="primary" @click="openCreate">新增教师</el-button>
    </template>

    <FilterBar :loading="loading" @search="search" @reset="reset">
      <el-input v-model="query.keyword" placeholder="工号或姓名" clearable />
    </FilterBar>

    <DataTable
      v-model:page="query.page"
      v-model:page-size="query.pageSize"
      :columns="columns"
      :data="rows"
      :loading="loading"
      :total="total"
      row-key="teacherid"
      empty-text="没有匹配的教师"
      empty-hint="调整筛选条件，或先新增教师账号"
      @change="load"
    >
      <template #title="{ row }">
        <el-tag v-if="row.title" size="small" disable-transitions>{{ row.title }}</el-tag>
        <span v-else>—</span>
      </template>

      <template #contact="{ row }">
        <div class="cell-stack">
          <span>{{ row.phone || '—' }}</span>
          <span class="cell-stack__sub">{{ row.email || '—' }}</span>
        </div>
      </template>

      <template #actions="{ row }">
        <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        <el-button link type="danger" @click="remove(row)">删除</el-button>
      </template>
    </DataTable>

    <FormDialog
      v-model="formVisible"
      :title="isEdit ? '编辑教师' : '新增教师'"
      :model="form"
      :rules="rules"
      :loading="submitting"
      :columns="2"
      width="680px"
      @submit="handleSubmit"
      @closed="resetForm"
    >
      <el-form-item label="工号" prop="teacherno">
        <el-input v-model="form.teacherno" placeholder="请输入工号" />
      </el-form-item>
      <el-form-item label="姓名" prop="teachername">
        <el-input v-model="form.teachername" placeholder="请输入姓名" />
      </el-form-item>
      <el-form-item label="性别" prop="sex">
        <el-radio-group v-model="form.sex">
          <el-radio value="男">男</el-radio>
          <el-radio value="女">女</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          show-password
          :placeholder="isEdit ? '留空则不修改' : '默认 123456'"
        />
      </el-form-item>
      <el-form-item label="职称" prop="title">
        <el-select v-model="form.title" placeholder="请选择职称" clearable>
          <el-option v-for="t in TITLES" :key="t" :label="t" :value="t" />
        </el-select>
      </el-form-item>
      <el-form-item label="部门" prop="department">
        <el-input v-model="form.department" placeholder="请输入所在部门" />
      </el-form-item>
      <el-form-item label="电话" prop="phone">
        <el-input v-model="form.phone" placeholder="请输入联系电话" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="form.email" placeholder="请输入邮箱" />
      </el-form-item>
    </FormDialog>

    <el-dialog v-model="importVisible" title="批量导入教师" width="560px">
      <div class="import">
        <el-upload
          :show-file-list="false"
          :auto-upload="false"
          accept=".xlsx,.xls"
          :on-change="handleFilePick"
        >
          <el-button type="primary" :loading="importing">选择 Excel 文件</el-button>
        </el-upload>
        <p class="import__tip">
          支持 .xlsx / .xls。必需列：工号、姓名；可选列：性别、职称、部门、电话、邮箱。
        </p>

        <div v-if="importResult" class="import__result">
          <div class="import__summary">
            <span>总计 {{ importResult.total }}</span>
            <span class="is-success">成功 {{ importResult.success }}</span>
            <span v-if="importResult.fail" class="is-fail">失败 {{ importResult.fail }}</span>
          </div>
          <ul v-if="importResult.errors.length" class="import__errors">
            <li v-for="(msg, i) in importResult.errors" :key="i">{{ msg }}</li>
          </ul>
        </div>
      </div>
      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>

<script setup>
/** 教师管理 */
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DataTable, FilterBar, FormDialog, PageLayout } from '@/components/ui'
import { useCrudTable } from '@/composables/useCrudTable'
import { createTeacher, deleteTeacher, listTeachers, updateTeacher } from '@/api/teacher'
import { downloadTemplate, importRows, readSheetRows } from '@/utils/excel'

const TITLES = ['教授', '副教授', '讲师', '助教']

const columns = [
  { prop: 'teacherno', label: '工号', width: 130 },
  { prop: 'teachername', label: '姓名', width: 110 },
  { prop: 'sex', label: '性别', width: 80, align: 'center' },
  { label: '职称', width: 110, slot: 'title' },
  { prop: 'department', label: '部门', minWidth: 160 },
  { label: '联系方式', minWidth: 200, slot: 'contact' }
]

const rules = {
  teacherno: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  teachername: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const { rows, total, loading, query, load, search, reset, remove } = useCrudTable({
  fetch: listTeachers,
  remove: (row) => deleteTeacher(row.teacherid),
  initialQuery: { keyword: '' },
  confirmText: (row) => `确定要删除教师「${row.teachername}」吗？删除后不可恢复。`
})

/* ---------- 新增与编辑 ---------- */

const formVisible = ref(false)
const submitting = ref(false)
const isEdit = ref(false)

const emptyForm = {
  teacherid: null,
  teacherno: '',
  teachername: '',
  password: '',
  sex: '男',
  title: '',
  department: '',
  phone: '',
  email: ''
}

const form = reactive({ ...emptyForm })

const resetForm = () => Object.assign(form, emptyForm)

const openCreate = () => {
  resetForm()
  isEdit.value = false
  formVisible.value = true
}

const openEdit = (row) => {
  Object.assign(form, emptyForm, row, { password: '' })
  isEdit.value = true
  formVisible.value = true
}

const handleSubmit = async () => {
  submitting.value = true
  try {
    const payload = { ...form }
    if (isEdit.value && !payload.password) delete payload.password

    if (isEdit.value) await updateTeacher(payload)
    else await createTeacher({ ...payload, password: payload.password || '123456' })

    ElMessage.success(isEdit.value ? '已保存' : '已新增')
    formVisible.value = false
    await load()
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

/* ---------- 批量导入 ---------- */

const importVisible = ref(false)
const importing = ref(false)
const importResult = ref(null)

const handleDownloadTemplate = async () => {
  try {
    await downloadTemplate({
      headers: ['工号', '姓名', '性别', '职称', '部门', '电话', '邮箱'],
      sample: ['T2020001', '李老师', '女', '副教授', '计算机学院', '13900000000', 'li@example.com'],
      fileName: '教师导入模板.xlsx',
      sheetName: '教师信息'
    })
  } catch (e) {
    ElMessage.error(e.message)
  }
}

const handleFilePick = async (file) => {
  const raw = file.raw
  if (!raw) return

  importing.value = true
  importResult.value = null

  try {
    const sheetRows = await readSheetRows(raw)

    importResult.value = await importRows(sheetRows, async (row) => {
      const teacherno = row.工号 || row.teacherno
      const teachername = row.姓名 || row.teachername
      if (!teacherno || !teachername) throw new Error('缺少工号或姓名')

      await createTeacher({
        teacherno,
        teachername,
        password: '123456',
        sex: row.性别 || row.sex || '男',
        title: row.职称 || row.title || '',
        department: row.部门 || row.department || '',
        phone: row.电话 || row.phone || '',
        email: row.邮箱 || row.email || ''
      })
    })

    const { success, fail } = importResult.value
    if (success) ElMessage.success(`成功导入 ${success} 条`)
    if (fail) ElMessage.warning(`${fail} 条导入失败，请查看下方明细`)
    if (success) await load()
  } catch (e) {
    ElMessage.error(e.message || '导入失败')
  } finally {
    importing.value = false
  }
}
</script>

<style scoped>
.cell-stack {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.cell-stack__sub {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.import {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-3);
}

.import__tip {
  margin: 0;
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-tertiary);
  line-height: var(--zy-line-height-relaxed);
}

.import__result {
  padding: var(--zy-space-3);
  background: var(--zy-bg-sunken);
  border-radius: var(--zy-radius-md);
}

.import__summary {
  display: flex;
  gap: var(--zy-space-4);
  font-size: var(--zy-font-size-sm);
  font-variant-numeric: tabular-nums;
}

.import__summary .is-success { color: var(--zy-success-base); }
.import__summary .is-fail { color: var(--zy-danger-base); }

.import__errors {
  margin: var(--zy-space-2) 0 0;
  padding-left: var(--zy-space-4);
  max-height: 160px;
  overflow: auto;
  font-size: var(--zy-font-size-xs);
  color: var(--zy-danger-base);
}
</style>

<template>
  <PageLayout
    title="学生管理"
    description="维护学生账号、所属专业与班级，支持 Excel 批量导入。"
    :breadcrumb="[{ label: '教务管理' }, { label: '学生管理' }]"
  >
    <template #actions>
      <el-button @click="handleDownloadTemplate">下载模板</el-button>
      <el-button @click="importVisible = true">批量导入</el-button>
      <el-button type="primary" @click="openCreate">新增学生</el-button>
    </template>

    <FilterBar :loading="loading" @search="search" @reset="reset">
      <el-input v-model="query.keyword" placeholder="学号或姓名" clearable />
      <el-select v-model="query.majorid" placeholder="全部专业" clearable>
        <el-option v-for="m in majors" :key="m.majorid" :label="m.majorname" :value="m.majorid" />
      </el-select>
      <el-select v-model="query.classid" placeholder="全部班级" clearable :disabled="!query.majorid">
        <el-option v-for="c in filterClasses" :key="c.classid" :label="c.classname" :value="c.classid" />
      </el-select>
    </FilterBar>

    <DataTable
      v-model:page="query.page"
      v-model:page-size="query.pageSize"
      :columns="columns"
      :data="rows"
      :loading="loading"
      :total="total"
      row-key="studentid"
      empty-text="没有匹配的学生"
      empty-hint="调整筛选条件，或先新增学生账号"
      @change="load"
    >
      <template #sex="{ row }">
        <span>{{ row.sex || '—' }}</span>
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
      :title="isEdit ? '编辑学生' : '新增学生'"
      :model="form"
      :rules="rules"
      :loading="submitting"
      :columns="2"
      width="680px"
      @submit="handleSubmit"
      @closed="resetForm"
    >
      <el-form-item label="学号" prop="studentno">
        <el-input v-model="form.studentno" placeholder="请输入学号" />
      </el-form-item>
      <el-form-item label="姓名" prop="studentname">
        <el-input v-model="form.studentname" placeholder="请输入姓名" />
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
      <el-form-item label="专业" prop="majorid">
        <el-select v-model="form.majorid" placeholder="请选择专业" clearable>
          <el-option v-for="m in majors" :key="m.majorid" :label="m.majorname" :value="m.majorid" />
        </el-select>
      </el-form-item>
      <el-form-item label="班级" prop="classid">
        <el-select v-model="form.classid" placeholder="请选择班级" :disabled="!form.majorid">
          <el-option v-for="c in formClasses" :key="c.classid" :label="c.classname" :value="c.classid" />
        </el-select>
      </el-form-item>
      <el-form-item label="电话" prop="phone">
        <el-input v-model="form.phone" placeholder="请输入联系电话" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="form.email" placeholder="请输入邮箱" />
      </el-form-item>
      <el-form-item label="地址" prop="address" class="zy-form__full">
        <el-input v-model="form.address" placeholder="请输入通讯地址" />
      </el-form-item>
    </FormDialog>

    <el-dialog v-model="importVisible" title="批量导入学生" width="560px">
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
          支持 .xlsx / .xls。必需列：学号、姓名、班级名称；可选列：性别、电话、邮箱、地址、专业名称。
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
/** 学生管理 */
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { DataTable, FilterBar, FormDialog, PageLayout } from '@/components/ui'
import { useCrudTable } from '@/composables/useCrudTable'
import { createStudent, deleteStudent, listStudents, updateStudent } from '@/api/student'
import { listAllMajors } from '@/api/major'
import { listClasses, listClassesByMajor } from '@/api/classes'
import { downloadTemplate, importRows, readSheetRows } from '@/utils/excel'

const columns = [
  { prop: 'studentno', label: '学号', width: 130 },
  { prop: 'studentname', label: '姓名', width: 110 },
  { label: '性别', width: 80, align: 'center', slot: 'sex' },
  { prop: 'majorname', label: '专业', minWidth: 160 },
  { prop: 'classname', label: '班级', minWidth: 130 },
  { label: '联系方式', minWidth: 200, slot: 'contact' }
]

const rules = {
  studentno: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  studentname: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  classid: [{ required: true, message: '请选择班级', trigger: 'change' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const { rows, total, loading, query, load, search, reset, remove } = useCrudTable({
  fetch: listStudents,
  remove: (row) => deleteStudent(row.studentid),
  initialQuery: { keyword: '', majorid: null, classid: null },
  confirmText: (row) => `确定要删除学生「${row.studentname}」吗？删除后不可恢复。`
})

/* ---------- 专业与班级级联 ---------- */

const majors = ref([])
const filterClasses = ref([])
const formClasses = ref([])

const loadMajors = async () => {
  const res = await listAllMajors().catch(() => null)
  majors.value = res?.data || []
}
loadMajors()

const fetchClasses = async (majorid) => {
  if (!majorid) return []
  const res = await listClassesByMajor(majorid).catch(() => null)
  return res?.data || []
}

watch(() => query.majorid, async (majorid) => {
  query.classid = null
  filterClasses.value = await fetchClasses(majorid)
})

/* ---------- 新增与编辑 ---------- */

const formVisible = ref(false)
const submitting = ref(false)
const isEdit = ref(false)

const emptyForm = {
  studentid: null,
  studentno: '',
  studentname: '',
  password: '',
  sex: '男',
  phone: '',
  email: '',
  address: '',
  majorid: null,
  classid: null
}

const form = reactive({ ...emptyForm })

watch(() => form.majorid, async (majorid, previous) => {
  formClasses.value = await fetchClasses(majorid)
  // 仅在用户主动改专业时清空班级，回填编辑数据时要保留原班级
  if (previous !== undefined && majorid !== previous) {
    const stillValid = formClasses.value.some((c) => c.classid === form.classid)
    if (!stillValid) form.classid = null
  }
})

const resetForm = () => {
  Object.assign(form, emptyForm)
  formClasses.value = []
}

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
    // 编辑时留空表示不改密码，避免把空串写进数据库
    if (isEdit.value && !payload.password) delete payload.password

    if (isEdit.value) await updateStudent(payload)
    else await createStudent({ ...payload, password: payload.password || '123456' })

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

const TEMPLATE_HEADERS = ['学号', '姓名', '性别', '电话', '邮箱', '地址', '专业名称', '班级名称']

const handleDownloadTemplate = async () => {
  try {
    await downloadTemplate({
      headers: TEMPLATE_HEADERS,
      sample: ['2023001001', '张三', '男', '13800000000', 'zhangsan@example.com', '广东省广州市', '计算机科学与技术', '计科23-1'],
      fileName: '学生导入模板.xlsx',
      sheetName: '学生信息'
    })
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/** 班级名称在全校范围内可能重名，优先在所选专业内匹配 */
const createClassResolver = () => {
  const cache = new Map()

  const allClasses = async () => {
    if (!cache.has('__all__')) {
      const res = await listClasses({ page: 1, pageSize: 10000 }).catch(() => null)
      cache.set('__all__', res?.data || [])
    }
    return cache.get('__all__')
  }

  return async (majorid, className) => {
    if (majorid) {
      const key = `${majorid}::${className}`
      if (!cache.has(key)) {
        const list = await fetchClasses(majorid)
        cache.set(key, list.find((c) => c.classname === className)?.classid || null)
      }
      if (cache.get(key)) return cache.get(key)
    }
    return (await allClasses()).find((c) => c.classname === className)?.classid || null
  }
}

const handleFilePick = async (file) => {
  const raw = file.raw
  if (!raw) return

  importing.value = true
  importResult.value = null

  try {
    const sheetRows = await readSheetRows(raw)
    const resolveClassId = createClassResolver()

    importResult.value = await importRows(sheetRows, async (row) => {
      const studentno = row.学号 || row.studentno
      const studentname = row.姓名 || row.studentname
      const className = row.班级名称 || row.classname

      if (!studentno || !studentname) throw new Error('缺少学号或姓名')
      if (!className) throw new Error('缺少班级名称')

      const majorName = row.专业名称 || row.majorname
      const majorid = majors.value.find((m) => m.majorname === majorName)?.majorid || null
      const classid = await resolveClassId(majorid, className)
      if (!classid) throw new Error(`找不到班级「${className}」`)

      await createStudent({
        studentno,
        studentname,
        password: '123456',
        sex: row.性别 || row.sex || '男',
        phone: row.电话 || row.phone || '',
        email: row.邮箱 || row.email || '',
        address: row.地址 || row.address || '',
        majorid,
        classid
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

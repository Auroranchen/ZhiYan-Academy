<template>
  <PageLayout
    title="班级管理"
    description="维护教学班级及其所属专业，学生账号需归属到具体班级。"
    :breadcrumb="[{ label: '基础数据' }, { label: '班级管理' }]"
  >
    <template #actions>
      <el-button type="primary" @click="openCreate">新增班级</el-button>
    </template>

    <DataTable
      v-model:page="query.page"
      v-model:page-size="query.pageSize"
      :columns="columns"
      :data="rows"
      :loading="loading"
      :total="total"
      row-key="classid"
      empty-text="还没有班级"
      empty-hint="先在专业管理中建立专业，再新增班级"
      actions-width="140"
      @change="load"
    >
      <template #major="{ row }">
        <span v-if="row.majorname">{{ row.majorname }}</span>
        <el-tag v-else type="info" size="small" disable-transitions>未归属</el-tag>
      </template>

      <template #actions="{ row }">
        <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        <el-button link type="danger" @click="remove(row)">删除</el-button>
      </template>
    </DataTable>

    <FormDialog
      v-model="formVisible"
      :title="isEdit ? '编辑班级' : '新增班级'"
      :model="form"
      :rules="rules"
      :loading="submitting"
      width="480px"
      @submit="handleSubmit"
      @closed="resetForm"
    >
      <el-form-item label="班级名称" prop="classname">
        <el-input v-model="form.classname" placeholder="例如：计科23-1" />
      </el-form-item>
      <el-form-item label="所属专业" prop="majorid">
        <el-select v-model="form.majorid" placeholder="请选择专业">
          <el-option v-for="m in majors" :key="m.majorid" :label="m.majorname" :value="m.majorid" />
        </el-select>
      </el-form-item>
    </FormDialog>
  </PageLayout>
</template>

<script setup>
/** 班级管理 */
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DataTable, FormDialog, PageLayout } from '@/components/ui'
import { useCrudTable } from '@/composables/useCrudTable'
import { createClass, deleteClass, listClasses, updateClass } from '@/api/classes'
import { listAllMajors } from '@/api/major'

const columns = [
  { prop: 'classid', label: '编号', width: 100 },
  { prop: 'classname', label: '班级名称', minWidth: 180 },
  { label: '所属专业', minWidth: 200, slot: 'major' }
]

const rules = {
  classname: [{ required: true, message: '请输入班级名称', trigger: 'blur' }],
  majorid: [{ required: true, message: '请选择所属专业', trigger: 'change' }]
}

const { rows, total, loading, query, load, remove } = useCrudTable({
  fetch: listClasses,
  remove: (row) => deleteClass(row.classid),
  confirmText: (row) => `确定要删除班级「${row.classname}」吗？该班级下的学生将失去班级归属。`
})

const majors = ref([])
listAllMajors()
  .then((res) => { majors.value = res?.data || [] })
  .catch(() => { majors.value = [] })

const formVisible = ref(false)
const submitting = ref(false)
const isEdit = ref(false)

const emptyForm = { classid: null, classname: '', majorid: null }
const form = reactive({ ...emptyForm })

const resetForm = () => Object.assign(form, emptyForm)

const openCreate = () => {
  resetForm()
  isEdit.value = false
  formVisible.value = true
}

const openEdit = (row) => {
  Object.assign(form, emptyForm, row)
  isEdit.value = true
  formVisible.value = true
}

const handleSubmit = async () => {
  submitting.value = true
  try {
    if (isEdit.value) await updateClass(form)
    else await createClass(form)
    ElMessage.success(isEdit.value ? '已保存' : '已新增')
    formVisible.value = false
    await load()
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    submitting.value = false
  }
}
</script>

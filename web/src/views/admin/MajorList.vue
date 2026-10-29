<template>
  <PageLayout
    title="专业管理"
    description="维护学校开设的专业，专业下可继续划分班级。"
    :breadcrumb="[{ label: '基础数据' }, { label: '专业管理' }]"
  >
    <template #actions>
      <el-button type="primary" @click="openCreate">新增专业</el-button>
    </template>

    <DataTable
      v-model:page="query.page"
      v-model:page-size="query.pageSize"
      :columns="columns"
      :data="rows"
      :loading="loading"
      :total="total"
      row-key="majorid"
      empty-text="还没有专业"
      empty-hint="新增专业后即可在班级与课程中引用"
      actions-width="140"
      @change="load"
    >
      <template #actions="{ row }">
        <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        <el-button link type="danger" @click="remove(row)">删除</el-button>
      </template>
    </DataTable>

    <FormDialog
      v-model="formVisible"
      :title="isEdit ? '编辑专业' : '新增专业'"
      :model="form"
      :rules="rules"
      :loading="submitting"
      width="480px"
      @submit="handleSubmit"
      @closed="resetForm"
    >
      <el-form-item label="专业名称" prop="majorname">
        <el-input v-model="form.majorname" placeholder="例如：计算机科学与技术" />
      </el-form-item>
    </FormDialog>
  </PageLayout>
</template>

<script setup>
/** 专业管理 */
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DataTable, FormDialog, PageLayout } from '@/components/ui'
import { useCrudTable } from '@/composables/useCrudTable'
import { createMajor, deleteMajor, listMajors, updateMajor } from '@/api/major'

const columns = [
  { prop: 'majorid', label: '编号', width: 100 },
  { prop: 'majorname', label: '专业名称', minWidth: 240 }
]

const rules = {
  majorname: [{ required: true, message: '请输入专业名称', trigger: 'blur' }]
}

const { rows, total, loading, query, load, remove } = useCrudTable({
  fetch: listMajors,
  remove: (row) => deleteMajor(row.majorid),
  confirmText: (row) => `确定要删除专业「${row.majorname}」吗？其下的班级将失去专业归属。`
})

const formVisible = ref(false)
const submitting = ref(false)
const isEdit = ref(false)

const emptyForm = { majorid: null, majorname: '' }
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
    if (isEdit.value) await updateMajor(form)
    else await createMajor(form)
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

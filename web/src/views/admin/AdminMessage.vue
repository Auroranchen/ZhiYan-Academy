<template>
  <PageLayout
    title="消息中心"
    description="向学生、教师或整个班级发送站内消息。"
    :breadcrumb="[{ label: '系统管理' }, { label: '消息中心' }]"
  >
    <template #actions>
      <el-button type="primary" @click="openSend">发送消息</el-button>
    </template>

    <DataTable
      v-model:page="query.page"
      v-model:page-size="query.pageSize"
      :columns="columns"
      :data="rows"
      :loading="loading"
      :total="total"
      row-key="id"
      empty-text="还没有发送过消息"
      empty-hint="发送后可在这里查看历史记录"
      actions-width="150"
      @change="load"
    >
      <template #target="{ row }">
        <el-tag size="small" disable-transitions>{{ TARGET_TEXT[row.receivertype] || '未知' }}</el-tag>
      </template>

      <template #time="{ row }">{{ formatDate(row.createtime, 'minute') }}</template>

      <template #actions="{ row }">
        <el-button link type="primary" @click="openView(row)">查看</el-button>
        <el-button link type="danger" @click="remove(row)">删除</el-button>
      </template>
    </DataTable>

    <FormDialog
      v-model="sendVisible"
      title="发送消息"
      :model="form"
      :rules="rules"
      :loading="sending"
      width="600px"
      confirm-text="发送"
      @submit="submitSend"
      @closed="resetForm"
    >
      <el-form-item label="发送给" prop="targetType">
        <el-radio-group v-model="form.targetType" @change="onTargetTypeChange">
          <el-radio :value="1">学生</el-radio>
          <el-radio :value="2">教师</el-radio>
          <el-radio :value="3">班级</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item :label="TARGET_TEXT[form.targetType]" prop="receiverid">
        <el-select v-model="form.receiverid" filterable :placeholder="`请选择${TARGET_TEXT[form.targetType]}`">
          <el-option
            v-for="opt in targetOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" placeholder="请输入消息标题" maxlength="60" show-word-limit />
      </el-form-item>
      <el-form-item label="内容" prop="content">
        <el-input v-model="form.content" type="textarea" :rows="5" placeholder="请输入消息内容" />
      </el-form-item>
    </FormDialog>

    <el-dialog v-model="viewVisible" title="消息详情" width="600px">
      <article class="message">
        <h3 class="message__title">{{ viewRow.title }}</h3>
        <p class="message__meta">
          接收者：{{ viewRow.receivername || '—' }} · {{ formatDate(viewRow.createtime, 'minute') }}
        </p>
        <div class="message__body">{{ viewRow.content }}</div>
      </article>
    </el-dialog>
  </PageLayout>
</template>

<script setup>
/** 管理端消息中心 */
import { computed, reactive, ref, watch } from 'vue'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { DataTable, FormDialog, PageLayout } from '@/components/ui'
import { useCrudTable } from '@/composables/useCrudTable'
import { deleteMessage, listSentMessages, sendMessage, sendMessageToClass } from '@/api/message'
import { listStudents } from '@/api/student'
import { listTeachers } from '@/api/teacher'
import { listClasses } from '@/api/classes'
import { formatDate } from '@/utils/format'

const TARGET_TEXT = { 1: '学生', 2: '教师', 3: '班级' }
const SENDER_TYPE_ADMIN = 1

const store = useStore()
const adminid = store.state.user?.adminid

const columns = [
  { prop: 'title', label: '标题', minWidth: 220 },
  { prop: 'receivername', label: '接收者', width: 180 },
  { label: '接收类型', width: 110, slot: 'target' },
  { label: '发送时间', width: 170, slot: 'time' }
]

const { rows, total, loading, query, load, remove } = useCrudTable({
  fetch: (params) => listSentMessages({ ...params, sendertype: SENDER_TYPE_ADMIN, senderid: adminid }),
  remove: (row) => deleteMessage(row.id),
  confirmText: (row) => `确定要删除消息「${row.title}」吗？`
})

/* ---------- 发送 ---------- */

const sendVisible = ref(false)
const sending = ref(false)

const emptyForm = { targetType: 1, receiverid: null, title: '', content: '' }
const form = reactive({ ...emptyForm })

const rules = {
  receiverid: [{ required: true, message: '请选择接收对象', trigger: 'change' }],
  title: [{ required: true, message: '请输入消息标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入消息内容', trigger: 'blur' }]
}

const students = ref([])
const teachers = ref([])
const classes = ref([])

const targetOptions = computed(() => {
  if (form.targetType === 1) {
    return students.value.map((s) => ({ value: s.studentid, label: `${s.studentname}（${s.studentno}）` }))
  }
  if (form.targetType === 2) {
    return teachers.value.map((t) => ({ value: t.teacherid, label: `${t.teachername}（${t.teacherno}）` }))
  }
  return classes.value.map((c) => ({ value: c.classid, label: c.classname }))
})

/** 候选名单按需拉取一次即可，切回来时复用已有数据 */
const loadTargets = async (type) => {
  if (type === 1 && !students.value.length) {
    const res = await listStudents({ page: 1, pageSize: 500 }).catch(() => null)
    students.value = res?.data || []
  } else if (type === 2 && !teachers.value.length) {
    const res = await listTeachers({ page: 1, pageSize: 500 }).catch(() => null)
    teachers.value = res?.data || []
  } else if (type === 3 && !classes.value.length) {
    const res = await listClasses({ page: 1, pageSize: 200 }).catch(() => null)
    classes.value = res?.data || []
  }
}

const onTargetTypeChange = (type) => {
  form.receiverid = null
  loadTargets(type)
}

watch(sendVisible, (open) => {
  if (open) loadTargets(form.targetType)
})

const resetForm = () => Object.assign(form, emptyForm)

const openSend = () => {
  resetForm()
  sendVisible.value = true
}

const submitSend = async () => {
  sending.value = true
  try {
    const base = {
      title: form.title,
      content: form.content,
      sendertype: SENDER_TYPE_ADMIN,
      senderid: adminid
    }

    if (form.targetType === 3) {
      await sendMessageToClass({ ...base, classid: form.receiverid })
    } else {
      await sendMessage({ ...base, receivertype: form.targetType, receiverid: form.receiverid })
    }

    ElMessage.success('已发送')
    sendVisible.value = false
    await load()
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    sending.value = false
  }
}

/* ---------- 查看 ---------- */

const viewVisible = ref(false)
const viewRow = ref({})

const openView = (row) => {
  viewRow.value = row
  viewVisible.value = true
}
</script>

<style scoped>
.message__title {
  margin: 0 0 var(--zy-space-2);
  font-size: var(--zy-font-size-lg);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.message__meta {
  margin: 0 0 var(--zy-space-4);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-tertiary);
}

.message__body {
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-secondary);
  white-space: pre-wrap;
}
</style>

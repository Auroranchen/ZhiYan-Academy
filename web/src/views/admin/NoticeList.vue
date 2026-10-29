<template>
  <PageLayout
    title="通知公告"
    description="面向全体师生发布公告，并跟踪各角色的阅读情况。"
    :breadcrumb="[{ label: '系统管理' }, { label: '通知公告' }]"
  >
    <template #actions>
      <el-button type="primary" @click="openCreate">发布公告</el-button>
    </template>

    <DataTable
      v-model:page="query.page"
      v-model:page-size="query.pageSize"
      :columns="columns"
      :data="rows"
      :loading="loading"
      :total="total"
      row-key="noticeid"
      empty-text="还没有公告"
      empty-hint="发布后师生端会同步收到"
      actions-width="230"
      @change="load"
    >
      <template #time="{ row }">
        {{ formatDate(row.createtime, 'minute') }}
      </template>

      <template #actions="{ row }">
        <el-button link @click="openView(row)">查看</el-button>
        <el-button link @click="openStats(row)">阅读统计</el-button>
        <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        <el-button link type="danger" @click="remove(row)">删除</el-button>
      </template>
    </DataTable>

    <!-- 查看 -->
    <el-dialog v-model="viewVisible" title="公告详情" width="720px">
      <article class="notice">
        <h3 class="notice__title">{{ viewRow.title }}</h3>
        <p class="notice__time">发布于 {{ formatDate(viewRow.createtime, 'minute') }}</p>
        <div class="notice__body" v-safe-html="viewRow.content"></div>
      </article>
    </el-dialog>

    <!-- 阅读统计 -->
    <el-dialog v-model="statsVisible" title="阅读统计" width="720px" @closed="resetStats">
      <div v-loading="statsLoading">
        <p class="stats__subject">{{ statsTitle }}</p>
        <div class="stats__summary">
          <StatTile label="学生已读" :value="stats.studentReadCount" tone="success" />
          <StatTile label="教师已读" :value="stats.teacherReadCount" tone="success" />
          <StatTile label="学生未读" :value="unreadList.length" :tone="unreadList.length ? 'warning' : 'default'" />
        </div>
        <DataTable
          :columns="unreadColumns"
          :data="unreadList"
          :show-pagination="false"
          row-key="studentid"
          max-height="320"
          empty-text="全部学生均已阅读"
        />
      </div>
    </el-dialog>

    <!-- 新增与编辑 -->
    <el-dialog
      v-model="formVisible"
      :title="isEdit ? '编辑公告' : '发布公告'"
      width="840px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form :model="form" label-width="72px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="请输入公告标题" maxlength="80" show-word-limit />
        </el-form-item>
        <el-form-item label="内容" required>
          <div class="editor">
            <Toolbar class="editor__toolbar" :editor="editorRef" :default-config="{}" mode="default" />
            <Editor
              v-model="form.content"
              class="editor__body"
              :default-config="editorConfig"
              mode="default"
              @on-created="(editor) => (editorRef = editor)"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ isEdit ? '保存' : '发布' }}
        </el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>

<script setup>
/** 通知公告管理 */
import { onBeforeUnmount, reactive, ref, shallowRef } from 'vue'
import { ElMessage } from 'element-plus'
import '@wangeditor/editor/dist/css/style.css'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import { DataTable, PageLayout, StatTile } from '@/components/ui'
import { useCrudTable } from '@/composables/useCrudTable'
import {
  createNotice,
  deleteNotice,
  getNoticeReadStats,
  getNoticeUnreadList,
  listNotices,
  updateNotice
} from '@/api/notice'
import { formatDate } from '@/utils/format'
import { sanitizeHtml } from '@/utils/safeHtml'

const columns = [
  { prop: 'title', label: '标题', minWidth: 260 },
  { label: '发布时间', width: 170, slot: 'time' }
]

const unreadColumns = [
  { prop: 'studentid', label: '学号', width: 160 },
  { prop: 'studentname', label: '学生姓名', minWidth: 140 }
]

const { rows, total, loading, query, load, remove } = useCrudTable({
  fetch: listNotices,
  remove: (row) => deleteNotice(row.noticeid),
  confirmText: (row) => `确定要删除公告「${row.title}」吗？删除后师生端将不再可见。`
})

/* ---------- 查看 ---------- */

const viewVisible = ref(false)
const viewRow = ref({})

const openView = (row) => {
  viewRow.value = row
  viewVisible.value = true
}

/* ---------- 阅读统计 ---------- */

const statsVisible = ref(false)
const statsLoading = ref(false)
const statsTitle = ref('')
const stats = reactive({ studentReadCount: 0, teacherReadCount: 0 })
const unreadList = ref([])

const openStats = async (row) => {
  statsTitle.value = row.title || ''
  statsVisible.value = true
  statsLoading.value = true

  const [statsRes, unreadRes] = await Promise.all([
    getNoticeReadStats(row.noticeid).catch(() => null),
    getNoticeUnreadList(row.noticeid).catch(() => null)
  ])

  stats.studentReadCount = Number(statsRes?.data?.studentReadCount || 0)
  stats.teacherReadCount = Number(statsRes?.data?.teacherReadCount || 0)
  unreadList.value = unreadRes?.data || []
  statsLoading.value = false
}

const resetStats = () => {
  stats.studentReadCount = 0
  stats.teacherReadCount = 0
  unreadList.value = []
  statsTitle.value = ''
}

/* ---------- 富文本编辑 ---------- */

const editorRef = shallowRef()
const editorConfig = { placeholder: '请输入公告内容...' }

onBeforeUnmount(() => editorRef.value?.destroy())

const formVisible = ref(false)
const submitting = ref(false)
const isEdit = ref(false)

const emptyForm = { noticeid: null, title: '', content: '' }
const form = reactive({ ...emptyForm })

const resetForm = () => Object.assign(form, emptyForm)

const openCreate = () => {
  resetForm()
  isEdit.value = false
  formVisible.value = true
}

const openEdit = (row) => {
  Object.assign(form, emptyForm, row, { content: sanitizeHtml(row.content) })
  isEdit.value = true
  formVisible.value = true
}

/** 富文本内容不走 el-form 校验，空编辑器会留下一个空段落标签 */
const handleSubmit = async () => {
  const content = sanitizeHtml(form.content || '')
  if (!form.title.trim()) {
    ElMessage.warning('请输入公告标题')
    return
  }
  if (!content.trim() || content === '<p><br></p>') {
    ElMessage.warning('请输入公告内容')
    return
  }

  form.content = content
  submitting.value = true
  try {
    if (isEdit.value) await updateNotice(form)
    else await createNotice(form)
    ElMessage.success(isEdit.value ? '已保存' : '已发布')
    formVisible.value = false
    await load()
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.notice__title {
  margin: 0 0 var(--zy-space-2);
  font-size: var(--zy-font-size-xl);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.notice__time {
  margin: 0 0 var(--zy-space-5);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-tertiary);
}

.notice__body {
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-secondary);
}

.stats__subject {
  margin: 0 0 var(--zy-space-4);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.stats__summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--zy-space-3);
  margin-bottom: var(--zy-space-4);
}

.editor {
  width: 100%;
  border: 1px solid var(--zy-border-default);
  border-radius: var(--zy-radius-control);
  overflow: hidden;
}

.editor__toolbar {
  border-bottom: 1px solid var(--zy-border-subtle);
}

.editor__body {
  height: 320px;
  overflow-y: hidden;
}
</style>

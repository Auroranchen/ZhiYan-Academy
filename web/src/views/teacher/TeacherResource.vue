<template>
  <PageLayout
    title="课程资源"
    description="上传与维护课程讲义、代码和参考资料，学生端可直接下载。"
    :breadcrumb="[{ label: '教学管理' }, { label: '课程资源' }]"
  >
    <template #actions>
      <el-button type="primary" @click="openUpload">上传资源</el-button>
    </template>

    <FilterBar :loading="loading" @search="search" @reset="reset">
      <el-select v-model="query.semester" placeholder="全部学期" clearable @change="onSemesterChange">
        <el-option v-for="s in SEMESTERS" :key="s" :label="s" :value="s" />
      </el-select>
      <el-select v-model="query.courseid" placeholder="全部课程" clearable>
        <el-option v-for="c in semesterCourses" :key="c.courseid" :label="c.coursename" :value="c.courseid" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="资源标题" clearable />
    </FilterBar>

    <div v-if="selection.length" class="batch">
      <span>已选 {{ selection.length }} 项</span>
      <el-button type="danger" link @click="batchDelete">批量删除</el-button>
    </div>

    <DataTable
      v-model:page="query.page"
      v-model:page-size="query.pageSize"
      :columns="columns"
      :data="rows"
      :loading="loading"
      :total="total"
      row-key="id"
      selectable
      empty-text="没有匹配的资源"
      empty-hint="上传后学生即可在课程资源中看到"
      actions-width="230"
      @update:selection="selection = $event"
      @change="load"
    >
      <template #type="{ row }">
        <el-tag size="small" disable-transitions>{{ row.filetype || '其他' }}</el-tag>
      </template>

      <template #size="{ row }">{{ formatFileSize(row.filesize) }}</template>

      <template #time="{ row }">{{ formatDate(row.createtime, 'minute') }}</template>

      <template #actions="{ row }">
        <el-button link @click="openDetail(row)">详情</el-button>
        <el-button link type="primary" @click="download(row)">下载</el-button>
        <template v-if="isMine(row)">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </template>
    </DataTable>

    <!-- 上传 -->
    <el-dialog v-model="uploadVisible" title="上传资源" width="560px" @closed="resetUpload">
      <el-form :model="uploadForm" label-width="72px">
        <el-form-item label="课程" required>
          <el-select v-model="uploadForm.courseid" placeholder="请选择课程" filterable>
            <el-option v-for="c in allCourses" :key="c.courseid" :label="c.coursename" :value="c.courseid" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="uploadForm.title" placeholder="请输入资源标题" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="uploadForm.filetype">
            <el-option v-for="t in FILE_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="uploadForm.description" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>
        <el-form-item label="文件" required>
          <el-upload ref="uploadRef" :auto-upload="false" :limit="1" :on-change="(f) => (uploadFile = f.raw)">
            <el-button>选择文件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">上传</el-button>
      </template>
    </el-dialog>

    <!-- 编辑 -->
    <el-dialog v-model="editVisible" title="编辑资源" width="560px" @closed="resetEdit">
      <el-form :model="editForm" label-width="72px">
        <el-form-item label="标题" required>
          <el-input v-model="editForm.title" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="editForm.filetype">
            <el-option v-for="t in FILE_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="editForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="文件">
          <el-upload
            ref="editUploadRef"
            :auto-upload="false"
            :limit="1"
            :on-change="(f) => (editFile = f.raw)"
            :on-remove="() => (editFile = null)"
          >
            <el-button>更换文件（可选）</el-button>
            <template #tip>
              <p class="tip">
                {{ editFile ? `新文件：${editFile.name}` : `当前文件：${editForm.currentFilename || '—'}` }}
              </p>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editing" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <DetailDialog v-model="detailVisible" title="资源详情" :fields="detailFields" :data="detailRow">
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" @click="download(detailRow)">下载</el-button>
      </template>
    </DetailDialog>
  </PageLayout>
</template>

<script setup>
/** 课程资源管理（教师端） */
import { computed, reactive, ref, watch } from 'vue'
import { useStore } from 'vuex'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DataTable, DetailDialog, FilterBar, PageLayout } from '@/components/ui'
import { useCrudTable } from '@/composables/useCrudTable'
import {
  deleteResource,
  listResourcesByTeacher,
  resourceDownloadUrl,
  updateResource,
  uploadResource
} from '@/api/courseResource'
import { listCoursesByTeacher } from '@/api/course'
import { getCurrentSemester } from '@/utils/semester'
import { formatDate, formatFileSize } from '@/utils/format'

const SEMESTERS = [
  '2026-2027-2', '2026-2027-1',
  '2025-2026-2', '2025-2026-1',
  '2024-2025-2', '2024-2025-1',
  '2023-2024-2', '2023-2024-1'
]

const FILE_TYPES = [
  { value: 'PPT', label: 'PPT' },
  { value: 'PDF', label: 'PDF' },
  { value: 'CODE', label: '代码' },
  { value: 'DOC', label: '文档' },
  { value: 'OTHER', label: '其他' }
]

const columns = [
  { prop: 'title', label: '标题', minWidth: 180 },
  { prop: 'coursename', label: '课程', minWidth: 140 },
  { label: '类型', width: 100, align: 'center', slot: 'type' },
  { label: '大小', width: 100, align: 'right', slot: 'size' },
  { prop: 'downloadcount', label: '下载量', width: 90, align: 'right' },
  { label: '上传时间', width: 170, slot: 'time' }
]

const detailFields = [
  { label: '标题', prop: 'title' },
  { label: '课程', prop: 'coursename' },
  { label: '文件类型', prop: 'filetype' },
  { label: '文件名', prop: 'filename' },
  { label: '文件大小', prop: 'filesize', formatter: (v) => formatFileSize(v) },
  { label: '下载次数', prop: 'downloadcount', formatter: (v) => v || 0 },
  { label: '上传时间', prop: 'createtime', formatter: (v) => formatDate(v, 'minute') },
  { label: '说明', prop: 'description' }
]

const store = useStore()
const teacherid = store.state.user?.teacherid

const { rows, total, loading, selection, query, load, search, reset, remove } = useCrudTable({
  fetch: (params) => listResourcesByTeacher(teacherid, params),
  remove: (row) => deleteResource(row.id),
  initialQuery: { keyword: '', semester: getCurrentSemester(), courseid: null },
  confirmText: (row) => `确定要删除资源「${row.title}」吗？学生将无法再下载。`
})

/* ---------- 课程选项 ---------- */

const allCourses = ref([])

listCoursesByTeacher(teacherid)
  .then((res) => { allCourses.value = res?.data || [] })
  .catch(() => { allCourses.value = [] })

const semesterCourses = computed(() => {
  if (!query.semester) return allCourses.value
  return allCourses.value.filter((c) => c.semester === query.semester)
})

// 切学期后原课程可能不在新学期里，清掉避免筛出空列表
watch(() => query.semester, () => { query.courseid = null })

const onSemesterChange = () => search()

/** 资源列表含其他教师的公开资源，只有自己课程下的才可改可删 */
const isMine = (row) => allCourses.value.some((c) => c.courseid === row.courseid)

/* ---------- 上传 ---------- */

const uploadVisible = ref(false)
const uploading = ref(false)
const uploadRef = ref(null)
const uploadFile = ref(null)
const uploadForm = reactive({ courseid: null, title: '', filetype: 'OTHER', description: '' })

const resetUpload = () => {
  Object.assign(uploadForm, { courseid: null, title: '', filetype: 'OTHER', description: '' })
  uploadFile.value = null
  uploadRef.value?.clearFiles()
}

const openUpload = () => {
  resetUpload()
  uploadVisible.value = true
}

const buildFormData = (form, file) => {
  const data = new FormData()
  data.append('courseid', form.courseid)
  data.append('teacherid', teacherid)
  data.append('title', form.title)
  data.append('filetype', form.filetype)
  data.append('description', form.description || '')
  data.append('file', file)
  return data
}

const submitUpload = async () => {
  if (!uploadForm.courseid || !uploadForm.title.trim() || !uploadFile.value) {
    ElMessage.warning('请选择课程、填写标题并选择文件')
    return
  }

  uploading.value = true
  try {
    await uploadResource(buildFormData(uploadForm, uploadFile.value))
    ElMessage.success('已上传')
    uploadVisible.value = false
    await load()
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    uploading.value = false
  }
}

/* ---------- 编辑 ---------- */

const editVisible = ref(false)
const editing = ref(false)
const editUploadRef = ref(null)
const editFile = ref(null)
const editForm = reactive({
  id: null,
  courseid: null,
  title: '',
  filetype: 'OTHER',
  description: '',
  currentFilename: ''
})

const resetEdit = () => {
  Object.assign(editForm, {
    id: null,
    courseid: null,
    title: '',
    filetype: 'OTHER',
    description: '',
    currentFilename: ''
  })
  editFile.value = null
  editUploadRef.value?.clearFiles()
}

const openEdit = (row) => {
  resetEdit()
  Object.assign(editForm, {
    id: row.id,
    courseid: row.courseid,
    title: row.title,
    filetype: row.filetype || 'OTHER',
    description: row.description || '',
    currentFilename: row.filename
  })
  editVisible.value = true
}

const saveEdit = async () => {
  if (!editForm.title.trim()) {
    ElMessage.warning('标题不能为空')
    return
  }

  editing.value = true
  try {
    if (!editFile.value) {
      await updateResource({
        id: editForm.id,
        title: editForm.title,
        filetype: editForm.filetype,
        description: editForm.description
      })
    } else {
      // 后端没有「替换文件」接口，只能重传再删旧记录。
      // 必须先传后删：原实现是先删后传，一旦上传失败资源就永久丢了。
      await uploadResource(buildFormData(editForm, editFile.value))
      await deleteResource(editForm.id).catch(() => {
        ElMessage.warning('新文件已上传，但旧版本未能删除，请手动清理')
      })
    }

    ElMessage.success('已保存')
    editVisible.value = false
    await load()
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    editing.value = false
  }
}

/* ---------- 详情、下载与批量删除 ---------- */

const detailVisible = ref(false)
const detailRow = ref({})

const openDetail = (row) => {
  detailRow.value = row
  detailVisible.value = true
}

const download = (row) => {
  if (!row?.id) return
  window.open(resourceDownloadUrl(row.id), '_blank')
}

const batchDelete = async () => {
  const mine = selection.value.filter(isMine)
  if (!mine.length) {
    ElMessage.warning('选中项里没有你可以删除的资源')
    return
  }

  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${mine.length} 项资源吗？`, '批量删除', { type: 'warning' })
  } catch {
    return
  }

  const results = await Promise.allSettled(mine.map((row) => deleteResource(row.id)))
  const failed = results.filter((r) => r.status === 'rejected').length

  if (failed) ElMessage.warning(`${mine.length - failed} 项已删除，${failed} 项失败`)
  else ElMessage.success(`已删除 ${mine.length} 项`)

  await load()
}
</script>

<style scoped>
.batch {
  display: flex;
  align-items: center;
  gap: var(--zy-space-3);
  padding: var(--zy-space-2) var(--zy-space-4);
  background: var(--zy-brand-soft);
  border: 1px solid var(--zy-brand-border);
  border-radius: var(--zy-radius-md);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.tip {
  margin: var(--zy-space-1) 0 0;
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}
</style>

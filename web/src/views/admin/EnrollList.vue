<template>
  <PageLayout
    title="入学申请"
    description="处理官网提交的学校与机构接入申请。"
    :breadcrumb="[{ label: '系统监管' }, { label: '入学申请' }]"
  >
    <template #actions>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </template>

    <template #summary>
      <StatTile label="待审" :value="counts.pending" :tone="counts.pending ? 'warning' : 'default'" />
      <StatTile label="已通过" :value="counts.approved" tone="success" />
      <StatTile label="已驳回" :value="counts.rejected" />
    </template>

    <FilterBar :loading="loading" @search="load" @reset="resetFilters">
      <el-select v-model="statusFilter" placeholder="全部状态" clearable>
        <el-option v-for="s in STATUSES" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
    </FilterBar>

    <DataTable
      :columns="columns"
      :data="visibleRows"
      :loading="loading"
      :show-pagination="false"
      row-key="id"
      empty-text="暂无入学申请"
      empty-hint="官网提交的接入申请会出现在这里"
      actions-width="210"
    >
      <template #attachment="{ row }">
        <el-button v-if="row.attachment" link type="primary" @click="downloadAttachment(row)">
          {{ row.attachmentName || '下载' }}
        </el-button>
        <span v-else class="muted">无</span>
      </template>

      <template #status="{ row }">
        <el-tag :type="statusTone(row.status)" size="small" disable-transitions>
          {{ statusLabel(row.status) }}
        </el-tag>
      </template>

      <template #time="{ row }">
        {{ formatDate(row.createTime, 'minute') }}
      </template>

      <template #actions="{ row }">
        <el-button
          v-if="Number(row.status) !== 1"
          link
          type="success"
          :loading="updatingId === row.id"
          @click="setStatus(row, 1)"
        >
          通过
        </el-button>
        <el-button
          v-if="Number(row.status) !== 2"
          link
          type="danger"
          :loading="updatingId === row.id"
          @click="setStatus(row, 2)"
        >
          驳回
        </el-button>
        <el-button
          v-if="Number(row.status) !== 0"
          link
          :loading="updatingId === row.id"
          @click="setStatus(row, 0)"
        >
          转待审
        </el-button>
      </template>
    </DataTable>
  </PageLayout>
</template>

<script setup>
/** 入学申请审核 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DataTable, FilterBar, PageLayout, StatTile } from '@/components/ui'
import { listEnrollApplications, updateEnrollStatus } from '@/api/system'
import { formatDate } from '@/utils/format'

const STATUSES = [
  { value: 0, label: '待审' },
  { value: 1, label: '已通过' },
  { value: 2, label: '已驳回' }
]

const columns = [
  { prop: 'school', label: '学校 / 机构', minWidth: 180 },
  { prop: 'contactName', label: '联系人', width: 100 },
  { prop: 'contactRole', label: '职务', width: 120 },
  { prop: 'phone', label: '手机号', width: 130 },
  { prop: 'remark', label: '需求说明', minWidth: 200 },
  { label: '附件', width: 130, slot: 'attachment' },
  { label: '状态', width: 100, slot: 'status' },
  { label: '提交时间', width: 170, slot: 'time' }
]

const rows = ref([])
const loading = ref(false)
const updatingId = ref(null)
const statusFilter = ref(null)

const statusLabel = (status) =>
  STATUSES.find((s) => s.value === Number(status))?.label || '待审'

const statusTone = (status) => {
  const value = Number(status)
  if (value === 1) return 'success'
  if (value === 2) return 'danger'
  return 'warning'
}

// 接口一次性返回全部申请，筛选与计数都在前端完成
const visibleRows = computed(() => {
  if (statusFilter.value === null || statusFilter.value === '') return rows.value
  return rows.value.filter((row) => Number(row.status) === Number(statusFilter.value))
})

const counts = computed(() => ({
  pending: rows.value.filter((r) => Number(r.status) === 0).length,
  approved: rows.value.filter((r) => Number(r.status) === 1).length,
  rejected: rows.value.filter((r) => Number(r.status) === 2).length
}))

const load = async () => {
  loading.value = true
  try {
    const res = await listEnrollApplications().catch(() => null)
    rows.value = res?.data || []
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  statusFilter.value = null
  return load()
}

const setStatus = async (row, status) => {
  try {
    await ElMessageBox.confirm(
      `确认将「${row.school}」标记为「${statusLabel(status)}」？`,
      '更新状态',
      { type: 'warning' }
    )
  } catch {
    return
  }

  updatingId.value = row.id
  try {
    await updateEnrollStatus(row.id, status)
    row.status = status
    ElMessage.success('状态已更新')
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    updatingId.value = null
  }
}

const downloadAttachment = (row) => {
  window.open(`/ZhiYanAcademy/enroll/attachment/${row.id}`, '_blank')
}

onMounted(load)
</script>

<style scoped>
.muted {
  color: var(--zy-text-tertiary);
}
</style>

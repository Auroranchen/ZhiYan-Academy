<!--
  作业阅读统计

  从 TeacherHomework 拆出：谁读了、谁没读只和这一个弹窗有关，
  数据也只在打开时才需要，放在主页面会让它多背两份状态。
-->
<template>
  <el-dialog
    :model-value="modelValue"
    title="作业阅读统计"
    width="750px"
    @update:model-value="$emit('update:modelValue', $event)"
    @closed="reset"
  >
    <div v-loading="loading">
      <p class="title">{{ homework.title || '作业阅读统计' }}</p>

      <div class="summary">
        <StatTile label="已读人数" :value="stats.readCount" tone="success" />
        <StatTile label="未读人数" :value="stats.unreadCount" :tone="stats.unreadCount ? 'warning' : 'default'" />
      </div>

      <DataTable
        :columns="columns"
        :data="rows"
        :show-pagination="false"
        max-height="360"
        row-key="studentid"
        empty-text="暂无学生数据"
      >
        <template #status="{ row }">
          <el-tag :type="row.isRead ? 'success' : 'danger'" size="small" disable-transitions>
            {{ row.isRead ? '已读' : '未读' }}
          </el-tag>
        </template>
        <template #time="{ row }">{{ formatDate(row.readtime, 'minute') }}</template>
      </DataTable>
    </div>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { DataTable, StatTile } from '@/components/ui'
import { getHomeworkReadStats, listHomeworkReadStatus } from '@/api/homework'
import { formatDate } from '@/utils/format'

const columns = [
  { prop: 'studentid', label: '学号', width: 120 },
  { prop: 'studentname', label: '学生姓名', width: 120 },
  { prop: 'classname', label: '班级', width: 120 },
  { label: '阅读状态', slot: 'status', width: 100 },
  { label: '阅读时间', slot: 'time', minWidth: 160 }
]

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 当前查看的作业行，需含 homeworkid 与 title */
  homework: { type: Object, default: () => ({}) }
})

defineEmits(['update:modelValue'])

const loading = ref(false)
const rows = ref([])
const stats = reactive({ readCount: 0, unreadCount: 0 })

const reset = () => {
  rows.value = []
  stats.readCount = 0
  stats.unreadCount = 0
}

const load = async (homeworkid) => {
  loading.value = true
  try {
    const [statsRes, statusRes] = await Promise.all([
      getHomeworkReadStats(homeworkid),
      listHomeworkReadStatus(homeworkid)
    ])
    stats.readCount = Number(statsRes.data?.readCount || 0)
    stats.unreadCount = Number(statsRes.data?.unreadCount || 0)
    rows.value = statusRes.data || []
  } catch (e) {
    console.error('加载阅读统计失败:', e)
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible && props.homework?.homeworkid) load(props.homework.homeworkid)
  }
)
</script>

<style scoped>
.title {
  margin: 0 0 var(--zy-space-3);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--zy-space-3);
  margin-bottom: var(--zy-space-4);
}
</style>

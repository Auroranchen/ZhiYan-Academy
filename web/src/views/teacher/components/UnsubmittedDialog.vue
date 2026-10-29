<!--
  未提交名单与一键催交

  从 TeacherHomework 拆出：班级筛选、人数统计、群发提醒是一组自洽的逻辑，
  且只在弹窗打开时才有意义。
-->
<template>
  <el-dialog
    :model-value="modelValue"
    title="未提交统计"
    width="560px"
    @update:model-value="$emit('update:modelValue', $event)"
    @closed="reset"
  >
    <div v-loading="loading">
      <p class="title">{{ homework.title }}</p>

      <div class="summary">
        <StatTile label="未提交" :value="visibleList.length" unit="人" :tone="visibleList.length ? 'danger' : 'success'" />
        <StatTile label="涉及班级" :value="classCount" unit="个" />
      </div>

      <div class="toolbar">
        <el-select v-model="classFilter" placeholder="全部班级" clearable class="toolbar__filter">
          <el-option v-for="name in classOptions" :key="name" :label="name" :value="name" />
        </el-select>
        <el-button
          v-if="visibleList.length"
          type="warning"
          plain
          :loading="reminding"
          @click="remindAll"
        >
          一键提醒
        </el-button>
      </div>

      <DataTable
        :columns="columns"
        :data="visibleList"
        :show-pagination="false"
        show-index
        max-height="340"
        :empty-text="classFilter ? '该班级所有学生均已提交' : '所有学生均已提交'"
      />
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { DataTable, StatTile } from '@/components/ui'
import { listUnsubmitted, remindUnsubmitted } from '@/api/homework'
import { confirmOrAbort } from '@/utils/confirm'

/** 消息里带上作业 ID，学生点提醒可直接跳转到作业 */
const MESSAGE_SENDER_TEACHER = 2

const columns = [
  { prop: 'studentno', label: '学号', minWidth: 130, align: 'center' },
  { prop: 'studentname', label: '姓名', minWidth: 90, align: 'center' },
  { prop: 'classname', label: '班级', minWidth: 140, align: 'center' }
]

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 当前查看的作业行，需含 homeworkid 与 title */
  homework: { type: Object, default: () => ({}) }
})

defineEmits(['update:modelValue'])

const store = useStore()

const loading = ref(false)
const reminding = ref(false)
const allList = ref([])
const classFilter = ref('')

const classOptions = computed(() =>
  Array.from(new Set(allList.value.map((s) => s.classname).filter(Boolean))).sort()
)

const visibleList = computed(() =>
  classFilter.value ? allList.value.filter((s) => s.classname === classFilter.value) : allList.value
)

const classCount = computed(
  () => new Set(visibleList.value.map((s) => s.classname).filter(Boolean)).size
)

const reset = () => {
  allList.value = []
  classFilter.value = ''
}

const load = async (homeworkid) => {
  loading.value = true
  try {
    const res = await listUnsubmitted(homeworkid)
    allList.value = res.data || []
  } catch (e) {
    console.error('加载未提交名单失败:', e)
  } finally {
    loading.value = false
  }
}

const remindAll = async () => {
  const count = visibleList.value.length
  if (!count) return
  if (!(await confirmOrAbort(`确定要向 ${count} 名未提交学生发送提醒消息吗？`, '一键提醒'))) return

  reminding.value = true
  try {
    await remindUnsubmitted({
      homeworkid: props.homework.homeworkid,
      title: '作业提交提醒',
      content: `你有一份作业【${props.homework.title}】尚未提交，请尽快完成提交。\n[homeworkid:${props.homework.homeworkid}]`,
      sendertype: MESSAGE_SENDER_TEACHER,
      senderid: store.state.user?.teacherid
    })
    ElMessage.success('提醒消息已发送')
  } catch (e) {
    console.error('发送提醒失败:', e)
  } finally {
    reminding.value = false
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

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--zy-space-2);
  margin-bottom: var(--zy-space-3);
}

.toolbar__filter {
  width: 180px;
}
</style>

<template>
  <PageLayout
    title="作业互评"
    description="在已提交的学生之间循环分配评阅任务，支持匿名评阅与均分回填。"
    :breadcrumb="[{ label: '教学管理' }, { label: '作业互评' }]"
  >
    <template #actions>
      <el-select
        v-model="homeworkid"
        placeholder="选择一次作业"
        filterable
        class="picker"
        @change="loadTask"
      >
        <el-option v-for="hw in homeworks" :key="hw.homeworkid" :label="hw.title" :value="hw.homeworkid" />
      </el-select>
      <el-button :disabled="!homeworkid" :loading="loading" @click="loadTask">刷新</el-button>
    </template>

    <el-empty v-if="!homeworkid" description="请先选择一次作业" />

    <template v-else>
      <SectionCard title="互评设置">
        <div class="config">
          <div class="config__field">
            <label class="config__label">每份作业评阅人数</label>
            <el-input-number v-model="form.reviewersPer" :min="1" :max="10" controls-position="right" />
          </div>
          <div class="config__field">
            <label class="config__label">互评满分</label>
            <el-input-number v-model="form.maxScore" :min="1" :max="100" :step="5" controls-position="right" />
          </div>
          <div class="config__field">
            <label class="config__label">匿名评阅</label>
            <el-switch v-model="form.anonymous" />
          </div>
          <div class="config__actions">
            <el-button v-if="taskExists" @click="closeTask">关闭互评</el-button>
            <el-button type="primary" :loading="assigning" @click="assign">
              {{ taskExists ? '重新分配' : '开启互评' }}
            </el-button>
          </div>
        </div>
        <el-alert type="info" :closable="false" show-icon class="config__tip">
          系统会在已提交的学生之间循环分配评阅任务。重新分配会清空现有评阅记录，学生每完成一份互评得 3 积分。
        </el-alert>
      </SectionCard>

      <template v-if="taskExists">
        <SectionCard title="评阅进度">
          <div class="progress">
            <div class="progress__stats">
              <StatTile label="任务总数" :value="progress.total || 0" />
              <StatTile label="已完成" :value="progress.done || 0" tone="success" />
              <StatTile label="完成率" :value="donePercent" unit="%" tone="brand" />
            </div>
            <el-progress :percentage="donePercent" :stroke-width="12" />
          </div>
        </SectionCard>

        <SectionCard title="学生互评成绩汇总" :count="summary.length || null">
          <template #action>
            <el-button
              link
              type="primary"
              :loading="applying"
              :disabled="!summary.length"
              @click="applyScores"
            >
              均分回填为成绩
            </el-button>
          </template>

          <DataTable
            :columns="columns"
            :data="summary"
            :loading="loading"
            :show-pagination="false"
            show-index
            row-key="studentno"
            empty-text="暂无互评数据"
            empty-hint="学生完成评阅后成绩会出现在这里"
          >
            <template #avg="{ row }">
              <b class="avg">{{ row.avgScore != null ? row.avgScore : '—' }}</b>
            </template>

            <template #reviewed="{ row }">
              {{ row.reviewedCount || 0 }} / {{ row.totalCount || 0 }}
              <el-tag v-if="isFinished(row)" type="success" size="small" class="tag-gap" disable-transitions>
                完成
              </el-tag>
            </template>
          </DataTable>
        </SectionCard>
      </template>
    </template>
  </PageLayout>
</template>

<script setup>
/** 作业互评（教师端） */
import { computed, onMounted, reactive, ref } from 'vue'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { DataTable, PageLayout, SectionCard, StatTile } from '@/components/ui'
import { confirmOrAbort } from '@/utils/confirm'
import { listHomeworkByTeacher } from '@/api/homework'
import {
  applyPeerScores,
  assignPeerReview,
  closePeerReview,
  getPeerSummary,
  getPeerTask
} from '@/api/peerReview'

const columns = [
  { prop: 'studentno', label: '学号', width: 150 },
  { prop: 'studentname', label: '姓名', width: 120 },
  {
    label: '同伴均分',
    width: 140,
    align: 'center',
    slot: 'avg',
    sortable: true,
    sortBy: (row) => Number(row.avgScore) || 0
  },
  { label: '评阅进度', minWidth: 180, slot: 'reviewed' }
]

const store = useStore()
const teacherid = store.state.user?.teacherid

const homeworks = ref([])
const homeworkid = ref(null)

const loading = ref(false)
const assigning = ref(false)
const applying = ref(false)

const taskExists = ref(false)
const progress = ref({})
const summary = ref([])

const form = reactive({ reviewersPer: 2, maxScore: 100, anonymous: true })

const donePercent = computed(() => {
  const total = Number(progress.value.total) || 0
  const done = Number(progress.value.done) || 0
  return total > 0 ? Math.round((done / total) * 100) : 0
})

const isFinished = (row) =>
  Number(row.totalCount) > 0 && Number(row.reviewedCount) === Number(row.totalCount)

const loadHomeworks = async () => {
  if (!teacherid) return
  const res = await listHomeworkByTeacher(teacherid, { page: 1, pageSize: 200 }).catch(() => null)
  homeworks.value = res?.data || []
}

const loadTask = async () => {
  if (!homeworkid.value) return
  loading.value = true
  try {
    const res = await getPeerTask(homeworkid.value).catch(() => null)
    const task = res?.data?.task
    progress.value = res?.data?.progress || {}

    if (!task) {
      taskExists.value = false
      summary.value = []
      return
    }

    taskExists.value = true
    form.reviewersPer = Number(task.reviewersPer) || 2
    form.maxScore = Number(task.maxScore) || 100
    form.anonymous = Number(task.anonymous) === 1

    const summaryRes = await getPeerSummary(homeworkid.value).catch(() => null)
    summary.value = summaryRes?.data || []
  } finally {
    loading.value = false
  }
}

onMounted(loadHomeworks)

/* ---------- 操作 ---------- */

const assign = async () => {
  if (taskExists.value) {
    const ok = await confirmOrAbort('重新分配会清空已有互评记录，确定继续？', '重新分配互评')
    if (!ok) return
  }

  assigning.value = true
  try {
    const res = await assignPeerReview({
      homeworkid: homeworkid.value,
      reviewersPer: form.reviewersPer,
      maxScore: form.maxScore,
      anonymous: form.anonymous ? 1 : 0
    })
    ElMessage.success(res?.msg || '已分配')
    await loadTask()
  } catch {
    // 提交人数不足等业务错误由 http 拦截器提示
  } finally {
    assigning.value = false
  }
}

const applyScores = async () => {
  const ok = await confirmOrAbort(
    '将把每位学生的同伴均分写入其作业成绩并标记为已批改，原成绩会被覆盖。确定继续？',
    '回填参考成绩'
  )
  if (!ok) return

  applying.value = true
  try {
    const res = await applyPeerScores(homeworkid.value)
    ElMessage.success(res?.msg || '已回填')
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    applying.value = false
  }
}

const closeTask = async () => {
  const ok = await confirmOrAbort('关闭后学生将无法继续互评，确定？', '关闭互评')
  if (!ok) return

  try {
    await closePeerReview(homeworkid.value)
    ElMessage.success('已关闭互评')
    await loadTask()
  } catch {
    // 失败提示由 http 拦截器统一处理
  }
}
</script>

<style scoped>
.picker {
  width: 280px;
}

.config {
  display: flex;
  align-items: flex-end;
  gap: var(--zy-space-6);
  flex-wrap: wrap;
}

.config__field {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-2);
}

.config__label {
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.config__actions {
  display: flex;
  gap: var(--zy-space-2);
  margin-left: auto;
}

.config__tip {
  margin-top: var(--zy-space-4);
}

.progress {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-4);
}

.progress__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--zy-space-3);
}

.avg {
  color: var(--zy-brand-base);
  font-variant-numeric: tabular-nums;
}

.tag-gap {
  margin-left: var(--zy-space-1);
}

@media screen and (max-width: 768px) {
  .picker {
    width: 100%;
  }

  .config__actions {
    margin-left: 0;
    width: 100%;
  }
}
</style>

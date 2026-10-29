<template>
  <PageLayout title="作业互评" description="匿名评阅同学作业，每完成一份可得 3 积分。">
    <template #actions>
      <el-button :loading="loading" @click="loadAll">刷新</el-button>
    </template>

    <el-tabs v-model="activeTab">
      <el-tab-pane :label="`待我评阅 (${todoList.length})`" name="todo">
        <DataTable
          :columns="todoColumns"
          :data="todoList"
          :loading="loading"
          :show-pagination="false"
          row-key="id"
          empty-text="暂无待评阅任务"
          empty-hint="教师开启互评后，任务会出现在这里"
          actions-width="120"
        >
          <template #max="{ row }">{{ row.maxScore || DEFAULT_MAX }}</template>
          <template #actions="{ row }">
            <el-button link type="primary" @click="openReview(row)">去评阅</el-button>
          </template>
        </DataTable>
      </el-tab-pane>

      <el-tab-pane :label="`我已完成 (${doneList.length})`" name="done">
        <DataTable
          :columns="doneColumns"
          :data="doneList"
          :show-pagination="false"
          row-key="id"
          empty-text="还没有完成的互评"
          actions-width="130"
        >
          <template #score="{ row }">
            <b class="score">{{ row.score }}</b> / {{ row.maxScore || DEFAULT_MAX }}
          </template>
          <template #time="{ row }">{{ formatDate(row.submitTime, 'minute') }}</template>
          <template #actions="{ row }">
            <el-button link type="primary" @click="openReview(row)">查看 / 修改</el-button>
          </template>
        </DataTable>
      </el-tab-pane>

      <el-tab-pane :label="`我收到的评价 (${received.length})`" name="received">
        <el-empty v-if="!received.length" description="暂未收到互评" />

        <SectionCard
          v-for="group in receivedGroups"
          :key="group.homeworkid"
          :title="`${group.coursename} · ${group.homeworktitle}`"
          class="group"
        >
          <template #action>
            <span class="group__avg">
              同伴均分 <b>{{ group.average }}</b> / {{ group.maxScore }}（{{ group.items.length }} 人评阅）
            </span>
          </template>

          <div v-for="(item, index) in group.items" :key="item.id" class="review">
            <div class="review__head">
              <span class="review__peer">匿名同学 {{ index + 1 }}</span>
              <el-rate :model-value="toStars(item.score, group.maxScore)" disabled />
              <span class="review__score">{{ item.score }} 分</span>
            </div>
            <p class="review__comment">{{ item.comment || '（未填写评语）' }}</p>
          </div>
        </SectionCard>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="reviewVisible" title="评阅作业" width="760px" top="6vh">
      <div v-loading="detailLoading">
        <div class="detail__meta">
          <el-tag effect="plain" disable-transitions>{{ detail.coursename }}</el-tag>
          <span>{{ detail.homeworktitle }}</span>
          <span class="detail__author">作者：{{ detail.revieweename }}</span>
        </div>

        <h4 class="detail__label">作业内容</h4>
        <div class="detail__content">
          <template v-if="detail.filepath">
            <el-alert type="info" :closable="false" show-icon title="这是一份文档作业，请下载后评阅" />
            <el-button class="detail__download" @click="downloadFile(detail.submitid)">
              下载作业文档
            </el-button>
          </template>

          <pre v-if="detail.content" class="detail__text">{{ prettyContent(detail.content) }}</pre>

          <el-empty
            v-if="!detail.content && !detail.filepath"
            description="该同学未提交可见内容"
            :image-size="60"
          />
        </div>

        <el-divider />

        <el-form label-width="72px">
          <el-form-item label="评分">
            <div class="score-row">
              <el-input-number v-model="form.score" :min="0" :max="maxScore" :step="1" />
              <span class="score-row__max">/ {{ maxScore }} 分</span>
              <el-slider v-model="form.score" :min="0" :max="maxScore" class="score-row__slider" />
            </div>
          </el-form-item>
          <el-form-item label="评语">
            <el-input
              v-model="form.comment"
              type="textarea"
              :rows="4"
              maxlength="500"
              show-word-limit
              placeholder="客观指出优点与可改进之处，帮助同学进步"
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交评阅</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>

<script setup>
/** 作业互评（学生端） */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DataTable, PageLayout, SectionCard } from '@/components/ui'
import {
  getPeerReviewDetail,
  listMyPeerTasks,
  listReceivedPeerReviews,
  submitPeerReview
} from '@/api/peerReview'
import { formatDate, formatScore } from '@/utils/format'
import { refreshPeerTodo } from '@/utils/peerBadge'

const DEFAULT_MAX = 100

const todoColumns = [
  { prop: 'coursename', label: '课程', minWidth: 150 },
  { prop: 'homeworktitle', label: '作业', minWidth: 220 },
  { label: '满分', width: 90, align: 'right', slot: 'max' }
]

const doneColumns = [
  { prop: 'coursename', label: '课程', minWidth: 150 },
  { prop: 'homeworktitle', label: '作业', minWidth: 220 },
  { label: '我打的分', width: 130, align: 'right', slot: 'score' },
  { label: '评阅时间', width: 170, slot: 'time' }
]

const activeTab = ref('todo')
const loading = ref(false)
const tasks = ref([])
const received = ref([])

const todoList = computed(() => tasks.value.filter((t) => Number(t.status) === 0))
const doneList = computed(() => tasks.value.filter((t) => Number(t.status) === 1))

/** 收到的评价按作业分组，顺带算出同伴均分 */
const receivedGroups = computed(() => {
  const groups = new Map()

  received.value.forEach((item) => {
    if (!groups.has(item.homeworkid)) {
      groups.set(item.homeworkid, {
        homeworkid: item.homeworkid,
        homeworktitle: item.homeworktitle,
        coursename: item.coursename,
        maxScore: item.maxScore || DEFAULT_MAX,
        items: []
      })
    }
    groups.get(item.homeworkid).items.push(item)
  })

  return [...groups.values()].map((group) => {
    const sum = group.items.reduce((total, x) => total + Number(x.score || 0), 0)
    return { ...group, average: group.items.length ? formatScore(sum / group.items.length) : '0' }
  })
})

/** 把百分制分数换算成五星，用于收到的评价展示 */
const toStars = (score, max) => {
  const full = Number(max) || DEFAULT_MAX
  return Math.round(((Number(score) || 0) / full) * 5)
}

const loadAll = async () => {
  loading.value = true
  try {
    const [mine, recv] = await Promise.all([
      listMyPeerTasks().catch(() => null),
      listReceivedPeerReviews().catch(() => null)
    ])
    tasks.value = mine?.data || []
    received.value = recv?.data || []
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)

/* ---------- 评阅 ---------- */

const reviewVisible = ref(false)
const detailLoading = ref(false)
const submitting = ref(false)
const detail = ref({})
const form = reactive({ score: 0, comment: '' })

const maxScore = computed(() => detail.value.maxScore || DEFAULT_MAX)

const openReview = async (row) => {
  reviewVisible.value = true
  detailLoading.value = true
  detail.value = {}
  Object.assign(form, { score: 0, comment: '' })

  try {
    const res = await getPeerReviewDetail(row.id)
    detail.value = res?.data || {}
    form.score = detail.value.score != null ? Number(detail.value.score) : 0
    form.comment = detail.value.comment || ''
  } catch {
    // 无权评阅等情况由拦截器提示，直接关闭弹窗
    reviewVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const submit = async () => {
  if (form.score == null) {
    ElMessage.warning('请先打分')
    return
  }

  submitting.value = true
  try {
    await submitPeerReview({ id: detail.value.id, score: form.score, comment: form.comment })
    ElMessage.success('评阅已提交，感谢你的认真评价')
    reviewVisible.value = false
    await loadAll()
    refreshPeerTodo()
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

const downloadFile = (submitid) =>
  window.open(`/ZhiYanAcademy/studenthomework/download/${submitid}`, '_blank')

/** 在线答题的内容是 JSON，格式化后更易读；文档类原样展示 */
const prettyContent = (content) => {
  if (!content) return ''
  try {
    return JSON.stringify(JSON.parse(content), null, 2)
  } catch {
    return content
  }
}
</script>

<style scoped>
.score {
  color: var(--zy-brand-base);
  font-variant-numeric: tabular-nums;
}

.group {
  margin-bottom: var(--zy-space-3);
}

.group__avg {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.group__avg b {
  color: var(--zy-brand-base);
  font-size: var(--zy-font-size-base);
}

.review {
  padding: var(--zy-space-3) 0;
  border-bottom: 1px solid var(--zy-border-subtle);
}

.review:last-child {
  border-bottom: none;
}

.review__head {
  display: flex;
  align-items: center;
  gap: var(--zy-space-3);
}

.review__peer {
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.review__score {
  font-size: var(--zy-font-size-sm);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-brand-base);
}

.review__comment {
  margin: var(--zy-space-2) 0 0;
  font-size: var(--zy-font-size-sm);
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-secondary);
}

/* ===== 评阅弹窗 ===== */
.detail__meta {
  display: flex;
  align-items: center;
  gap: var(--zy-space-3);
  flex-wrap: wrap;
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.detail__author {
  color: var(--zy-text-tertiary);
}

.detail__label {
  margin: var(--zy-space-4) 0 var(--zy-space-2);
  font-size: var(--zy-font-size-sm);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.detail__content {
  padding: var(--zy-space-3);
  background: var(--zy-bg-sunken);
  border-radius: var(--zy-radius-md);
}

.detail__download {
  margin-top: var(--zy-space-3);
}

.detail__text {
  margin: 0;
  max-height: 280px;
  overflow: auto;
  font-family: var(--zy-font-mono);
  font-size: var(--zy-font-size-sm);
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-secondary);
  white-space: pre-wrap;
}

.score-row {
  display: flex;
  align-items: center;
  gap: var(--zy-space-3);
  flex-wrap: wrap;
}

.score-row__max {
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-tertiary);
}

.score-row__slider {
  width: 280px;
}

@media screen and (max-width: 768px) {
  .score-row__slider {
    width: 100%;
  }
}
</style>

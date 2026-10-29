<template>
  <PageLayout
    title="我的收藏"
    :description="`共 ${rows.length} 道收藏题目，可随时回到 AI 讲解现场`"
  >
    <template #actions>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </template>

    <div v-loading="loading" class="list">
      <el-empty v-if="!loading && !rows.length" description="还没有收藏，可在 AI 作业讲解中收藏题目" />

      <article v-for="item in rows" :key="item.id" class="card">
        <header class="card__head">
          <el-tag size="small" type="info" effect="plain" disable-transitions>
            作业 #{{ item.homeworkId }}
          </el-tag>
          <span class="card__time">{{ formatDate(item.createTime, 'minute') }}</span>
        </header>

        <p class="card__question">{{ item.questionContent || '（无题目快照）' }}</p>
        <p v-if="preview(item)" class="card__preview">{{ preview(item) }}</p>

        <footer class="card__actions">
          <el-button link type="primary" @click="openDetail(item)">查看讲解快照</el-button>
          <el-button type="primary" :loading="jumpingId === item.id" @click="goExplain(item)">
            回到讲解
          </el-button>
        </footer>
      </article>
    </div>

    <el-dialog v-model="detailVisible" title="讲解快照" width="600px" destroy-on-close>
      <p class="detail__question">{{ detailItem?.questionContent }}</p>
      <pre class="detail__body">{{ detailText }}</pre>
    </el-dialog>
  </PageLayout>
</template>

<script setup>
/** 我的收藏（学生端） */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { PageLayout } from '@/components/ui'
import { listAiFavorites } from '@/api/learning'
import { listStudentHomeworkByStudent } from '@/api/homework'
import { formatDate } from '@/utils/format'

const router = useRouter()
const store = useStore()

const loading = ref(false)
const rows = ref([])
const jumpingId = ref(null)

/** 讲解内容是 AI 返回的结构化 JSON，历史数据里也可能是纯文本 */
const parseExplain = (raw) => {
  if (!raw) return null
  try {
    return typeof raw === 'string' ? JSON.parse(raw) : raw
  } catch {
    return { analysis: String(raw) }
  }
}

const preview = (item) => {
  const parsed = parseExplain(item?.explainContent)
  if (!parsed) return ''

  const keyPoint = Array.isArray(parsed.keyPoints) ? parsed.keyPoints[0] : parsed.keyPoints
  const text = String(parsed.analysis || parsed.errorAnalysis || keyPoint || '')
  return text.length > 120 ? `${text.slice(0, 120)}…` : text
}

const load = async () => {
  loading.value = true
  try {
    const res = await listAiFavorites().catch(() => null)
    rows.value = res?.data || []
  } finally {
    loading.value = false
  }
}

onMounted(load)

/* ---------- 详情 ---------- */

const detailVisible = ref(false)
const detailItem = ref(null)

const detailText = computed(() => {
  const parsed = parseExplain(detailItem.value?.explainContent)
  if (!parsed) return '（无讲解内容）'

  const parts = []
  if (parsed.keyPoints) {
    const points = Array.isArray(parsed.keyPoints) ? parsed.keyPoints.join('\n') : parsed.keyPoints
    parts.push(`要点：\n${points}`)
  }
  if (parsed.analysis || parsed.errorAnalysis) {
    parts.push(`解析：\n${parsed.analysis || parsed.errorAnalysis}`)
  }
  return parts.length ? parts.join('\n\n') : JSON.stringify(parsed, null, 2)
})

const openDetail = (item) => {
  detailItem.value = item
  detailVisible.value = true
}

/* ---------- 跳回讲解 ---------- */

/** 收藏只记了作业 id，讲解页需要的是提交记录 id，要先反查 */
const goExplain = async (item) => {
  const studentid = store.state.user?.studentid
  if (!studentid || !item?.homeworkId) {
    ElMessage.warning('无法定位作业提交记录')
    return
  }

  jumpingId.value = item.id
  try {
    const res = await listStudentHomeworkByStudent(studentid, { page: 1, pageSize: 500 })
    const match = (res?.data || []).find((r) => Number(r.homeworkid) === Number(item.homeworkId))

    if (!match?.id) {
      ElMessage.warning('未找到对应的已提交作业，请先在「已完成作业」中打开讲解')
      return
    }

    router.push({
      path: '/student/homework/explain',
      query: { id: match.id, questionId: item.questionId || undefined }
    })
  } catch {
    ElMessage.error('跳转失败')
  } finally {
    jumpingId.value = null
  }
}
</script>

<style scoped>
.list {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-3);
  min-height: 160px;
}

.card {
  padding: var(--zy-space-4);
  background: var(--zy-bg-surface);
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-surface);
}

.card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--zy-space-2);
}

.card__time {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.card__question {
  margin: 0;
  font-weight: var(--zy-font-weight-medium);
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-primary);
}

.card__preview {
  margin: var(--zy-space-2) 0 0;
  font-size: var(--zy-font-size-sm);
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-secondary);
}

.card__actions {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  margin-top: var(--zy-space-3);
}

.detail__question {
  margin: 0 0 var(--zy-space-3);
  font-weight: var(--zy-font-weight-medium);
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-primary);
}

.detail__body {
  margin: 0;
  padding: var(--zy-space-3);
  background: var(--zy-bg-sunken);
  border-radius: var(--zy-radius-md);
  font-family: inherit;
  font-size: var(--zy-font-size-sm);
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-secondary);
  white-space: pre-wrap;
}
</style>

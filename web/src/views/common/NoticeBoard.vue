<template>
  <PageLayout
    title="通知公告"
    :description="`共 ${total} 条公告，了解教学与平台的最新动态`"
  >
    <template #actions>
      <el-input v-model="keyword" placeholder="搜索通知标题" clearable class="search">
        <template #prefix><Icon icon="ph:magnifying-glass" /></template>
      </el-input>
    </template>

    <div v-loading="loading" class="board">
      <el-empty v-if="!loading && !visibleRows.length" :description="emptyText" />

      <button
        v-for="(row, index) in visibleRows"
        :key="row.noticeid"
        class="card"
        type="button"
        @click="openDetail(row)"
      >
        <span class="card__date">
          <span class="card__day">{{ dateParts(row.createtime).day }}</span>
          <span class="card__month">{{ dateParts(row.createtime).month }}</span>
        </span>

        <span class="card__body">
          <span class="card__title">
            <el-tag v-if="index === 0 && query.page === 1 && !keyword" type="warning" size="small" disable-transitions>
              最新
            </el-tag>
            {{ row.title }}
          </span>
          <span class="card__meta">
            <span><Icon icon="ph:clock-duotone" /> {{ timeAgo(row.createtime) }}</span>
            <span><Icon icon="ph:user-circle-duotone" /> 教务发布</span>
          </span>
        </span>

        <Icon icon="ph:caret-right-bold" class="card__arrow" />
      </button>
    </div>

    <div class="pager">
      <el-pagination
        :current-page="query.page"
        :page-size="query.pageSize"
        :total="total"
        background
        layout="prev, pager, next"
        @update:current-page="onPageChange"
      />
    </div>

    <el-dialog v-model="detailVisible" width="680px">
      <template #header>
        <div class="detail__head">
          <h3 class="detail__title">{{ current.title }}</h3>
          <p class="detail__meta">发布于 {{ formatDate(current.createtime, 'minute') }}</p>
        </div>
      </template>
      <div class="detail__body" v-safe-html="current.content"></div>
    </el-dialog>
  </PageLayout>
</template>

<script setup>
/**
 * 通知公告（教师端与学生端共用）
 *
 * 两端原先各有一份模板完全相同的实现，唯一差别是标记已读时的角色。
 * 这里按登录角色区分，路由直接指向本组件。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useStore } from 'vuex'
import { Icon } from '@iconify/vue'
import { PageLayout } from '@/components/ui'
import { getNotice, listNotices, markNoticeRead } from '@/api/notice'
import { formatDate, timeAgo, toDate } from '@/utils/format'

const store = useStore()

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const keyword = ref('')
const query = reactive({ page: 1, pageSize: 10 })

const emptyText = computed(() => (keyword.value ? '没有匹配的公告' : '暂无通知公告'))

// 后端分页不支持标题检索，这里只在当前页内过滤
const visibleRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return rows.value
  return rows.value.filter((n) => (n.title || '').toLowerCase().includes(kw))
})

const dateParts = (value) => {
  const d = toDate(value)
  if (!d) return { day: '--', month: '--' }
  return { day: String(d.getDate()).padStart(2, '0'), month: `${d.getMonth() + 1} 月` }
}

const load = async () => {
  loading.value = true
  try {
    const res = await listNotices({ ...query }).catch(() => null)
    rows.value = res?.data || []
    total.value = res?.total || 0
  } finally {
    loading.value = false
  }
}

const onPageChange = (page) => {
  query.page = page
  load()
}

onMounted(load)

/* ---------- 详情与已读 ---------- */

const detailVisible = ref(false)
const current = ref({})

/** 已读回执按角色上报，失败不影响阅读本身 */
const reportRead = (noticeid) => {
  const { role } = store.state
  const user = store.state.user || {}
  const idField = role === 'teacher' ? 'teacherid' : 'studentid'
  const userId = user[idField]
  if (!noticeid || !userId || (role !== 'teacher' && role !== 'student')) return

  markNoticeRead(role, { noticeid, [idField]: userId }).catch(() => {})
}

const openDetail = async (row) => {
  const res = await getNotice(row.noticeid).catch(() => null)
  current.value = res?.data || row
  detailVisible.value = true
  reportRead(row.noticeid)
}
</script>

<style scoped>
.search {
  width: 260px;
}

.board {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-2);
  min-height: 200px;
}

.card {
  display: flex;
  align-items: center;
  gap: var(--zy-space-4);
  width: 100%;
  padding: var(--zy-space-3) var(--zy-space-4);
  background: var(--zy-bg-surface);
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-surface);
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color var(--zy-duration-fast) var(--zy-ease-standard),
              background-color var(--zy-duration-fast) var(--zy-ease-standard);
}

.card:hover {
  border-color: var(--zy-brand-border);
  background: var(--zy-bg-hover);
}

.card__date {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 52px;
  height: 56px;
  border-radius: var(--zy-radius-md);
  background: var(--zy-brand-soft);
  border: 1px solid var(--zy-brand-border);
}

.card__day {
  font-size: var(--zy-font-size-xl);
  font-weight: var(--zy-font-weight-bold);
  line-height: 1.1;
  color: var(--zy-brand-base);
  font-variant-numeric: tabular-nums;
}

.card__month {
  font-size: var(--zy-font-size-xs);
  font-weight: var(--zy-font-weight-medium);
  color: var(--zy-brand-base);
}

.card__body {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-1);
  flex: 1;
  min-width: 0;
}

.card__title {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  font-size: var(--zy-font-size-base);
  font-weight: var(--zy-font-weight-medium);
  color: var(--zy-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__meta {
  display: flex;
  align-items: center;
  gap: var(--zy-space-4);
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.card__meta span {
  display: inline-flex;
  align-items: center;
  gap: var(--zy-space-1);
}

.card__arrow {
  flex-shrink: 0;
  font-size: 14px;
  color: var(--zy-text-disabled);
  transition: color var(--zy-duration-fast) var(--zy-ease-standard);
}

.card:hover .card__arrow {
  color: var(--zy-brand-base);
}

.pager {
  display: flex;
  justify-content: center;
}

.detail__title {
  margin: 0;
  font-size: var(--zy-font-size-lg);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.detail__meta {
  margin: var(--zy-space-1) 0 0;
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.detail__body {
  padding: var(--zy-space-4);
  background: var(--zy-bg-sunken);
  border-radius: var(--zy-radius-md);
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-secondary);
}

@media screen and (max-width: 768px) {
  .search {
    width: 100%;
  }
}
</style>

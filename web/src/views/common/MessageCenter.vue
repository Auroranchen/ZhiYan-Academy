<template>
  <PageLayout title="消息中心" :description="summary">
    <template #actions>
      <el-input v-model="search" placeholder="搜索标题或发送者" clearable class="search">
        <template #prefix><Icon icon="ph:magnifying-glass" /></template>
      </el-input>
      <el-button v-if="canMarkAllRead" :disabled="!unreadCount" @click="markAllRead">全部已读</el-button>
      <el-button v-if="canSend" type="primary" @click="openSend">发送消息</el-button>
    </template>

    <div class="toolbar">
      <el-radio-group v-model="activeTab" @change="load">
        <el-radio-button value="received">
          收件箱<span v-if="unreadCount" class="badge">{{ unreadCount }}</span>
        </el-radio-button>
        <el-radio-button v-if="canSend" value="sent">发件箱</el-radio-button>
      </el-radio-group>

      <el-radio-group v-if="activeTab === 'received'" v-model="readFilter" size="small">
        <el-radio-button value="all">全部</el-radio-button>
        <el-radio-button value="unread">未读</el-radio-button>
        <el-radio-button value="read">已读</el-radio-button>
      </el-radio-group>
    </div>

    <div v-loading="loading" class="list">
      <el-empty v-if="!loading && !visibleRows.length" :description="emptyText" />

      <div
        v-for="row in visibleRows"
        :key="row.id"
        class="card"
        :class="{ 'is-unread': isInbox && !row.isread, 'is-static': !isInbox }"
        @click="isInbox && openDetail(row)"
      >
        <span class="card__icon" :class="`is-${meta(row).tone}`">
          <Icon :icon="meta(row).icon" />
        </span>

        <span class="card__body">
          <span class="card__top">
            <span class="card__title">{{ row.title }}</span>
            <el-tag v-if="isInbox" size="small" disable-transitions>{{ meta(row).label }}</el-tag>
          </span>
          <span class="card__preview">{{ preview(row.content) }}</span>
          <span class="card__meta">
            <span v-if="isInbox">
              <Icon icon="ph:user-circle-duotone" /> {{ row.sendername || '系统' }}
            </span>
            <span v-else>
              <Icon icon="ph:arrow-bend-up-right-duotone" /> 发给 {{ row.receivername || '—' }}
            </span>
            <span><Icon icon="ph:clock-duotone" /> {{ timeAgo(row.createtime) }}</span>
          </span>
        </span>

        <span v-if="isInbox" class="card__right">
          <span v-if="!row.isread" class="dot"></span>
          <el-button link type="danger" @click.stop="removeMessage(row)">删除</el-button>
        </span>
      </div>
    </div>

    <div v-if="isInbox" class="pager">
      <el-pagination
        :current-page="page"
        :page-size="PAGE_SIZE"
        :total="total"
        background
        layout="total, prev, pager, next"
        @update:current-page="onPageChange"
      />
    </div>

    <FormDialog
      v-if="canSend"
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
          <el-option v-for="opt in targetOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" placeholder="请输入标题" maxlength="60" show-word-limit />
      </el-form-item>
      <el-form-item label="内容" prop="content">
        <el-input v-model="form.content" type="textarea" :rows="5" placeholder="请输入内容" />
      </el-form-item>
    </FormDialog>

    <el-dialog v-model="detailVisible" width="620px">
      <template #header>
        <div>
          <h3 class="detail__title">{{ detail.title }}</h3>
          <p class="detail__meta">
            {{ detail.sendername || '系统' }} · {{ formatDate(detail.createtime, 'minute') }}
          </p>
        </div>
      </template>
      <div class="detail__body">{{ detailContent }}</div>
      <template v-if="detailAction" #footer>
        <el-button type="primary" @click="goDetailAction">{{ detailAction.label }}</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>

<script setup>
/**
 * 消息中心（教师端与学生端共用）
 *
 * 两端的列表、筛选、搜索、详情与删除完全一致，差异只在能力上：
 * 教师可发送消息并查看发件箱，学生可一键全部已读。按登录角色开关。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useStore } from 'vuex'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Icon } from '@iconify/vue'
import { FormDialog, PageLayout } from '@/components/ui'
import {
  deleteMessage,
  listReceivedMessages,
  listSentMessages,
  markAllMessagesRead,
  markMessageRead,
  sendMessage,
  sendMessageToClass
} from '@/api/message'
import { listStudents } from '@/api/student'
import { listTeachers } from '@/api/teacher'
import { listClasses } from '@/api/classes'
import { formatDate, timeAgo } from '@/utils/format'
import { refreshUnread } from '@/utils/notify'

const PAGE_SIZE = 10
const TARGET_TEXT = { 1: '学生', 2: '教师', 3: '班级' }

/** 消息类型按标题推断，用于列表左侧图标与标签 */
const CATEGORIES = [
  { match: ['签到', '考勤'], icon: 'ph:calendar-check-duotone', tone: 'warning', label: '考勤' },
  { match: ['成绩', '批改'], icon: 'ph:medal-duotone', tone: 'success', label: '成绩' },
  { match: ['资源', '推送'], icon: 'ph:stack-duotone', tone: 'info', label: '资源' },
  { match: ['评价'], icon: 'ph:star-duotone', tone: 'warning', label: '评价' },
  { match: ['作业'], icon: 'ph:file-text-duotone', tone: 'brand', label: '作业' },
  { match: ['答疑', '通知'], icon: 'ph:bell-duotone', tone: 'info', label: '通知' }
]
const DEFAULT_CATEGORY = { icon: 'ph:envelope-duotone', tone: 'neutral', label: '消息' }

/**
 * 系统消息会在正文末尾埋入跳转标记，例如 [homeworkid:12]。
 * 展示时要剔除标记，同时在详情底部给出对应的跳转入口。
 */
const ACTION_MARKER = /\n?\[(?:homeworkid|resourceid|planid):\d+\]/g

const ACTION_ROUTES = [
  { key: 'homeworkid', label: '去提交作业', to: (id) => ({ path: '/student/homework/submit', query: { id } }) },
  { key: 'resourceid', label: '查看资源包', to: (id) => ({ path: '/student/agent', query: { library: '1', resourceId: id } }) },
  { key: 'planid', label: '查看学习路径', to: (id) => ({ path: '/student/learning-path', query: { planId: id } }) }
]

const store = useStore()
const router = useRouter()
const role = computed(() => store.state.role)
const isTeacher = computed(() => role.value === 'teacher')

// 消息表用 1 表示学生、2 表示教师
const partyType = computed(() => (isTeacher.value ? 2 : 1))
const partyId = computed(() =>
  isTeacher.value ? store.state.user?.teacherid : store.state.user?.studentid
)

const canSend = computed(() => isTeacher.value)
const canMarkAllRead = computed(() => !isTeacher.value)

/* ---------- 列表 ---------- */

const activeTab = ref('received')
const isInbox = computed(() => activeTab.value === 'received')

const loading = ref(false)
const receivedRows = ref([])
const sentRows = ref([])
const total = ref(0)
const page = ref(1)
const search = ref('')
const readFilter = ref('all')

const unreadCount = computed(() => receivedRows.value.filter((m) => !m.isread).length)

const summary = computed(() => {
  if (unreadCount.value > 0) return `你有 ${unreadCount.value} 条未读消息`
  return isTeacher.value ? '收发消息，与学生和同事保持沟通' : '查看来自教师与系统的消息'
})

const emptyText = computed(() => {
  if (!isInbox.value) return '暂无已发送消息'
  return search.value || readFilter.value !== 'all' ? '没有匹配的消息' : '暂无消息'
})

// 后端未提供检索与已读筛选，均在当前页内过滤
const visibleRows = computed(() => {
  if (!isInbox.value) return sentRows.value

  let list = receivedRows.value
  if (readFilter.value === 'unread') list = list.filter((m) => !m.isread)
  else if (readFilter.value === 'read') list = list.filter((m) => m.isread)

  const kw = search.value.trim().toLowerCase()
  if (!kw) return list
  return list.filter(
    (m) =>
      (m.title || '').toLowerCase().includes(kw) ||
      (m.sendername || '').toLowerCase().includes(kw)
  )
})

const meta = (row) => {
  const title = row?.title || ''
  return CATEGORIES.find((c) => c.match.some((k) => title.includes(k))) || DEFAULT_CATEGORY
}

/** 去掉正文里用于跳转的内部标记，再截断为一行预览 */
const preview = (content) => {
  const text = String(content || '')
    .replace(ACTION_MARKER, '')
    .replace(/\s+/g, ' ')
    .trim()
  return text.length > 64 ? `${text.slice(0, 64)}…` : text
}

const load = async () => {
  if (!partyId.value) return
  loading.value = true
  try {
    if (isInbox.value) {
      const res = await listReceivedMessages({
        receivertype: partyType.value,
        receiverid: partyId.value,
        page: page.value,
        pageSize: PAGE_SIZE
      }).catch(() => null)
      receivedRows.value = res?.data || []
      total.value = res?.total || 0
    } else {
      const res = await listSentMessages({
        sendertype: partyType.value,
        senderid: partyId.value,
        page: 1,
        pageSize: 100
      }).catch(() => null)
      sentRows.value = res?.data || []
    }
  } finally {
    loading.value = false
  }
}

const onPageChange = (next) => {
  page.value = next
  load()
}

onMounted(load)

/* ---------- 阅读与删除 ---------- */

const detailVisible = ref(false)
const detail = ref({})

const detailContent = computed(() => String(detail.value?.content || '').replace(ACTION_MARKER, '').trim())

/** 跳转目标都在学生端路由下，教师收到的消息不带这些标记 */
const detailAction = computed(() => {
  if (isTeacher.value) return null
  const content = String(detail.value?.content || '')
  for (const route of ACTION_ROUTES) {
    const matched = content.match(new RegExp(`\\[${route.key}:(\\d+)\\]`))
    if (matched) return { label: route.label, to: route.to(matched[1]) }
  }
  return null
})

const goDetailAction = () => {
  if (!detailAction.value) return
  detailVisible.value = false
  router.push(detailAction.value.to)
}

const openDetail = async (row) => {
  detail.value = row
  detailVisible.value = true
  if (row.isread) return

  try {
    await markMessageRead(row.id)
    row.isread = 1
    refreshUnread()
  } catch {
    // 已读状态失败不影响阅读
  }
}

const markAllRead = async () => {
  try {
    await markAllMessagesRead({ receivertype: partyType.value, receiverid: partyId.value })
    receivedRows.value.forEach((m) => { m.isread = 1 })
    refreshUnread()
    ElMessage.success('已全部标记为已读')
  } catch {
    // 失败提示由 http 拦截器统一处理
  }
}

const removeMessage = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除消息「${row.title}」吗？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteMessage(row.id)
    ElMessage.success('已删除')
    await load()
  } catch {
    // 失败提示由 http 拦截器统一处理
  }
}

/* ---------- 发送（仅教师） ---------- */

const sendVisible = ref(false)
const sending = ref(false)

const emptyForm = { targetType: 1, receiverid: null, title: '', content: '' }
const form = reactive({ ...emptyForm })

const rules = {
  receiverid: [{ required: true, message: '请选择接收对象', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
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

/** 候选名单按需加载一次，切回来时复用 */
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

const resetForm = () => Object.assign(form, emptyForm)

const openSend = () => {
  resetForm()
  sendVisible.value = true
  loadTargets(form.targetType)
}

const submitSend = async () => {
  sending.value = true
  try {
    const base = {
      title: form.title,
      content: form.content,
      sendertype: partyType.value,
      senderid: partyId.value
    }

    if (form.targetType === 3) {
      await sendMessageToClass({ ...base, classid: form.receiverid })
    } else {
      await sendMessage({ ...base, receivertype: form.targetType, receiverid: form.receiverid })
    }

    ElMessage.success('已发送')
    sendVisible.value = false
    if (!isInbox.value) await load()
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
.search {
  width: 240px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zy-space-3);
  flex-wrap: wrap;
}

.badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  margin-left: var(--zy-space-1);
  padding: 0 5px;
  border-radius: var(--zy-radius-full);
  background: var(--zy-palette-danger-500);
  color: #fff;
  font-size: 11px;
}

.list {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-2);
  min-height: 200px;
}

.card {
  display: flex;
  align-items: center;
  gap: var(--zy-space-3);
  padding: var(--zy-space-3) var(--zy-space-4);
  background: var(--zy-bg-surface);
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-surface);
  cursor: pointer;
  transition: border-color var(--zy-duration-fast) var(--zy-ease-standard),
              background-color var(--zy-duration-fast) var(--zy-ease-standard);
}

.card:hover {
  border-color: var(--zy-brand-border);
}

.card.is-static {
  cursor: default;
}

.card.is-unread {
  background: var(--zy-brand-soft);
  border-color: var(--zy-brand-border);
}

.card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  border-radius: var(--zy-radius-md);
  font-size: 22px;
}

.card__icon.is-brand { color: var(--zy-brand-base); background: var(--zy-brand-soft); }
.card__icon.is-success { color: var(--zy-success-base); background: var(--zy-success-soft); }
.card__icon.is-warning { color: var(--zy-warning-base); background: var(--zy-warning-soft); }
.card__icon.is-info { color: var(--zy-info-base); background: var(--zy-info-soft); }
.card__icon.is-neutral { color: var(--zy-text-tertiary); background: var(--zy-bg-sunken); }

.card__body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  flex: 1;
  min-width: 0;
}

.card__top {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  min-width: 0;
}

.card__title {
  font-size: var(--zy-font-size-base);
  color: var(--zy-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card.is-unread .card__title {
  color: var(--zy-text-primary);
  font-weight: var(--zy-font-weight-semibold);
}

.card__preview {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
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

.card__right {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  flex-shrink: 0;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--zy-brand-base);
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
  white-space: pre-wrap;
}

@media screen and (max-width: 768px) {
  .search {
    width: 100%;
  }

  .card__preview {
    display: none;
  }
}
</style>

<template>
  <SectionCard v-if="active || sessions.length" title="签到管理">
    <template #action>
      <el-tag v-if="active" type="success" effect="dark" size="small" disable-transitions>
        进行中
      </el-tag>
    </template>

    <template v-if="active">
      <div class="session">
        <div class="session__code">
          <PatternLock v-if="active.type === 3" :model-value="active.checkinCode" readonly :size="120" />
          <span v-else class="session__digits">{{ active.checkinCode }}</span>

          <div class="session__meta">
            <span>{{ active.coursename }} · {{ active.classname }}</span>
            <span v-if="active.type === 3">学生需画出左侧手势签到</span>
            <span>已签到 <b>{{ active.checkedCount || 0 }}</b> / {{ active.totalStudents ?? '?' }} 人</span>
            <span>截止 {{ formatDate(active.endTime, 'minute') }}</span>
          </div>
        </div>

        <div class="session__actions">
          <el-button @click="refresh">刷新</el-button>
          <el-button @click="openDetail(active)">详情</el-button>
          <el-popconfirm
            title="关闭签到后未签到学生将被标记为缺勤，确认？"
            width="260"
            @confirm="close(active.id)"
          >
            <template #reference><el-button type="danger">结束签到</el-button></template>
          </el-popconfirm>
        </div>
      </div>

      <div class="roster">
        <div class="roster__head">
          <span class="roster__title">实时签到情况</span>
          <span class="roster__live"><i></i>实时更新</span>
        </div>

        <div class="roster__cols">
          <div class="roster__col">
            <p class="roster__col-head is-checked">已签到（{{ roster.checkedList.length }}）</p>
            <div class="roster__list">
              <div v-for="s in roster.checkedList" :key="`c${s.studentid}`" class="person">
                <el-avatar :size="34" :src="avatarOf(s)">{{ initialOf(s) }}</el-avatar>
                <div class="person__info">
                  <span class="person__name">{{ s.studentname }}</span>
                  <span class="person__tags">
                    <el-tag
                      :type="s.status === 1 ? 'success' : 'warning'"
                      size="small"
                      effect="light"
                      disable-transitions
                    >
                      {{ s.status === 1 ? '正常' : '迟到' }}
                    </el-tag>
                    <el-tag v-if="s.proxy" type="info" size="small" effect="plain" disable-transitions>
                      代签
                    </el-tag>
                  </span>
                </div>
              </div>
              <el-empty v-if="!roster.checkedList.length" description="暂无人签到" :image-size="48" />
            </div>
          </div>

          <div class="roster__col">
            <p class="roster__col-head is-unchecked">未签到（{{ roster.uncheckedList.length }}）</p>
            <div class="roster__list">
              <div v-for="s in roster.uncheckedList" :key="`u${s.studentid}`" class="person">
                <el-avatar :size="34" :src="avatarOf(s)" class="is-muted">{{ initialOf(s) }}</el-avatar>
                <span class="person__name">{{ s.studentname }}</span>
                <el-popconfirm
                  :title="`确认帮 ${s.studentname} 代签？`"
                  width="220"
                  @confirm="signFor(s, active.id, reloadRoster)"
                >
                  <template #reference>
                    <el-button size="small" type="success" class="person__sign">代签</el-button>
                  </template>
                </el-popconfirm>
              </div>
              <el-empty v-if="!roster.uncheckedList.length" description="全部已签到" :image-size="48" />
            </div>
          </div>
        </div>
      </div>
    </template>

    <DataTable
      v-else
      :columns="sessionColumns"
      :data="sessions.slice(0, RECENT_LIMIT)"
      :show-pagination="false"
      row-key="id"
      empty-text="暂无签到记录"
      actions-width="90"
    >
      <template #attendance="{ row }">
        {{ row.checkedCount || 0 }}/{{ row.totalStudents ?? '?' }}
      </template>
      <template #status="{ row }">
        <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" disable-transitions>
          {{ row.status === 1 ? '进行中' : '已结束' }}
        </el-tag>
      </template>
      <template #time="{ row }">{{ formatDate(row.startTime, 'minute') }}</template>
      <template #actions="{ row }">
        <el-button link type="primary" @click="openDetail(row)">详情</el-button>
      </template>
    </DataTable>
  </SectionCard>

  <!-- 发起签到 -->
  <el-dialog v-model="createVisible" title="发起签到" width="480px">
    <el-form label-width="110px">
      <el-form-item label="课程" required>
        <el-select v-model="form.courseid" class="full">
          <el-option
            v-for="c in courses"
            :key="c.courseid"
            :label="c.coursename"
            :value="c.courseid"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="班级" required>
        <el-select v-model="form.classid" class="full">
          <el-option
            v-for="c in formClasses"
            :key="c.classid"
            :label="c.classname"
            :value="c.classid"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="签到标题">
        <el-input v-model="form.title" placeholder="如：第 5 周考勤" />
      </el-form-item>

      <el-form-item label="签到方式">
        <el-radio-group v-model="form.type">
          <el-radio :value="1">签到码</el-radio>
          <el-radio :value="2">一键签到</el-radio>
          <el-radio :value="3">手势签到</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="form.type === 1" label="自定义签到码">
        <el-input v-model="form.checkinCode" placeholder="留空自动生成 4 位数字" maxlength="6" />
      </el-form-item>

      <el-form-item v-if="form.type === 3" label="设置手势">
        <div class="gesture">
          <PatternLock ref="patternRef" v-model="form.checkinCode" :size="220" />
          <div class="gesture__tips">
            <span v-if="form.checkinCode">已设置 {{ form.checkinCode.length }} 个点的手势</span>
            <span v-else>请在上方绘制手势图案</span>
            <el-button link type="primary" @click="resetPattern">重设手势</el-button>
          </div>
        </div>
      </el-form-item>

      <el-form-item label="持续时长">
        <el-input-number v-model="form.duration" :min="1" :max="60" />
        <span class="unit">分钟</span>
      </el-form-item>

      <el-form-item label="迟到阈值">
        <el-input-number v-model="form.lateMinutes" :min="0" :max="30" />
        <span class="unit">分钟</span>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="creating" @click="create">发起签到</el-button>
    </template>
  </el-dialog>

  <!-- 签到详情 -->
  <el-dialog v-model="detailVisible" title="签到详情" width="650px">
    <template v-if="detail">
      <el-descriptions :column="3" border size="small" class="detail-desc">
        <el-descriptions-item label="课程">{{ detail.session?.coursename }}</el-descriptions-item>
        <el-descriptions-item label="班级">{{ detail.session?.classname }}</el-descriptions-item>
        <el-descriptions-item label="签到码">{{ detail.session?.checkinCode }}</el-descriptions-item>
        <el-descriptions-item label="方式">
          <el-tag :type="detail.session?.type === 3 ? 'warning' : ''" size="small" disable-transitions>
            {{ TYPE_LABEL[detail.session?.type] || '签到码' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detail.session?.status === 1 ? 'success' : 'info'" size="small" disable-transitions>
            {{ detail.session?.status === 1 ? '进行中' : '已结束' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="已签到">{{ detail.checkedList?.length || 0 }}</el-descriptions-item>
        <el-descriptions-item label="未签到">{{ detail.uncheckedList?.length || 0 }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs>
        <el-tab-pane :label="`已签到 (${detail.checkedList?.length || 0})`">
          <DataTable
            :columns="checkedColumns"
            :data="detail.checkedList || []"
            :show-pagination="false"
            row-key="studentid"
            max-height="300"
            empty-text="暂无人签到"
          >
            <template #status="{ row }">
              <el-tag
                :type="row.status === 1 ? 'success' : 'warning'"
                size="small"
                disable-transitions
              >
                {{ row.status === 1 ? '正常' : '迟到' }}
              </el-tag>
              <el-tag v-if="row.proxy" type="info" size="small" class="tag-gap" disable-transitions>
                代签
              </el-tag>
            </template>
            <template #time="{ row }">{{ formatDate(row.time, 'minute') }}</template>
          </DataTable>
        </el-tab-pane>

        <el-tab-pane :label="`未签到 (${detail.uncheckedList?.length || 0})`">
          <DataTable
            :columns="uncheckedColumns"
            :data="detail.uncheckedList || []"
            :show-pagination="false"
            row-key="studentid"
            max-height="300"
            empty-text="全部已签到"
            actions-width="90"
          >
            <template #actions="{ row }">
              <el-popconfirm
                :title="`确认帮 ${row.studentname} 代签？`"
                width="220"
                @confirm="signFor(row, detail.session?.id, () => openDetail(detail.session))"
              >
                <template #reference>
                  <el-button size="small" type="success">代签</el-button>
                </template>
              </el-popconfirm>
            </template>
          </DataTable>
        </el-tab-pane>
      </el-tabs>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 课堂签到面板（教师端）
 *
 * 从考勤页拆出来：签到会话有自己的 WebSocket 推送与轮询兜底，
 * 和考勤记录的增删改查是两套独立的生命周期。
 */
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { DataTable, SectionCard } from '@/components/ui'
import PatternLock from '@/components/PatternLock.vue'
import {
  closeCheckinSession,
  createCheckinSession,
  getCheckinSessionDetail,
  listCheckinSessions,
  proxyCheckin
} from '@/api/attendance'
import { createCheckinSocket } from '@/utils/checkinSocket'
import { formatDate } from '@/utils/format'
import { resolveAvatarUrl } from '@/utils/avatar'

/** WebSocket 负责实时更新，轮询只作兜底，间隔可以放宽 */
const POLL_INTERVAL = 15000
const RECENT_LIMIT = 5
const MIN_GESTURE_POINTS = 4

const TYPE_LABEL = { 1: '签到码', 2: '一键签到', 3: '手势签到' }

const DEFAULT_FORM = {
  courseid: null,
  classid: null,
  title: '',
  type: 1,
  checkinCode: '',
  duration: 10,
  lateMinutes: 5
}

const sessionColumns = [
  { prop: 'title', label: '签到', minWidth: 160 },
  { prop: 'coursename', label: '课程', width: 130 },
  { label: '到场', width: 90, align: 'right', slot: 'attendance' },
  { label: '状态', width: 90, slot: 'status' },
  { label: '时间', width: 160, slot: 'time' }
]

const checkedColumns = [
  { prop: 'studentno', label: '学号', width: 120 },
  { prop: 'studentname', label: '姓名', width: 100 },
  { label: '状态', width: 120, slot: 'status' },
  { label: '签到时间', minWidth: 160, slot: 'time' }
]

const uncheckedColumns = [
  { prop: 'studentno', label: '学号', width: 120 },
  { prop: 'studentname', label: '姓名', minWidth: 120 }
]

const props = defineProps({
  teacherid: { type: [Number, String], default: null },
  courses: { type: Array, default: () => [] },
  /** 由父组件按课程解析班级，避免两处重复实现班级映射逻辑 */
  resolveClasses: { type: Function, required: true }
})

const emit = defineEmits(['closed'])

const sessions = ref([])
const active = ref(null)
const roster = reactive({ checkedList: [], uncheckedList: [] })

const detailVisible = ref(false)
const detail = ref(null)

const createVisible = ref(false)
const creating = ref(false)
const form = reactive({ ...DEFAULT_FORM })
const formClasses = ref([])
const patternRef = ref(null)

const initialOf = (student) => (student?.studentname || '?').charAt(0)
const avatarOf = (student) => resolveAvatarUrl(student?.avatar, 'student', student?.studentid)

/* ---------- 会话与实时名单 ---------- */

let socket = null
let socketClassid = null
let pollTimer = null
let pollingActive = false
let reloadPromise = null
let mounted = false

/** 进行中的签到切换班级时要重连，没有进行中的则断开 */
const syncSocket = () => {
  const classid = active.value?.classid || null
  if (classid === socketClassid) return

  socket?.close()
  socket = null
  socketClassid = classid
  if (!classid) return

  socket = createCheckinSocket(classid, (data) => {
    if (!data) return
    if (data.type === 'checkin') {
      reloadRoster()
    } else if (data.type === 'session_created' || data.type === 'closed') {
      reloadAll()
    }
  })
}

const reloadSessions = async () => {
  const res = await listCheckinSessions(props.teacherid).catch(() => null)
  sessions.value = res?.data || []
  active.value = sessions.value.find((s) => s.status === 1) || null
  syncSocket()
}

const reloadRoster = async () => {
  const id = active.value?.id
  if (!id) {
    Object.assign(roster, { checkedList: [], uncheckedList: [] })
    return
  }

  const res = await getCheckinSessionDetail(id, true).catch(() => null)
  if (!res?.data) return
  Object.assign(roster, {
    checkedList: res.data.checkedList || [],
    uncheckedList: res.data.uncheckedList || []
  })
}

const reloadAll = async () => {
  if (reloadPromise) return reloadPromise

  reloadPromise = (async () => {
    await reloadSessions()
    await reloadRoster()
  })().finally(() => {
    reloadPromise = null
  })

  return reloadPromise
}

const schedulePoll = () => {
  if (!pollingActive) return

  pollTimer = setTimeout(async () => {
    pollTimer = null
    try {
      await reloadAll()
    } finally {
      schedulePoll()
    }
  }, POLL_INTERVAL)
}

const refresh = async () => {
  await reloadAll()
  ElMessage.success('已刷新')
}

onMounted(async () => {
  mounted = true
  await reloadAll()
  if (!mounted) return
  pollingActive = true
  schedulePoll()
})

onBeforeUnmount(() => {
  mounted = false
  pollingActive = false
  clearTimeout(pollTimer)
  pollTimer = null
  socket?.close()
  socket = null
})

/* ---------- 发起签到 ---------- */

const open = () => {
  Object.assign(form, DEFAULT_FORM)
  formClasses.value = []
  createVisible.value = true
}

defineExpose({ open })

watch(
  () => form.courseid,
  (courseid) => {
    formClasses.value = props.resolveClasses(courseid)
    form.classid = null
  }
)

// 签到码与手势共用 checkinCode 字段，切换方式后必须清空
watch(
  () => form.type,
  () => {
    form.checkinCode = ''
  }
)

const resetPattern = () => {
  form.checkinCode = ''
  patternRef.value?.clear()
}

const create = async () => {
  if (!form.courseid || !form.classid) {
    ElMessage.warning('请选择课程和班级')
    return
  }
  if (form.type === 3 && (form.checkinCode || '').length < MIN_GESTURE_POINTS) {
    ElMessage.warning(`请绘制至少连接 ${MIN_GESTURE_POINTS} 个点的手势`)
    return
  }

  creating.value = true
  try {
    const res = await createCheckinSession(props.teacherid, { ...form })
    ElMessage.success(
      form.type === 3 ? '签到已发起（手势签到）' : `签到已发起，签到码：${res?.data?.checkinCode || ''}`
    )
    createVisible.value = false
    await reloadAll()
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    creating.value = false
  }
}

const close = async (id) => {
  await closeCheckinSession(id, props.teacherid)
  ElMessage.success('签到已结束，未签到学生已标记缺勤')
  Object.assign(roster, { checkedList: [], uncheckedList: [] })
  await reloadSessions()
  emit('closed')
}

/* ---------- 详情与代签 ---------- */

const openDetail = async (session) => {
  const res = await getCheckinSessionDetail(session.id).catch(() => null)
  if (!res?.data) return
  detail.value = res.data
  detailVisible.value = true
}

const signFor = async (student, sessionId, after) => {
  if (!sessionId) return
  try {
    await proxyCheckin(sessionId, student.studentid, props.teacherid)
    ElMessage.success(`已帮 ${student.studentname} 代签`)
    await after?.()
  } catch {
    // 失败提示由 http 拦截器统一处理
  }
}
</script>

<style scoped>
.full {
  width: 100%;
}

.unit {
  margin-left: var(--zy-space-2);
  color: var(--zy-text-tertiary);
}

/* ===== 进行中的签到 ===== */
.session {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--zy-space-4);
}

.session__code {
  display: flex;
  align-items: center;
  gap: var(--zy-space-4);
}

.session__digits {
  font-family: var(--zy-font-mono);
  font-size: 36px;
  font-weight: var(--zy-font-weight-bold);
  letter-spacing: 6px;
  color: var(--zy-brand-base);
}

.session__meta {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-1);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.session__meta b {
  color: var(--zy-success-base);
}

.session__actions {
  display: flex;
  gap: var(--zy-space-2);
}

/* ===== 实时名单 ===== */
.roster {
  margin-top: var(--zy-space-5);
  padding-top: var(--zy-space-4);
  border-top: 1px solid var(--zy-border-subtle);
}

.roster__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--zy-space-3);
}

.roster__title {
  font-size: var(--zy-font-size-base);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.roster__live {
  display: inline-flex;
  align-items: center;
  gap: var(--zy-space-1);
  font-size: var(--zy-font-size-xs);
  color: var(--zy-success-base);
}

.roster__live i {
  width: 6px;
  height: 6px;
  background: currentcolor;
  border-radius: var(--zy-radius-full);
  animation: roster-pulse 1.6s infinite;
}

@keyframes roster-pulse {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.25;
  }
}

.roster__cols {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--zy-space-4);
}

.roster__col-head {
  margin: 0 0 var(--zy-space-2);
  padding: var(--zy-space-1) var(--zy-space-3);
  font-size: var(--zy-font-size-sm);
  font-weight: var(--zy-font-weight-semibold);
  border-radius: var(--zy-radius-sm);
}

.roster__col-head.is-checked {
  color: var(--zy-success-base);
  background: var(--zy-success-soft);
}

.roster__col-head.is-unchecked {
  color: var(--zy-warning-base);
  background: var(--zy-warning-soft);
}

.roster__list {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-1);
  max-height: 280px;
  overflow-y: auto;
}

.person {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  padding: var(--zy-space-1) var(--zy-space-2);
  border-radius: var(--zy-radius-md);
}

.person:hover {
  background: var(--zy-bg-hover);
}

.person__info {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
}

.person__name {
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-primary);
}

.person__tags {
  display: inline-flex;
  gap: var(--zy-space-1);
}

.person__sign {
  margin-left: auto;
}

.is-muted {
  filter: grayscale(1);
  opacity: 0.7;
}

/* ===== 弹窗 ===== */
.gesture {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-2);
}

.gesture__tips {
  display: flex;
  align-items: center;
  gap: var(--zy-space-3);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-tertiary);
}

.detail-desc {
  margin-bottom: var(--zy-space-4);
}

.tag-gap {
  margin-left: var(--zy-space-1);
}

@media screen and (max-width: 768px) {
  .roster__cols {
    grid-template-columns: 1fr;
  }
}
</style>

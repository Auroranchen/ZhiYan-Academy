<template>
  <PageLayout title="课堂签到" description="输入教师发布的签到码，或按设定手势完成签到。">
    <template #actions>
      <el-button @click="$router.push('/student/attendance')">考勤记录</el-button>
    </template>

    <SectionCard v-if="activeSessions.length" title="当前可签到" :count="activeSessions.length">
      <div class="sessions">
        <div v-for="s in activeSessions" :key="s.id" class="session">
          <div class="session__info">
            <p class="session__course">
              {{ s.coursename }}
              <el-tag :type="TYPE_TONE[s.type] || ''" size="small" disable-transitions>
                {{ TYPE_LABEL[s.type] || '签到码' }}
              </el-tag>
            </p>
            <p class="session__meta">{{ s.classname }} · {{ s.teachername || '教师' }}</p>
            <p class="session__countdown" :class="{ 'is-expired': remaining(s) <= 0 }">
              {{ remaining(s) > 0 ? `剩余 ${formatCountdown(remaining(s))}` : '已超时' }}
            </p>
          </div>
          <el-button type="primary" :disabled="remaining(s) <= 0" @click="prepare(s)">
            {{ s.type === 3 ? '手势签到' : '去签到' }}
          </el-button>
        </div>
      </div>
    </SectionCard>

    <el-empty
      v-else-if="!result"
      description="当前没有进行中的签到，可直接输入签到码或等待教师发起"
    />

    <SectionCard title="签到">
      <template #action>
        <el-radio-group v-model="mode" size="small">
          <el-radio-button value="code">签到码</el-radio-button>
          <el-radio-button value="gesture">手势</el-radio-button>
        </el-radio-group>
      </template>

      <div v-if="mode === 'code'" class="code">
        <el-input
          v-model="code"
          placeholder="4-6 位签到码"
          maxlength="6"
          size="large"
          class="code__input"
          @keyup.enter="checkin(code)"
        />
        <el-button
          type="primary"
          size="large"
          :loading="submitting"
          :disabled="!code.trim()"
          @click="checkin(code)"
        >
          签到
        </el-button>
      </div>

      <div v-else class="gesture">
        <p class="gesture__hint">按老师设置的手势，在九宫格上画出图案（至少 4 个点）</p>
        <PatternLock ref="patternRef" v-model="gesture" :size="280" @complete="onGestureComplete" />
        <div class="gesture__actions">
          <span v-if="gesture" class="gesture__status">已连接 {{ gesture.length }} 个点</span>
          <el-button link type="primary" @click="resetGesture">重画</el-button>
        </div>
      </div>
    </SectionCard>

    <el-result
      v-if="result"
      :icon="result.success ? 'success' : 'error'"
      :title="result.title"
      :sub-title="result.subtitle"
    >
      <template v-if="result.success" #extra>
        <el-tag :type="result.status === 1 ? 'success' : 'warning'" size="large" disable-transitions>
          {{ result.status === 1 ? '正常签到' : '迟到签到' }}
        </el-tag>
      </template>
    </el-result>

    <SectionCard v-if="records.length" title="最近签到记录">
      <el-timeline>
        <el-timeline-item
          v-for="r in records"
          :key="r.id"
          :type="TIMELINE_TONE[r.status] || 'info'"
          :timestamp="formatDate(r.createtime, 'minute')"
          placement="top"
        >
          <div class="record">
            <span>{{ r.coursename }}</span>
            <el-tag :type="STATUS_TONE[r.status] || 'info'" size="small" disable-transitions>
              {{ STATUS_TEXT[r.status] || '—' }}
            </el-tag>
          </div>
        </el-timeline-item>
      </el-timeline>
    </SectionCard>
  </PageLayout>
</template>

<script setup>
/** 课堂签到（学生端） */
import { onMounted, onUnmounted, ref } from 'vue'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { PageLayout, SectionCard } from '@/components/ui'
import PatternLock from '@/components/PatternLock.vue'
import {
  listActiveCheckinSessions,
  listAttendanceByStudent,
  submitCheckin
} from '@/api/attendance'
import { formatDate, toDate } from '@/utils/format'

const REFRESH_INTERVAL = 10000
const MIN_GESTURE_POINTS = 4

const TYPE_LABEL = { 1: '签到码', 2: '一键签到', 3: '手势滑动' }
const TYPE_TONE = { 2: 'success', 3: 'warning' }

const STATUS_TEXT = { 1: '出勤', 2: '迟到', 3: '早退', 4: '缺勤', 5: '请假' }
const STATUS_TONE = { 1: 'success', 2: 'warning', 3: 'warning', 4: 'danger', 5: 'info' }
const TIMELINE_TONE = { 1: 'success', 2: 'warning', 3: 'warning', 4: 'danger' }

const store = useStore()

const activeSessions = ref([])
const records = ref([])
const result = ref(null)
const submitting = ref(false)

const mode = ref('code')
const code = ref('')
const gesture = ref('')
const patternRef = ref(null)

const now = ref(Date.now())
let timer = null

const remaining = (session) => (toDate(session.endTime)?.getTime() || 0) - now.value

const formatCountdown = (ms) => {
  const seconds = Math.floor(ms / 1000)
  return `${Math.floor(seconds / 60)} 分 ${String(seconds % 60).padStart(2, '0')} 秒`
}

/* ---------- 数据加载 ---------- */

const loadActiveSessions = async () => {
  const classid = store.state.user?.classid
  if (!classid) return
  const res = await listActiveCheckinSessions(classid).catch(() => null)
  activeSessions.value = res?.data || []
}

const loadRecords = async () => {
  const studentid = store.state.user?.studentid
  if (!studentid) return
  const res = await listAttendanceByStudent(studentid).catch(() => null)
  records.value = (res?.data || []).slice(0, 10)
}

onMounted(() => {
  loadActiveSessions()
  loadRecords()
  // 倒计时要走秒，签到列表按较低频率刷新即可
  timer = setInterval(() => {
    now.value = Date.now()
    loadActiveSessions()
  }, REFRESH_INTERVAL)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

/* ---------- 签到 ---------- */

const resetGesture = () => {
  gesture.value = ''
  patternRef.value?.clear()
}

/**
 * 签到码、一键签到、手势三种方式调的是同一个接口，
 * 原先各写了一份几乎相同的提交函数，这里合并。
 */
const checkin = async (rawCode) => {
  const value = String(rawCode || '').trim()
  if (!value) {
    ElMessage.warning('请输入签到码')
    return
  }
  if (submitting.value) return

  submitting.value = true
  try {
    const res = await submitCheckin(value, store.state.user?.studentid)
    result.value = {
      success: true,
      title: res?.data?.statusText || '签到成功',
      subtitle: `${res?.data?.coursename || ''} · ${formatDate(res?.data?.time, 'minute')}`,
      status: res?.data?.status
    }
    ElMessage.success(res?.data?.statusText || '签到成功')
    await Promise.all([loadActiveSessions(), loadRecords()])
  } catch (e) {
    const reason = e?.msg || e?.message || '签到码或手势不正确'
    result.value = { success: false, title: '签到失败', subtitle: reason }
    ElMessage.error(reason)
    if (mode.value === 'gesture') resetGesture()
  } finally {
    submitting.value = false
  }
}

const onGestureComplete = (pattern) => {
  if (!pattern || pattern.length < MIN_GESTURE_POINTS) {
    ElMessage.warning(`手势至少连接 ${MIN_GESTURE_POINTS} 个点`)
    return
  }
  checkin(pattern)
}

/** 一键签到直接用会话里的签到码提交，其余方式只切换到对应输入区 */
const prepare = (session) => {
  result.value = null

  if (session.type === 2) {
    checkin(session.checkinCode)
    return
  }

  if (session.type === 3) {
    mode.value = 'gesture'
    resetGesture()
    ElMessage.info('请按老师的手势在九宫格上画出图案')
  } else {
    mode.value = 'code'
    code.value = ''
    ElMessage.info('请输入签到码完成签到')
  }
}
</script>

<style scoped>
.sessions {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-2);
}

.session {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zy-space-3);
  padding: var(--zy-space-4);
  background: var(--zy-brand-soft);
  border: 1px solid var(--zy-brand-border);
  border-radius: var(--zy-radius-md);
}

.session__course {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  margin: 0;
  font-size: var(--zy-font-size-md);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.session__meta {
  margin: var(--zy-space-1) 0 0;
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-tertiary);
}

.session__countdown {
  margin: var(--zy-space-1) 0 0;
  font-size: var(--zy-font-size-sm);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-warning-base);
  font-variant-numeric: tabular-nums;
}

.session__countdown.is-expired {
  color: var(--zy-text-disabled);
}

.code {
  display: flex;
  align-items: center;
  gap: var(--zy-space-3);
}

.code__input {
  max-width: 260px;
}

.code__input :deep(.el-input__inner) {
  font-size: 24px;
  font-weight: var(--zy-font-weight-bold);
  letter-spacing: 8px;
  text-align: center;
}

.gesture {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--zy-space-3);
}

.gesture__hint {
  margin: 0;
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-tertiary);
}

.gesture__actions {
  display: flex;
  align-items: center;
  gap: var(--zy-space-3);
}

.gesture__status {
  font-size: var(--zy-font-size-sm);
  color: var(--zy-brand-base);
}

.record {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
}

@media screen and (max-width: 768px) {
  .code {
    flex-direction: column;
    align-items: stretch;
  }

  .code__input {
    max-width: 100%;
  }

  .session {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>

<template>
  <div class="layout">
    <TopNav
      :menu-items="menuItems"
      :max-visible="7"
      :user-name="studentName"
      :avatar-url="avatarUrl"
      user-role="学生"
      @logout="logout"
      @command="handleCommand"
    />
    <main class="layout__content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
/** 学生端主布局，同时承载课堂签到的实时提醒 */
import { computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { ElNotification } from 'element-plus'
import TopNav from '@/components/TopNav.vue'
import { listActiveCheckinSessions } from '@/api/attendance'
import { studentLogout } from '@/api/student'
import { useLogout } from '@/composables/useLogout'
import { createCheckinSocket } from '@/utils/checkinSocket'
import { getMenus } from '@/config/menus'

/** WebSocket 断开时靠轮询兜底，间隔不宜过短 */
const POLL_INTERVAL = 30000

const ROUTE_BY_COMMAND = {
  profile: '/student/profile',
  homework: '/student/homework/course'
}

const store = useStore()
const router = useRouter()
const { logout } = useLogout(studentLogout)

const menuItems = getMenus('student')
const studentName = computed(() => store.state.user?.studentname || '学生')
const avatarUrl = computed(() => store.getters.userAvatar || '')

const handleCommand = (command) => {
  if (command === 'logout') return logout()
  const path = ROUTE_BY_COMMAND[command]
  if (path) router.push(path)
}

/* ---------- 签到提醒 ---------- */

let pollTimer = null
let socket = null

/** 已提醒过的签到会话记在本地，避免刷新页面后重复弹窗 */
const notifiedKey = computed(() => `checkin_notified_${store.state.user?.studentid || 'anon'}`)

const readNotified = () => {
  try {
    return JSON.parse(localStorage.getItem(notifiedKey.value) || '[]')
  } catch (e) {
    return []
  }
}

const writeNotified = (ids) => {
  try {
    localStorage.setItem(notifiedKey.value, JSON.stringify(ids))
  } catch (e) {
    console.error('记录签到提醒状态失败:', e)
  }
}

const notifyCheckin = (coursename, sessionId) => {
  const notified = readNotified()
  if (sessionId != null && notified.includes(sessionId)) return

  ElNotification({
    title: '课堂签到提醒',
    message: `《${coursename || '课程'}》正在签到中，点击前往签到`,
    type: 'warning',
    duration: 8000,
    onClick: () => router.push('/student/checkin')
  })

  if (sessionId != null) writeNotified([...notified, sessionId])
}

const checkActiveCheckin = async () => {
  const classid = store.state.user?.classid
  if (!classid) return

  try {
    const res = await listActiveCheckinSessions(classid)
    const sessions = res.data || []
    sessions.forEach((s) => notifyCheckin(s.coursename, s.id))

    // 只留仍在进行中的会话，否则已提醒列表会无限增长
    const activeIds = sessions.map((s) => s.id)
    writeNotified(readNotified().filter((id) => activeIds.includes(id)))
  } catch (e) {
    // 提醒是附加能力，查不到不打扰学生
  }
}

onMounted(() => {
  // 先查一次，覆盖进入页面前就已发起的签到
  checkActiveCheckin()

  const classid = store.state.user?.classid
  if (classid) {
    socket = createCheckinSocket(classid, (data) => {
      if (data?.type === 'session_created') notifyCheckin(data.coursename, data.sessionId)
    })
  }

  pollTimer = setInterval(checkActiveCheckin, POLL_INTERVAL)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (socket) socket.close()
  pollTimer = null
  socket = null
})
</script>

<style scoped>
.layout {
  min-height: 100vh;
  background: var(--zy-bg-canvas);
}

.layout__content {
  max-width: var(--zy-layout-max-width);
  margin: 0 auto;
  padding: var(--zy-page-padding-y) var(--zy-space-8) var(--zy-space-10);
}

@media screen and (max-width: 768px) {
  .layout__content {
    padding: var(--zy-space-4);
  }
}
</style>

<template>
  <div class="layout">
    <TopNav
      :menu-items="menuItems"
      :max-visible="7"
      :user-name="teacherName"
      :avatar-url="avatarUrl"
      user-role="教师端"
      homework-label="作业管理"
      role-key="teacher"
      @logout="logout"
      @command="handleCommand"
    />
    <main class="layout__content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
/** 教师端主布局 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useStore } from 'vuex'
import TopNav from '@/components/TopNav.vue'
import { teacherLogout } from '@/api/teacher'
import { useLogout } from '@/composables/useLogout'
import { getMenus } from '@/config/menus'

const ROUTE_BY_COMMAND = {
  profile: '/teacher/profile',
  homework: '/teacher/homework',
  statistics: '/teacher/statistics'
}

const store = useStore()
const router = useRouter()
const { logout } = useLogout(teacherLogout)

const menuItems = getMenus('teacher')
const teacherName = computed(() => store.state.user?.teachername || '教师')
const avatarUrl = computed(() => store.getters.userAvatar || '')

const handleCommand = (command) => {
  if (command === 'logout') return logout()
  const path = ROUTE_BY_COMMAND[command]
  if (path) router.push(path)
}
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

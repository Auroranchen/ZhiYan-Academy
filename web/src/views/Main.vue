<template>
  <div class="layout">
    <TopNav
      :menu-items="menuItems"
      :max-visible="7"
      :user-name="realname"
      :avatar-url="avatarUrl"
      user-role="管理端"
      homework-label="作业管理"
      role-key="admin"
      @logout="logout"
      @command="handleCommand"
    />
    <main class="layout__content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
/** 管理端主布局 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useStore } from 'vuex'
import TopNav from '@/components/TopNav.vue'
import { adminLogout } from '@/api/system'
import { useLogout } from '@/composables/useLogout'
import { getMenus } from '@/config/menus'

const ROUTE_BY_COMMAND = {
  profile: '/admin/password',
  homework: '/admin/homework'
}

const store = useStore()
const router = useRouter()
const { logout } = useLogout(adminLogout)

const menuItems = getMenus('admin')
const realname = computed(() => store.getters.realname || '教务人员')
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

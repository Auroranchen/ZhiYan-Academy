<template>
  <nav class="top-nav" :class="{ 'top-nav--scrolled': isScrolled }">
    <div class="top-nav__inner">
      <!-- Mobile Hamburger Button (left side on mobile) -->
      <button class="top-nav__hamburger" @click="mobileDrawerVisible = true" aria-label="打开菜单">
        <Icon icon="ph:list-bold" :width="24" :height="24" />
      </button>

      <!-- Logo Area -->
      <div class="top-nav__logo">
        <img src="@/assets/images/logo.png" class="top-nav__logo-img" alt="logo" />
        <span class="top-nav__logo-text">智枢学堂</span>
        <span class="top-nav__role-tag" :class="`top-nav__role-tag--${roleKey}`">{{ userRole }}</span>
      </div>

      <!-- Desktop Navigation -->
      <div class="top-nav__menu">
        <template v-for="item in visibleItems" :key="item.path">
          <!-- Items with children: dropdown -->
          <el-dropdown
            v-if="item.children && item.children.length"
            trigger="hover"
            @command="onNavDropdownCommand"
          >
            <span
              class="top-nav__menu-item"
              :class="{ 'top-nav__menu-item--active': isItemActive(item) }"
            >
              <Icon v-if="item.icon" :icon="item.icon" class="top-nav__menu-icon" />
              {{ item.label }}
              <span v-if="groupBadge(item)" class="top-nav__dot"></span>
              <el-icon class="top-nav__menu-arrow"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="child in item.children"
                  :key="child.path"
                  :command="child.path"
                >
                  {{ child.label }}
                  <el-badge v-if="childBadge(child.label)" :value="childBadge(child.label)" :max="99" style="margin-left:8px" />
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <!-- Items without children: direct link -->
          <router-link
            v-else
            :to="item.path"
            class="top-nav__menu-item"
            :class="{ 'top-nav__menu-item--active': isItemActive(item) }"
          >
            <Icon v-if="item.icon" :icon="item.icon" class="top-nav__menu-icon" />
            {{ item.label }}
            <span v-if="item.label === '消息中心' && unreadCount > 0" class="top-nav__badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
            <span v-if="item.label === '作业互评' && peerTodo > 0" class="top-nav__badge">{{ peerTodo > 99 ? '99+' : peerTodo }}</span>
          </router-link>
        </template>

        <!-- "More" dropdown for overflow items -->
        <el-dropdown v-if="overflowItems.length" trigger="hover" @command="onNavDropdownCommand">
          <span class="top-nav__menu-item">
            更多
            <el-icon class="top-nav__menu-arrow"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <template v-for="item in overflowItems" :key="item.path">
                <el-dropdown-item
                  v-if="!item.children || !item.children.length"
                  :command="item.path"
                >
                  {{ item.label }}
                  <el-badge v-if="item.label === '消息中心' && unreadCount > 0" :value="unreadCount" :max="99" style="margin-left:8px" />
                  <el-badge v-if="item.label === '作业互评' && peerTodo > 0" :value="peerTodo" :max="99" style="margin-left:8px" />
                </el-dropdown-item>
                <!-- Overflow items with children: flat list -->
                <template v-else>
                  <el-dropdown-item
                    :command="item.path"
                    class="top-nav__overflow-group"
                  >
                    {{ item.label }}
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-for="child in item.children"
                    :key="child.path"
                    :command="child.path"
                    class="top-nav__overflow-child"
                  >
                    {{ child.label }}
                    <el-badge v-if="childBadge(child.label)" :value="childBadge(child.label)" :max="99" style="margin-left:8px" />
                  </el-dropdown-item>
                </template>
              </template>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <!-- Quick Action Icons (通知 / 消息等高频入口) -->
      <div v-if="quickItems.length" class="top-nav__quick">
        <el-tooltip v-for="q in quickItems" :key="q.path" :content="q.label" placement="bottom" :show-after="300">
          <button
            class="top-nav__quick-btn"
            :class="{ 'top-nav__quick-btn--active': activeRoute === q.path }"
            @click="goTo(q.path)"
          >
            <Icon :icon="q.icon" :width="20" :height="20" />
            <span v-if="childBadge(q.label)" class="top-nav__quick-badge">{{ childBadge(q.label) > 99 ? '99+' : childBadge(q.label) }}</span>
          </button>
        </el-tooltip>
        <span class="top-nav__divider"></span>
      </div>

      <!-- 外观设置：亮暗主题与显示密度 -->
      <ThemeToggle class="top-nav__theme" />

      <!-- User Area -->
      <div class="top-nav__user">
        <el-dropdown trigger="hover" @command="handleUserCommand">
          <div class="top-nav__user-trigger">
            <UserAvatar :name="userName" :avatar-url="avatarUrl" size="small" class="top-nav__avatar" />
            <span class="top-nav__user-name">{{ userName }}</span>
            <el-icon class="top-nav__user-arrow"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">
                <el-icon><User /></el-icon>个人中心
              </el-dropdown-item>
              <el-dropdown-item command="avatar">
                <el-icon><PictureIcon /></el-icon>修改头像
              </el-dropdown-item>
              <el-dropdown-item command="homework">
                <el-icon><Document /></el-icon>{{ homeworkLabel }}
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <!-- Mobile Avatar (right side on mobile) -->
      <div class="top-nav__mobile-avatar" @click="mobileDrawerVisible = true">
        <UserAvatar :name="userName" :avatar-url="avatarUrl" size="medium" class="top-nav__avatar" />
      </div>
    </div>

    <!-- Mobile Drawer -->
    <el-drawer
      v-model="mobileDrawerVisible"
      title="菜单导航"
      direction="ltr"
      size="280px"
      :with-header="true"
      class="top-nav__drawer"
    >
      <div class="top-nav__drawer-user">
        <div class="top-nav__drawer-avatar-wrap" @click="triggerAvatarUpload">
          <UserAvatar :name="userName" :avatar-url="avatarUrl" size="medium" class="top-nav__avatar" />
          <div class="top-nav__drawer-avatar-edit">
            <Icon icon="ph:camera-duotone" :width="14" :height="14" />
          </div>
        </div>
        <div class="top-nav__drawer-user-info">
          <span class="top-nav__drawer-user-name">{{ userName }}</span>
          <span class="top-nav__drawer-user-role">{{ userRole }}</span>
        </div>
      </div>
      <el-menu
        :default-active="activeRoute"
        :default-openeds="defaultOpeneds"
        router
        @select="mobileDrawerVisible = false"
        class="top-nav__drawer-menu"
      >
        <template v-for="item in menuItems" :key="item.path">
          <el-sub-menu v-if="item.children && item.children.length" :index="item.path">
            <template #title>
              <Icon v-if="item.icon" :icon="item.icon" class="top-nav__drawer-icon" />
              <span>{{ item.label }}</span>
            </template>
            <el-menu-item
              v-for="child in item.children"
              :key="child.path"
              :index="child.path"
            >
              <span>{{ child.label }}</span>
              <el-badge v-if="childBadge(child.label)" :value="childBadge(child.label)" :max="99" class="top-nav__drawer-badge" />
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.path">
            <Icon v-if="item.icon" :icon="item.icon" class="top-nav__drawer-icon" />
            <span>{{ item.label }}</span>
            <el-badge v-if="item.label === '消息中心' && unreadCount > 0" :value="unreadCount" :max="99" class="top-nav__drawer-badge" />
            <el-badge v-if="item.label === '作业互评' && peerTodo > 0" :value="peerTodo" :max="99" class="top-nav__drawer-badge" />
          </el-menu-item>
        </template>
      </el-menu>
      <div class="top-nav__drawer-footer">
        <el-button type="danger" plain @click="handleMobileLogout" style="width: 100%;">
          <el-icon><SwitchButton /></el-icon>退出登录
        </el-button>
      </div>
    </el-drawer>

    <!-- Hidden file input for avatar upload -->
    <input
      ref="avatarInput"
      type="file"
      accept="image/jpeg,image/png,image/gif,image/webp"
      style="display: none"
      @change="handleAvatarFileChange"
    />
  </nav>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { Icon } from '@iconify/vue'
// Picture 与 HTML 的 <picture> 同名，注册时改用别名避免与保留标签冲突
import { User, ArrowDown, Document, SwitchButton, Picture as PictureIcon } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import UserAvatar from '@/components/UserAvatar.vue'
import ThemeToggle from '@/components/ui/ThemeToggle.vue'
import { uploadAvatar } from '@/api/account'
import { notifyState, startNotify, stopNotify } from '@/utils/notify'
import { peerState, startPeerBadge, stopPeerBadge } from '@/utils/peerBadge'

/** 顶栏加毛玻璃的滚动阈值 */
const SCROLL_THRESHOLD = 10
/** 头像文件限制，与后端保持一致 */
const AVATAR_MAX_BYTES = 2 * 1024 * 1024
const AVATAR_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']

/** 带待办角标的菜单项，按名称匹配对应的计数来源 */
const BADGE_SOURCES = {
  消息中心: () => notifyState.unread,
  作业互评: () => peerState.todo
}

const props = defineProps({
  menuItems: { type: Array, required: true, default: () => [] },
  userName: { type: String, default: '用户' },
  userRole: { type: String, default: '学生' },
  homeworkLabel: { type: String, default: '我的作业' },
  /** 角色身份标识（admin / teacher / student）。
      三端统一用蓝色系三档深浅区分，不再用红/橙/绿——
      那是状态色，用在身份标识上会与语义色混淆。 */
  roleKey: { type: String, default: 'student' },
  avatarUrl: { type: String, default: '' },
  /** 顶部直接展示的菜单项数量，超出部分进入「更多」 */
  maxVisible: { type: Number, default: 6 }
})

const emit = defineEmits(['logout', 'command'])

const route = useRoute()
const router = useRouter()
const store = useStore()

const isScrolled = ref(false)
const mobileDrawerVisible = ref(false)
const avatarInput = ref(null)

// 未读消息数：由实时通知单例驱动（WebSocket 推送 + 定时兜底同步）
const unreadCount = computed(() => notifyState.unread)
// 作业互评待办数（学生端红点）
const peerTodo = computed(() => peerState.todo)

const activeRoute = computed(() => route.path)

// 桌面主导航：排除「quick（右侧图标入口）」与「mobileOnly（仅抽屉展示）」
const navItems = computed(() => props.menuItems.filter((i) => !i.quick && !i.mobileOnly))
const visibleItems = computed(() => navItems.value.slice(0, props.maxVisible))
const overflowItems = computed(() => navItems.value.slice(props.maxVisible))

// 右侧快捷图标入口（通知 / 消息）
const quickItems = computed(() => props.menuItems.filter((i) => i.quick))

// 移动端抽屉里的分组默认全部展开
const defaultOpeneds = computed(() =>
  props.menuItems.filter((item) => item.children?.length).map((item) => item.path)
)

const goTo = (path) => router.push(path)

const childBadge = (label) => BADGE_SOURCES[label]?.() || 0

/** 分组父项红点：任一子项有待办即显示 */
const groupBadge = (item) => Boolean(item.children?.some((c) => childBadge(c.label) > 0))

const isItemActive = (item) => {
  if (route.path === item.path) return true
  return Boolean(item.children?.some((child) => route.path === child.path))
}

const onNavDropdownCommand = (path) => router.push(path)

const triggerAvatarUpload = () => {
  if (!avatarInput.value) return
  // 清空后再触发，保证选同一张图也能再次触发 change
  avatarInput.value.value = ''
  avatarInput.value.click()
}

const handleUserCommand = (command) => {
  if (command === 'avatar') triggerAvatarUpload()
  else emit('command', command)
}

const handleAvatarFileChange = async (e) => {
  const file = e.target.files?.[0]
  if (!file) return

  if (!AVATAR_TYPES.includes(file.type)) {
    ElMessage.error('仅支持 JPEG、PNG、GIF、WebP 格式的图片')
    return
  }
  if (file.size > AVATAR_MAX_BYTES) {
    ElMessage.error('文件大小不能超过 2MB')
    return
  }

  const formData = new FormData()
  formData.append('file', file)

  try {
    const res = await uploadAvatar(formData)
    if (res.code === 200 && res.data?.avatarUrl) {
      store.commit('updateAvatar', res.data.avatarUrl)
      ElMessage.success('头像更新成功')
    }
  } catch (err) {
    // 拦截器已提示
  }
}

const handleMobileLogout = () => {
  mobileDrawerVisible.value = false
  emit('logout')
}

const handleScroll = () => {
  isScrolled.value = window.scrollY > SCROLL_THRESHOLD
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
  // 站内消息实时推送（红点 + 弹窗）
  startNotify(store.state.user)
  // 作业互评待办红点（学生端）
  startPeerBadge(store.state.user)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  stopNotify()
  stopPeerBadge()
})
</script>

<style scoped>
/* ===== Top Navigation Bar（深色外壳，恒定深色） =====
 * 外壳在明暗两种主题下都保持深色，与官网首页导航同一调性，
 * 呼应首页「深色顶栏 + 浅色正文」的节奏。
 *
 * 实现方式：把本组件用到的语义令牌整体重定向到 --zy-shell-*，
 * 下面几百行已有规则无需逐条改写就整套变深。
 *
 * 对比度：外壳是暗底，品牌色必须用 brand-500（= --zy-shell-active）；
 * brand-600(#2563eb) 在 #0a0b0e 上只有 3.79:1，不达 WCAG AA 4.5:1。 */
.top-nav {
  --zy-bg-glass: var(--zy-shell-bg-glass);
  --zy-bg-glass-strong: var(--zy-shell-bg-glass-strong);
  --zy-bg-surface: var(--zy-shell-bg);
  --zy-bg-hover: var(--zy-shell-hover);
  --zy-text-primary: var(--zy-shell-text);
  --zy-text-secondary: var(--zy-shell-text-muted);
  --zy-text-tertiary: var(--zy-shell-text-muted);
  --zy-border-subtle: var(--zy-shell-border);
  --zy-border-default: var(--zy-shell-border);
  --zy-brand-base: var(--zy-shell-active);
  --zy-brand-soft: rgba(59, 130, 246, 0.16);
  --zy-brand-on: var(--zy-shell-bg);

  position: sticky;
  top: 0;
  z-index: var(--zy-z-header);
  background: var(--zy-bg-glass);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--zy-border-subtle);
  transition: background-color var(--zy-duration-normal) var(--zy-ease-standard),
              box-shadow var(--zy-duration-normal) var(--zy-ease-standard);
}

.top-nav--scrolled {
  background: var(--zy-bg-glass-strong);
  box-shadow: var(--zy-shadow-sm);
}

.top-nav__inner {
  display: flex;
  align-items: center;
  height: var(--zy-header-height);
  max-width: var(--zy-layout-max-width);
  margin: 0 auto;
  padding: 0 var(--zy-space-8);
}

.top-nav__theme {
  flex-shrink: 0;
  margin-right: var(--zy-space-2);
}

/* ===== Logo ===== */
.top-nav__logo {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  flex-shrink: 0;
  margin-right: var(--zy-space-8);
}

.top-nav__logo-img {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  object-fit: cover;
}

.top-nav__logo-text {
  font-size: var(--zy-font-size-lg);
  font-weight: var(--zy-font-weight-bold);
  white-space: nowrap;
  letter-spacing: var(--zy-letter-spacing-tight);
  color: var(--zy-text-primary);
}

/* 角色标签：身份标识而非状态，三端统一用品牌蓝三档深浅区分。
   刻意不用 el-tag——那是状态组件，覆盖它的内部变量要跟组件样式的
   优先级和加载顺序打架，还容易失效。这里就是个静态小圆片，自己画更稳。
   三个变体都配深色文字——白字在 brand-500/400 这类中浅蓝上
   小字号达不到 4.5:1。 */
.top-nav__role-tag {
  margin-left: var(--zy-space-1);
  padding: 1px 8px;
  border-radius: var(--zy-radius-xs);
  font-size: var(--zy-font-size-xs);
  font-weight: var(--zy-font-weight-semibold);
  line-height: 1.5;
  white-space: nowrap;
}

.top-nav__role-tag--admin {
  background: var(--zy-palette-brand-400);
  color: var(--zy-shell-bg);
}

.top-nav__role-tag--teacher {
  background: var(--zy-palette-brand-300);
  color: var(--zy-shell-bg);
}

.top-nav__role-tag--student {
  background: var(--zy-palette-brand-200);
  color: var(--zy-shell-bg);
}

/* ===== Desktop Menu ===== */
.top-nav__menu {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.top-nav__menu-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 8px 14px;
  color: var(--zy-text-secondary);
  font-size: var(--zy-font-size-base);
  font-weight: var(--zy-font-weight-medium);
  text-decoration: none;
  border-radius: var(--zy-radius-md);
  cursor: pointer;
  transition: color var(--zy-duration-fast) var(--zy-ease-standard),
              background-color var(--zy-duration-fast) var(--zy-ease-standard);
  white-space: nowrap;
  position: relative;
}

.top-nav__menu-item:hover {
  color: var(--zy-text-primary);
  background: var(--zy-bg-hover);
}

.top-nav__menu-item--active {
  color: var(--zy-brand-base);
  background: var(--zy-brand-soft);
  font-weight: var(--zy-font-weight-semibold);
}

.top-nav__menu-item--active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 50%;
  transform: translateX(-50%);
  width: 20px;
  height: 2px;
  background: var(--zy-brand-base);
  border-radius: var(--zy-radius-full);
}

.top-nav__menu-icon {
  font-size: 20px;
}

.top-nav__menu-arrow {
  font-size: 12px;
  margin-left: 2px;
}

/* ===== Overflow items in "More" dropdown ===== */
.top-nav__overflow-group {
  font-weight: 600;
  color: var(--zy-text-secondary) !important;
  font-size: var(--zy-font-size-xs);
}

.top-nav__overflow-child {
  padding-left: 28px !important;
}

/* ===== User Area ===== */
.top-nav__user {
  display: flex;
  align-items: center;
  margin-left: auto;
  flex-shrink: 0;
}

.top-nav__user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: var(--zy-radius-full);
  transition: background var(--zy-duration-fast) var(--zy-ease-standard);
}

.top-nav__user-trigger:hover {
  background: var(--zy-bg-hover);
}

.top-nav__avatar {
  background: var(--zy-brand-base);
  color: var(--zy-brand-on);
}

.top-nav__user-name {
  color: var(--zy-text-primary);
  font-size: var(--zy-font-size-base);
  font-weight: var(--zy-font-weight-medium);
  white-space: nowrap;
}

.top-nav__user-arrow {
  color: var(--zy-text-tertiary);
  font-size: 12px;
  transition: transform var(--zy-duration-fast) var(--zy-ease-standard);
}

.top-nav__user-trigger:hover .top-nav__user-arrow {
  transform: rotate(180deg);
}

/* ===== Unread Badge ===== */
.top-nav__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  color: #fff;
  background: var(--zy-palette-danger-500);
  border-radius: var(--zy-radius-full);
  margin-left: 4px;
}

.top-nav__drawer-badge {
  margin-left: auto;
}

/* 分组父项待办红点 */
.top-nav__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--zy-palette-danger-500);
  flex-shrink: 0;
  margin-left: 2px;
  box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.18);
}

/* ===== Quick Action Icons（通知 / 消息） ===== */
.top-nav__quick {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  flex-shrink: 0;
}

.top-nav__quick-btn {
  position: relative;
  width: 38px;
  height: 38px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--zy-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color var(--zy-duration-fast) var(--zy-ease-standard),
              color var(--zy-duration-fast) var(--zy-ease-standard);
}

.top-nav__quick-btn:hover {
  background: var(--zy-bg-hover);
  color: var(--zy-text-primary);
}

.top-nav__quick-btn--active {
  background: var(--zy-brand-soft);
  color: var(--zy-brand-base);
}

.top-nav__quick-badge {
  position: absolute;
  top: 1px;
  right: -2px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: var(--zy-radius-full);
  background: var(--zy-palette-danger-500);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  line-height: 16px;
  text-align: center;
  border: 2px solid var(--zy-bg-surface);
  box-sizing: content-box;
}

.top-nav__divider {
  width: 1px;
  height: 22px;
  background: var(--zy-border-default);
  margin: 0 var(--zy-space-2) 0 var(--zy-space-3);
}

/* ===== Hamburger (Mobile) ===== */
.top-nav__hamburger {
  display: none;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  color: var(--zy-text-primary);
  cursor: pointer;
  padding: 6px;
  border-radius: var(--zy-radius-sm);
  transition: background-color var(--zy-duration-fast) var(--zy-ease-standard);
}

.top-nav__hamburger:hover {
  background: var(--zy-bg-hover);
}

/* ===== Mobile Avatar (right side) ===== */
.top-nav__mobile-avatar {
  display: none;
  align-items: center;
  margin-left: auto;
  cursor: pointer;
  padding: 4px;
  border-radius: var(--zy-radius-full);
  transition: background-color var(--zy-duration-fast) var(--zy-ease-standard);
}

.top-nav__mobile-avatar:hover {
  background: var(--zy-bg-hover);
}

/* ===== Mobile Drawer ===== */
.top-nav__drawer-user {
  display: flex;
  align-items: center;
  gap: var(--zy-space-4);
  padding: var(--zy-space-4) var(--zy-space-6);
  margin-bottom: var(--zy-space-2);
  border-bottom: 1px solid var(--zy-border-default);
}

.top-nav__drawer-avatar-wrap {
  position: relative;
  cursor: pointer;
  flex-shrink: 0;
}

.top-nav__drawer-avatar-edit {
  position: absolute;
  bottom: -2px;
  right: -2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--zy-brand-base);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid #fff;
}

.top-nav__drawer-user-info {
  display: flex;
  flex-direction: column;
}

.top-nav__drawer-user-name {
  font-size: var(--zy-font-size-base);
  font-weight: 600;
  color: var(--zy-text-primary);
}

.top-nav__drawer-user-role {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.top-nav__drawer-menu {
  border-right: none !important;
}

.top-nav__drawer-icon {
  font-size: 18px;
  margin-right: 8px;
  vertical-align: middle;
}

.top-nav__drawer-footer {
  padding: var(--zy-space-6);
  margin-top: auto;
  border-top: 1px solid var(--zy-border-default);
}

/* ===== Mobile Responsive ===== */
@media screen and (max-width: 768px) {
  .top-nav__inner {
    padding: 0 var(--zy-space-4);
  }

  .top-nav__menu {
    display: none;
  }

  .top-nav__quick {
    display: none;
  }

  .top-nav__user {
    display: none;
  }

  .top-nav__hamburger {
    display: flex;
    margin-right: 8px;
  }

  .top-nav__mobile-avatar {
    display: flex;
  }

  .top-nav__logo-text {
    display: none;
  }

  .top-nav__logo-img {
    width: 42px;
    height: 42px;
  }

  .top-nav__role-tag {
    display: none;
  }
}
</style>

<style>
/* 移动端抽屉 teleport 到 body，已不在 .top-nav 子树里，
 * scoped 样式里的 :deep() 需要一个带 data-v 的祖先才能命中，因此够不到。
 * 组件自己写的元素（.top-nav__drawer-*）仍由上面 scoped 块覆盖；
 * el-drawer / el-menu 的组件内部样式只能在这里用全局选择器处理。
 * 抽屉属于外壳的一部分，同样保持深色。 */
.top-nav__drawer.el-drawer {
  --el-bg-color: var(--zy-shell-bg);
  --el-bg-color-overlay: var(--zy-shell-bg);
  --el-text-color-primary: var(--zy-shell-text);
  --el-text-color-regular: var(--zy-shell-text-muted);
  --el-border-color-lighter: var(--zy-shell-border);
  --el-fill-color-light: var(--zy-shell-hover);
  --el-menu-bg-color: var(--zy-shell-bg);
  --el-menu-text-color: var(--zy-shell-text-muted);
  --el-menu-hover-bg-color: var(--zy-shell-hover);
  --el-menu-active-color: var(--zy-shell-active);

  background: var(--zy-shell-bg);
}

.top-nav__drawer .el-drawer__header {
  margin-bottom: 0;
  padding-bottom: var(--zy-space-4);
  color: var(--zy-shell-text);
  border-bottom: 1px solid var(--zy-shell-border);
}

.top-nav__drawer .el-drawer__close-btn {
  color: var(--zy-shell-text-muted);
}
</style>

<template>
  <div ref="containerRef">
    <div class="pull-refresh-indicator" :style="{ height: pullDistance + 'px' }">
      <template v-if="refreshing">
        <span class="pull-spinner"></span>
        <span>刷新中...</span>
      </template>
      <template v-else-if="pullDistance > 0">
        <Icon icon="ph:arrow-down-bold" class="pull-arrow" :class="{ flipped: pullDistance >= 30 }" />
        <span>{{ pullDistance >= 30 ? '松手刷新' : '下拉刷新' }}</span>
      </template>
    </div>

    <PageLayout :title="`${realname}，你好`" description="教务数据总览与常用入口">
      <template #actions>
        <DateBadge />
      </template>

      <template #summary>
        <StatTile
          v-for="item in statTiles"
          :key="item.label"
          :label="item.label"
          :value="item.value"
          :icon="item.icon"
          :to="item.to"
        />
      </template>

      <div class="home">
        <div class="home__main">
          <SectionCard
            title="最新通知"
            action-label="查看全部"
            action-to="/admin/notice"
            :loading="loading"
            :empty="!recentNotices.length"
            empty-text="暂无通知"
            empty-icon="ph:bell-slash-duotone"
          >
            <FeedList :items="recentNotices" item-key="noticeid" @select="$router.push('/admin/notice')">
              <template #title="{ item }">{{ item.title }}</template>
              <template #meta="{ item }">{{ formatDate(item.createtime) }}</template>
            </FeedList>
          </SectionCard>

          <SectionCard
            title="最近作业"
            action-label="查看全部"
            action-to="/admin/homework"
            :loading="loading"
            :empty="!recentHomeworks.length"
            empty-text="暂无作业"
            empty-icon="ph:clipboard-text-duotone"
          >
            <FeedList :items="recentHomeworks" item-key="homeworkid" @select="$router.push('/admin/homework')">
              <template #title="{ item }">{{ item.homeworkname }}</template>
              <template #sub="{ item }">{{ item.coursename || '未关联课程' }}</template>
              <template #meta="{ item }">
                <el-tag :type="deadlineTone(item.deadline)" size="small" disable-transitions>
                  {{ countdown(item.deadline).text }}
                </el-tag>
              </template>
            </FeedList>
          </SectionCard>
        </div>

        <SectionCard title="快捷入口" class="home__aside">
          <QuickLinks :items="quickLinks" />
        </SectionCard>
      </div>
    </PageLayout>
  </div>
</template>

<script setup>
/** 管理端首页 */
import { computed, ref } from 'vue'
import { useStore } from 'vuex'
import { Icon } from '@iconify/vue'
import { DateBadge, FeedList, PageLayout, QuickLinks, SectionCard, StatTile } from '@/components/ui'
import { usePullRefresh } from '@/composables/usePullRefresh'
import { countdown, formatDate } from '@/utils/format'
import { listMajors } from '@/api/major'
import { listClasses } from '@/api/classes'
import { listStudents } from '@/api/student'
import { listTeachers } from '@/api/teacher'
import { listNotices } from '@/api/notice'
import { listHomework } from '@/api/homework'

const store = useStore()
const realname = computed(() => store.getters.realname || '教务人员')

const loading = ref(true)
const stats = ref({ majorCount: 0, classCount: 0, studentCount: 0, teacherCount: 0 })
const recentNotices = ref([])
const recentHomeworks = ref([])

const statTiles = computed(() => [
  { label: '专业', value: stats.value.majorCount, icon: 'ph:stack-duotone', to: '/admin/major' },
  { label: '班级', value: stats.value.classCount, icon: 'ph:chalkboard-teacher-duotone', to: '/admin/class' },
  { label: '学生', value: stats.value.studentCount, icon: 'ph:student-duotone', to: '/admin/student' },
  { label: '教师', value: stats.value.teacherCount, icon: 'ph:user-circle-duotone', to: '/admin/teacher' }
])

const quickLinks = [
  { label: '专业管理', icon: 'ph:stack-duotone', to: '/admin/major' },
  { label: '班级管理', icon: 'ph:chalkboard-teacher-duotone', to: '/admin/class' },
  { label: '学生管理', icon: 'ph:student-duotone', to: '/admin/student' },
  { label: '教师管理', icon: 'ph:user-circle-duotone', to: '/admin/teacher' },
  { label: '课程管理', icon: 'ph:book-open-duotone', to: '/admin/course' },
  { label: '作业管理', icon: 'ph:file-text-duotone', to: '/admin/homework' },
  { label: '通知公告', icon: 'ph:bell-duotone', to: '/admin/notice' },
  { label: '统计报表', icon: 'ph:chart-bar-duotone', to: '/admin/statistics' }
]

const deadlineTone = (deadline) => {
  const { expired, urgent } = countdown(deadline)
  if (expired) return 'info'
  return urgent ? 'danger' : 'success'
}

/** 只取分页总数，pageSize 传 1 避免把整张表拉回来 */
const countOnly = (fetch) =>
  fetch({ page: 1, pageSize: 1 }).then((res) => res?.total || 0).catch(() => 0)

const refreshAll = async () => {
  const [majorCount, classCount, studentCount, teacherCount, notices, homeworks] = await Promise.all([
    countOnly(listMajors),
    countOnly(listClasses),
    countOnly(listStudents),
    countOnly(listTeachers),
    listNotices({ page: 1, pageSize: 5 }).catch(() => null),
    listHomework({ page: 1, pageSize: 5 }).catch(() => null)
  ])

  stats.value = { majorCount, classCount, studentCount, teacherCount }
  recentNotices.value = notices?.data || []
  recentHomeworks.value = homeworks?.data || []
}

const { pullDistance, refreshing, containerRef } = usePullRefresh(refreshAll)

refreshAll().finally(() => { loading.value = false })
</script>

<style scoped>
.home {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr);
  gap: var(--zy-stack-gap);
  align-items: start;
}

.home__main {
  display: flex;
  flex-direction: column;
  gap: var(--zy-stack-gap);
  min-width: 0;
}

@media screen and (max-width: 1024px) {
  .home {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>

<template>
  <PageLayout
    title="操作审计日志"
    description="记录全站的登录、增删改与导出行为，用于追溯与合规审查。"
    :breadcrumb="[{ label: '系统监管' }, { label: '操作审计日志' }]"
  >
    <template #actions>
      <el-button @click="exportCsv">导出 CSV</el-button>
      <el-popconfirm title="确定清理 90 天前的历史日志吗？" @confirm="cleanup">
        <template #reference>
          <el-button type="danger" plain>清理历史</el-button>
        </template>
      </el-popconfirm>
    </template>

    <template #summary>
      <StatTile label="累计操作" :value="overview.total" />
      <StatTile label="今日操作" :value="todayCount" tone="brand" />
      <StatTile label="登录事件" :value="loginCount" />
      <StatTile label="本页失败操作" :value="failCount" :tone="failCount ? 'danger' : 'default'" />
    </template>

    <div class="charts">
      <SectionCard title="操作类型分布">
        <div ref="actionChartRef" class="chart"></div>
      </SectionCard>
      <SectionCard title="近 14 天操作趋势">
        <div ref="trendChartRef" class="chart"></div>
      </SectionCard>
    </div>

    <FilterBar :loading="loading" @search="search" @reset="reset">
      <el-select v-model="query.userType" placeholder="全部用户类型" clearable>
        <el-option v-for="(label, value) in USER_TYPE_TEXT" :key="value" :label="label" :value="value" />
      </el-select>
      <el-select v-model="query.action" placeholder="全部动作" clearable>
        <el-option v-for="(label, value) in ACTION_TEXT" :key="value" :label="label" :value="value" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="用户名 / 对象 / URI" clearable />
    </FilterBar>

    <DataTable
      v-model:page="query.page"
      v-model:page-size="query.pageSize"
      :columns="columns"
      :data="rows"
      :loading="loading"
      :total="total"
      empty-text="没有匹配的操作记录"
      @change="load"
    >
      <template #time="{ row }">{{ formatDate(row.createTime, 'minute') }}</template>

      <template #user="{ row }">
        <el-tag :type="USER_TYPE_TONE[row.userType] || 'info'" size="small" effect="plain" disable-transitions>
          {{ USER_TYPE_TEXT[row.userType] || row.userType }}
        </el-tag>
        <span class="user-name">{{ row.userName || '—' }}</span>
      </template>

      <template #action="{ row }">
        <el-tag :type="ACTION_TONE[row.action] || 'info'" size="small" disable-transitions>
          {{ ACTION_TEXT[row.action] || row.action }}
        </el-tag>
      </template>

      <template #status="{ row }">
        <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" effect="dark" disable-transitions>
          {{ row.status === 1 ? '成功' : '失败' }}
        </el-tag>
      </template>

      <template #cost="{ row }">{{ row.costMs }} ms</template>
    </DataTable>
  </PageLayout>
</template>

<script setup>
/** 操作审计日志 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DataTable, FilterBar, PageLayout, SectionCard, StatTile } from '@/components/ui'
import { useCrudTable } from '@/composables/useCrudTable'
import { useChart } from '@/composables/useChart'
import { cleanupAuditLogs, getAuditOverview, listAuditLogs } from '@/api/system'
import { formatDate } from '@/utils/format'

const USER_TYPE_TEXT = { admin: '教务人员', teacher: '教师', student: '学生', anon: '游客' }
const USER_TYPE_TONE = { admin: 'danger', teacher: 'warning', student: 'success', anon: 'info' }

const ACTION_TEXT = {
  LOGIN: '登录',
  LOGOUT: '退出',
  CREATE: '新增',
  UPDATE: '修改',
  DELETE: '删除',
  GRADE: '批改',
  EXPORT: '导出',
  OTHER: '其他'
}
const ACTION_TONE = {
  LOGIN: 'success',
  LOGOUT: 'info',
  CREATE: 'primary',
  UPDATE: 'warning',
  DELETE: 'danger',
  EXPORT: 'info'
}

const columns = [
  { label: '时间', width: 160, slot: 'time' },
  { label: '用户', width: 170, slot: 'user' },
  { label: '动作', width: 100, slot: 'action' },
  { prop: 'module', label: '模块', width: 120 },
  { prop: 'target', label: '操作对象', minWidth: 200 },
  { prop: 'method', label: '方法', width: 80 },
  { prop: 'ip', label: 'IP', width: 140 },
  { label: '结果', width: 90, slot: 'status' },
  { label: '耗时', width: 100, align: 'right', slot: 'cost' }
]

const { rows, total, loading, query, load, search, reset } = useCrudTable({
  fetch: listAuditLogs,
  pageSize: 15,
  initialQuery: { userType: '', action: '', keyword: '' }
})

/** 后端概览不含失败数，这里按当前页近似统计 */
const failCount = computed(() => rows.value.filter((r) => r.status !== 1).length)

/* ---------- 概览与图表 ---------- */

const overview = ref({ total: 0, actionStat: [], dailyTrend: [] })

const todayCount = computed(() => {
  const today = new Date().toISOString().slice(0, 10)
  const item = (overview.value.dailyTrend || []).find((d) => d.day === today)
  return item ? Number(item.cnt) : 0
})

const loginCount = computed(() => {
  const item = (overview.value.actionStat || []).find((a) => a.name === 'LOGIN')
  return item ? Number(item.value) : 0
})

const loadOverview = async () => {
  const res = await getAuditOverview().catch(() => null)
  overview.value = {
    total: res?.data?.total || 0,
    actionStat: res?.data?.actionStat || [],
    dailyTrend: res?.data?.dailyTrend || []
  }
}

onMounted(loadOverview)

const actionChartRef = ref(null)
const trendChartRef = ref(null)

useChart(actionChartRef, (palette) => ({
  color: palette.series,
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { bottom: 0, type: 'scroll', textStyle: { color: palette.textMuted } },
  series: [
    {
      type: 'pie',
      radius: ['45%', '70%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderColor: palette.surface, borderWidth: 2 },
      label: { show: false },
      data: (overview.value.actionStat || []).map((a) => ({
        name: ACTION_TEXT[a.name] || a.name,
        value: Number(a.value)
      }))
    }
  ]
}))

useChart(trendChartRef, (palette) => {
  const trend = overview.value.dailyTrend || []
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 20, top: 20, bottom: 40 },
    xAxis: {
      type: 'category',
      data: trend.map((t) => t.day),
      axisLabel: { rotate: 30, color: palette.textMuted },
      axisLine: { lineStyle: { color: palette.axis } }
    },
    yAxis: {
      type: 'value',
      name: '操作量',
      nameTextStyle: { color: palette.textMuted },
      axisLabel: { color: palette.textMuted },
      splitLine: { lineStyle: { color: palette.split } }
    },
    series: [
      {
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.18 },
        data: trend.map((t) => Number(t.cnt)),
        itemStyle: { color: palette.brand }
      }
    ]
  }
})

/* ---------- 导出与清理 ---------- */

const exportCsv = () => {
  const params = new URLSearchParams({
    userType: query.userType || '',
    action: query.action || '',
    keyword: query.keyword || ''
  })
  window.open(`/ZhiYanAcademy/audit/export?${params}`, '_blank')
}

const cleanup = async () => {
  try {
    const res = await cleanupAuditLogs(90)
    ElMessage.success(res?.msg || '清理完成')
    await Promise.all([load(), loadOverview()])
  } catch {
    // 失败提示由 http 拦截器统一处理
  }
}
</script>

<style scoped>
.charts {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 3fr);
  gap: var(--zy-stack-gap);
}

.chart {
  height: 280px;
}

.user-name {
  margin-left: var(--zy-space-1);
}

@media screen and (max-width: 1024px) {
  .charts {
    grid-template-columns: minmax(0, 1fr);
  }

  .chart {
    height: 220px;
  }
}
</style>

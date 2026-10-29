<template>
  <PageLayout title="AI 调用监控" description="各场景的调用量、成功率、耗时与 Token 消耗。">
    <template #actions>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </template>

    <div class="tiles">
      <StatTile
        v-for="tile in tiles"
        :key="tile.label"
        :label="tile.label"
        :value="tile.value"
        :unit="tile.unit"
        :hint="tile.hint"
        :tone="tile.tone"
        :icon="tile.icon"
      />
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="14">
        <SectionCard title="各场景调用统计" :count="scenes.length">
          <DataTable
            :columns="sceneColumns"
            :data="scenes"
            :loading="loading"
            :show-pagination="false"
            row-key="scene"
            max-height="380"
            empty-text="暂无调用记录"
          >
            <template #scene="{ row }">
              <div class="scene">
                <b>{{ sceneLabel(row.scene) }}</b>
                <small>{{ row.scene }}</small>
              </div>
            </template>

            <template #successRate="{ row }">
              <el-progress
                :percentage="successRateOf(row)"
                :stroke-width="10"
                :color="rateColor(successRateOf(row))"
              />
            </template>

            <template #elapsed="{ row }">
              {{ row.avgMs ? `${(row.avgMs / 1000).toFixed(1)}s` : '—' }}
            </template>

            <template #tokens="{ row }">
              <div class="scene">
                <span>{{ formatTokens(row.totalTokens) }}</span>
                <small>均值 {{ formatTokens(row.avgTokens) }}</small>
              </div>
            </template>

            <template #lastCall="{ row }">{{ formatDate(row.lastCall, 'minute') }}</template>
          </DataTable>
        </SectionCard>
      </el-col>

      <el-col :xs="24" :lg="10">
        <SectionCard title="近 24 小时调用趋势" class="row-item">
          <el-empty v-if="!trend.length" description="近 24 小时暂无调用" :image-size="70" />
          <div v-show="trend.length" ref="trendRef" class="trend"></div>
        </SectionCard>

        <SectionCard title="最近失败记录" :count="fails.length">
          <el-empty v-if="!fails.length" description="没有失败记录" :image-size="60" />

          <div v-for="(fail, i) in fails" :key="i" class="fail">
            <div class="fail__head">
              <el-tag size="small" type="danger" effect="plain" disable-transitions>
                {{ sceneLabel(fail.scene) }}
              </el-tag>
              <span class="fail__time">{{ formatDate(fail.createTime, 'minute') }}</span>
            </div>
            <small v-if="fail.model" class="fail__model">{{ fail.model }}</small>
            <p class="fail__error">{{ fail.error || '未知错误' }}</p>
          </div>
        </SectionCard>
      </el-col>
    </el-row>

    <SectionCard title="AI 回答质量反馈">
      <div class="feedback">
        <div class="feedback__item">
          <b>{{ stats.feedback?.total || 0 }}</b>
          <span>反馈总数</span>
        </div>
        <div class="feedback__item">
          <b class="is-good">{{ stats.feedback?.helpfulRate || 0 }}%</b>
          <span>有帮助率</span>
        </div>
        <div class="feedback__item">
          <b class="is-bad">{{ stats.feedback?.unhelpful || 0 }}</b>
          <span>待改进回答</span>
        </div>
      </div>

      <div v-if="reasons.length" class="reasons">
        <span class="reasons__label">主要改进原因：</span>
        <el-tag
          v-for="item in reasons"
          :key="item.reason"
          type="warning"
          effect="plain"
          disable-transitions
        >
          {{ item.reason }} · {{ item.count }}
        </el-tag>
      </div>
      <el-empty v-else description="暂无文字反馈" :image-size="50" />
    </SectionCard>
  </PageLayout>
</template>

<script setup>
/** AI 调用监控（管理端） */
import { computed, onMounted, ref } from 'vue'
import { DataTable, PageLayout, SectionCard, StatTile } from '@/components/ui'
import { useChart } from '@/composables/useChart'
import { getAiStats } from '@/api/system'
import { formatDate } from '@/utils/format'

/** 场景标识由后端埋点写入，这里翻译成中文名 */
const SCENE_LABELS = {
  'agent.diagnose': '诊断智能体',
  'agent.diagnoseClass': '班级诊断智能体',
  'agent.plan': '规划智能体',
  'agent.resource': '资源生成智能体',
  'agent.mindmap': '思维导图智能体',
  'agent.quiz': '出题智能体',
  'agent.reading': '拓展阅读智能体',
  'agent.codecase': '代码实操智能体',
  'agent.explainer': '讲解动画智能体',
  'agent.assemble': '编排智能体',
  'profile.chat': '学习画像对话',
  'profile.evaluate': '学习效果评估',
  'insight.studentReport': '学生学习周报',
  'insight.classReport': '班级学情周报',
  'insight.smartPractice': 'AI 智能练习',
  'insight.mindmap': 'AI 思维导图',
  'insight.mindmapNode': '思维导图详解',
  'learnpath.generate': 'AI 学习路径'
}

const HEALTHY_RATE = 90
const WARNING_RATE = 70

const sceneColumns = [
  { label: '场景', minWidth: 160, slot: 'scene' },
  { prop: 'calls', label: '调用数', width: 90, align: 'right' },
  { prop: 'model', label: '模型', minWidth: 120 },
  { label: '成功率', width: 120, slot: 'successRate' },
  { label: '平均耗时', width: 100, align: 'right', slot: 'elapsed' },
  { label: 'Token', width: 110, align: 'right', slot: 'tokens' },
  { label: '最近调用', width: 160, slot: 'lastCall' }
]

const stats = ref({})
const loading = ref(false)
const trendRef = ref(null)

const scenes = computed(() => stats.value.byScene || [])
const trend = computed(() => stats.value.trend24h || [])
const fails = computed(() => stats.value.recentFails || [])
const reasons = computed(() => stats.value.feedbackReasons || [])

const sceneLabel = (scene) => SCENE_LABELS[scene] || scene

const successRateOf = (row) => {
  const calls = Number(row.calls) || 0
  return calls ? Math.round(((Number(row.success) || 0) * 100) / calls) : 0
}

const rateColor = (rate) => {
  if (rate >= HEALTHY_RATE) return 'var(--zy-success-base)'
  return rate >= WARNING_RATE ? 'var(--zy-warning-base)' : 'var(--zy-danger-base)'
}

/** Token 数动辄上百万，压缩成 K / M 更好读 */
const formatTokens = (value) => {
  const n = Number(value) || 0
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1000) return `${(n / 1000).toFixed(1)}K`
  return String(Math.round(n))
}

const tiles = computed(() => {
  const total = Number(stats.value.total) || 0
  const success = Number(stats.value.success) || 0

  return [
    {
      label: '累计 AI 调用',
      value: total,
      tone: 'brand',
      icon: 'ph:robot-duotone'
    },
    {
      label: '成功率',
      value: total ? Math.round((success * 100) / total) : 0,
      unit: '%',
      tone: 'success',
      icon: 'ph:check-circle-duotone'
    },
    {
      label: '平均响应耗时',
      value: ((Number(stats.value.avgElapsedMs) || 0) / 1000).toFixed(1),
      unit: 's',
      tone: 'default',
      icon: 'ph:timer-duotone'
    },
    {
      label: '累计 Token',
      value: formatTokens(stats.value.totalTokens),
      hint: `近 24 小时 ${formatTokens(stats.value.last24hTokens)}`,
      tone: 'warning',
      icon: 'ph:coins-duotone'
    }
  ]
})

useChart(trendRef, (palette) => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 40, right: 16, top: 32, bottom: 26 },
  legend: { data: ['调用', '成功'], top: 0, textStyle: { color: palette.text, fontSize: 11 } },
  xAxis: {
    type: 'category',
    data: trend.value.map((d) => d.hour),
    axisLabel: { color: palette.textMuted, fontSize: 10 },
    axisLine: { lineStyle: { color: palette.axis } }
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    axisLabel: { color: palette.textMuted },
    splitLine: { lineStyle: { color: palette.split } }
  },
  series: [
    {
      name: '调用',
      type: 'bar',
      data: trend.value.map((d) => d.calls),
      itemStyle: { color: palette.series[0], borderRadius: [3, 3, 0, 0] },
      barMaxWidth: 18
    },
    {
      name: '成功',
      type: 'line',
      smooth: true,
      data: trend.value.map((d) => d.success),
      itemStyle: { color: palette.success }
    }
  ]
}))

const load = async () => {
  loading.value = true
  try {
    const res = await getAiStats().catch(() => null)
    stats.value = res?.data || {}
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.tiles {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--zy-space-3);
}

.row-item {
  margin-bottom: var(--zy-space-4);
}

.scene {
  display: flex;
  flex-direction: column;
}

.scene small {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.trend {
  width: 100%;
  height: 220px;
}

.fail {
  padding: var(--zy-space-2) 0;
  border-bottom: 1px dashed var(--zy-border-subtle);
}

.fail:last-child {
  border-bottom: none;
}

.fail__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zy-space-2);
}

.fail__time,
.fail__model {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.fail__error {
  margin: var(--zy-space-1) 0 0;
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-secondary);
  word-break: break-all;
}

.feedback {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zy-space-12);
}

.feedback__item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.feedback__item b {
  font-size: var(--zy-font-size-3xl);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
  font-variant-numeric: tabular-nums;
}

.feedback__item span {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.feedback__item .is-good {
  color: var(--zy-success-base);
}

.feedback__item .is-bad {
  color: var(--zy-warning-base);
}

.reasons {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--zy-space-2);
  margin-top: var(--zy-space-5);
}

.reasons__label {
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

@media screen and (max-width: 992px) {
  .tiles {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .feedback {
    gap: var(--zy-space-8);
  }
}
</style>

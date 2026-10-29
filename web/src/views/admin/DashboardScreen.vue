<template>
  <!-- 数据可视化大屏 -->
  <div class="screen-wrap">
    <div class="screen-header">
      <div class="screen-title">
        <span class="title-main">智枢学堂 · 教学数据驾驶舱</span>
        <span class="title-sub">Smart Learning Data Cockpit</span>
      </div>
      <div class="screen-clock">
        <span class="clock-time">{{ clock }}</span>
        <el-button size="small" type="primary" plain :icon="Refresh" @click="loadData" :loading="loading">刷新</el-button>
      </div>
    </div>

    <!-- KPI 指标条 -->
    <div class="kpi-row">
      <div class="kpi-card" v-for="k in kpiCards" :key="k.label">
        <div class="kpi-icon" :style="{ background: k.color }">
          <Icon :icon="k.icon" :width="22" :height="22" />
        </div>
        <div class="kpi-body">
          <div class="kpi-value">{{ k.value }}<span class="kpi-unit">{{ k.unit }}</span></div>
          <div class="kpi-label">{{ k.label }}</div>
        </div>
      </div>
    </div>

    <!-- 图表网格 -->
    <div class="grid">
      <div class="panel panel-trend">
        <div class="panel-title">作业提交趋势（近14天）</div>
        <div ref="trendRef" class="chart"></div>
      </div>
      <div class="panel panel-gauge">
        <div class="panel-title">批改完成率</div>
        <div ref="gaugeRef" class="chart"></div>
      </div>
      <div class="panel">
        <div class="panel-title">各课程作业数 Top10</div>
        <div ref="hwCourseRef" class="chart"></div>
      </div>
      <div class="panel">
        <div class="panel-title">成绩区间分布</div>
        <div ref="scoreRef" class="chart"></div>
      </div>
      <div class="panel">
        <div class="panel-title">课程平均分 Top10</div>
        <div ref="avgRef" class="chart"></div>
      </div>
      <div class="panel">
        <div class="panel-title">专业学生分布</div>
        <div ref="majorRef" class="chart"></div>
      </div>
      <div class="panel">
        <div class="panel-title">教师课程负载 Top10</div>
        <div ref="teacherRef" class="chart"></div>
      </div>
      <div class="panel panel-ai">
        <div class="panel-title">AI 智能服务</div>
        <div class="ai-stats">
          <div class="ai-item">
            <div class="ai-num">{{ ai.totalExplains || 0 }}</div>
            <div class="ai-desc">AI讲解次数</div>
          </div>
          <div class="ai-item">
            <div class="ai-num">{{ ai.avgDuration || 0 }}<small>ms</small></div>
            <div class="ai-desc">平均生成耗时</div>
          </div>
          <div class="ai-item">
            <div class="ai-num">{{ ai.cacheHits || 0 }}</div>
            <div class="ai-desc">缓存命中</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 教务数据大屏
 *
 * 面向墙面投屏，整体走深色主题，配色与 ECharts 的 dark 主题绑定，
 * 因此这里的颜色是刻意写死的，不跟随应用的亮/暗主题切换。
 */
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import * as echarts from 'echarts'
import { Icon } from '@iconify/vue'
import { Refresh } from '@element-plus/icons-vue'
import { getDashboardScreen } from '@/api/statistics'

/** 大屏时钟每秒走一次 */
const CLOCK_INTERVAL = 1000

/** 坐标轴在深色底上的统一描边与字色 */
const axisStyle = {
  axisLine: { lineStyle: { color: 'rgba(255,255,255,0.3)' } },
  axisLabel: { color: 'rgba(255,255,255,0.75)' },
  splitLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } }
}
/** 图表自身不画底色，露出大屏的渐变背景 */
const transparentBg = { backgroundColor: 'transparent' }

const loading = ref(false)
const clock = ref('')
const kpi = reactive({})
const ai = reactive({})

const trendRef = ref(null)
const gaugeRef = ref(null)
const hwCourseRef = ref(null)
const scoreRef = ref(null)
const avgRef = ref(null)
const majorRef = ref(null)
const teacherRef = ref(null)
const charts = {}

let clockTimer = null

const kpiCards = computed(() => ([
  { label: '在校学生', value: kpi.students || 0, unit: '人', icon: 'ph:student-duotone', color: 'linear-gradient(135deg,#36d1dc,#5b86e5)' },
  { label: '授课教师', value: kpi.teachers || 0, unit: '人', icon: 'ph:chalkboard-teacher-duotone', color: 'linear-gradient(135deg,#f7971e,#ffd200)' },
  { label: '开设课程', value: kpi.courses || 0, unit: '门', icon: 'ph:book-open-duotone', color: 'linear-gradient(135deg,#11998e,#38ef7d)' },
  { label: '发布作业', value: kpi.homeworks || 0, unit: '份', icon: 'ph:file-text-duotone', color: 'linear-gradient(135deg,#ee0979,#ff6a00)' },
  { label: '作业提交', value: kpi.submissions || 0, unit: '次', icon: 'ph:upload-simple-duotone', color: 'linear-gradient(135deg,#8e2de2,#4a00e0)' },
  { label: '平均成绩', value: kpi.avgScore || 0, unit: '分', icon: 'ph:medal-duotone', color: 'linear-gradient(135deg,#fc5c7d,#6a82fb)' }
]))

const initChart = (refEl, key) => {
  if (refEl.value && !charts[key]) {
    charts[key] = echarts.init(refEl.value, 'dark')
  }
  return charts[key]
}

/** 纵向渐变，用于柱状与面积图 */
const verticalGradient = (from, to) =>
  new echarts.graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: from },
    { offset: 1, color: to }
  ])

/** 横向渐变，用于条形图 */
const horizontalGradient = (from, to) =>
  new echarts.graphic.LinearGradient(0, 0, 1, 0, [
    { offset: 0, color: from },
    { offset: 1, color: to }
  ])

const renderAll = (d) => {
  // 提交趋势
  const trend = d.submitTrend || []
  initChart(trendRef, 'trend').setOption({
    ...transparentBg,
    tooltip: { trigger: 'axis' },
    grid: { left: 45, right: 20, top: 25, bottom: 35 },
    xAxis: { type: 'category', data: trend.map(t => t.day), ...axisStyle, axisLabel: { ...axisStyle.axisLabel, rotate: 30 } },
    yAxis: { type: 'value', ...axisStyle },
    series: [{
      type: 'line', smooth: true, data: trend.map(t => Number(t.cnt)),
      symbolSize: 7, lineStyle: { width: 3, color: '#36d1dc' },
      itemStyle: { color: '#36d1dc' },
      areaStyle: { color: verticalGradient('rgba(54,209,220,0.5)', 'rgba(54,209,220,0)') }
    }]
  }, true)

  // 批改完成率仪表
  initChart(gaugeRef, 'gauge').setOption({
    ...transparentBg,
    series: [{
      type: 'gauge', startAngle: 210, endAngle: -30, min: 0, max: 100,
      progress: { show: true, width: 16, itemStyle: { color: '#38ef7d' } },
      axisLine: { lineStyle: { width: 16, color: [[1, 'rgba(255,255,255,0.12)']] } },
      axisTick: { show: false }, splitLine: { show: false },
      axisLabel: { color: 'rgba(255,255,255,0.6)', distance: 22, fontSize: 10 },
      pointer: { show: false },
      detail: { valueAnimation: true, formatter: '{value}%', color: '#38ef7d', fontSize: 30, offsetCenter: [0, 0] },
      data: [{ value: d.kpi?.gradedRate || 0 }]
    }]
  }, true)

  // 各课程作业数
  const hw = d.homeworkByCourse || []
  initChart(hwCourseRef, 'hw').setOption({
    ...transparentBg,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 45, right: 20, top: 20, bottom: 55 },
    xAxis: { type: 'category', data: hw.map(i => i.name), ...axisStyle, axisLabel: { ...axisStyle.axisLabel, rotate: 35, fontSize: 10 } },
    yAxis: { type: 'value', ...axisStyle },
    series: [{
      type: 'bar', data: hw.map(i => Number(i.value)), barWidth: '50%',
      itemStyle: { borderRadius: [4, 4, 0, 0], color: verticalGradient('#5b86e5', '#36d1dc') }
    }]
  }, true)

  // 成绩区间分布
  const sd = d.scoreDistribution || []
  initChart(scoreRef, 'score').setOption({
    ...transparentBg,
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { color: 'rgba(255,255,255,0.7)' }, type: 'scroll' },
    series: [{
      type: 'pie', radius: ['40%', '68%'], center: ['50%', '45%'],
      itemStyle: { borderColor: 'rgba(0,0,0,0.3)', borderWidth: 2 },
      label: { color: 'rgba(255,255,255,0.8)', fontSize: 10 },
      data: sd.map(i => ({ name: i.name, value: Number(i.value) }))
    }]
  }, true)

  // 课程平均分：条形图从下往上读，数据要倒序
  const avg = (d.avgScoreByCourse || []).slice().reverse()
  initChart(avgRef, 'avg').setOption({
    ...transparentBg,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 90, right: 25, top: 15, bottom: 25 },
    xAxis: { type: 'value', max: 100, ...axisStyle },
    yAxis: { type: 'category', data: avg.map(i => i.name), ...axisStyle, axisLabel: { ...axisStyle.axisLabel, fontSize: 10 } },
    series: [{
      type: 'bar', data: avg.map(i => Number(i.value)), barWidth: '55%',
      itemStyle: { borderRadius: [0, 4, 4, 0], color: horizontalGradient('#f7971e', '#ffd200') }
    }]
  }, true)

  // 专业分布
  const mj = d.studentsByMajor || []
  initChart(majorRef, 'major').setOption({
    ...transparentBg,
    tooltip: { trigger: 'item', formatter: '{b}: {c}人 ({d}%)' },
    legend: { bottom: 0, textStyle: { color: 'rgba(255,255,255,0.7)' }, type: 'scroll' },
    series: [{
      type: 'pie', roseType: 'radius', radius: ['25%', '65%'], center: ['50%', '45%'],
      label: { color: 'rgba(255,255,255,0.8)', fontSize: 10 },
      data: mj.map(i => ({ name: i.name, value: Number(i.value) }))
    }]
  }, true)

  // 教师负载
  const tw = (d.teacherWorkload || []).slice().reverse()
  initChart(teacherRef, 'teacher').setOption({
    ...transparentBg,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 70, right: 25, top: 15, bottom: 25 },
    xAxis: { type: 'value', ...axisStyle },
    yAxis: { type: 'category', data: tw.map(i => i.name), ...axisStyle, axisLabel: { ...axisStyle.axisLabel, fontSize: 10 } },
    series: [{
      type: 'bar', data: tw.map(i => Number(i.value)), barWidth: '55%',
      itemStyle: { borderRadius: [0, 4, 4, 0], color: horizontalGradient('#8e2de2', '#4a00e0') }
    }]
  }, true)
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getDashboardScreen()
    const d = res.data || {}
    Object.assign(kpi, d.kpi || {})
    Object.assign(ai, d.aiUsage || {})
    renderAll(d)
  } catch (e) {
    console.error('加载大屏数据失败', e)
  } finally {
    loading.value = false
  }
}

const tickClock = () => {
  clock.value = new Date().toLocaleString('zh-CN', { hour12: false })
}

const handleResize = () => Object.values(charts).forEach((c) => c && c.resize())

onMounted(() => {
  tickClock()
  clockTimer = setInterval(tickClock, CLOCK_INTERVAL)
  loadData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  clearInterval(clockTimer)
  window.removeEventListener('resize', handleResize)
  Object.values(charts).forEach((c) => c && c.dispose())
})
</script>

<style scoped>
.screen-wrap {
  min-height: calc(100vh - 40px);
  background: radial-gradient(1200px 600px at 50% -10%, #143a66 0%, #0b1f3a 45%, #060f22 100%);
  border-radius: 12px;
  padding: 18px 22px 26px;
  color: #e6f0ff;
}
.screen-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.title-main { font-size: 24px; font-weight: 800; letter-spacing: 2px;
  background: linear-gradient(90deg, #36d1dc, #5b86e5); -webkit-background-clip: text; background-clip: text; color: transparent; }
.title-sub { display: block; font-size: 11px; color: rgba(255,255,255,0.45); letter-spacing: 3px; margin-top: 2px; }
.screen-clock { display: flex; align-items: center; gap: 12px; }
.clock-time { font-size: 16px; font-variant-numeric: tabular-nums; color: rgba(255,255,255,0.85); }

.kpi-row { display: grid; grid-template-columns: repeat(6, 1fr); gap: 14px; margin-bottom: 16px; }
.kpi-card { display: flex; align-items: center; gap: 12px; padding: 14px 16px;
  background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.08); border-radius: 12px;
  backdrop-filter: blur(4px); }
.kpi-icon { width: 44px; height: 44px; border-radius: 10px; display: flex; align-items: center; justify-content: center; color: #fff; flex-shrink: 0; }
.kpi-value { font-size: 26px; font-weight: 800; line-height: 1; }
.kpi-unit { font-size: 12px; font-weight: 500; margin-left: 3px; color: rgba(255,255,255,0.6); }
.kpi-label { font-size: 12px; color: rgba(255,255,255,0.6); margin-top: 6px; }

.grid { display: grid; grid-template-columns: repeat(4, 1fr); grid-auto-rows: 260px; gap: 14px; }
.panel { background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.08);
  border-radius: 12px; padding: 12px 14px; display: flex; flex-direction: column; overflow: hidden; }
.panel-title { font-size: 14px; font-weight: 600; margin-bottom: 8px; color: #9fd4ff;
  border-left: 3px solid #36d1dc; padding-left: 8px; }
.chart { flex: 1; width: 100%; }
.panel-trend { grid-column: span 2; }
.panel-gauge { grid-column: span 1; }
.panel-ai { display: flex; flex-direction: column; }
.ai-stats { flex: 1; display: flex; flex-direction: column; justify-content: center; gap: 14px; }
.ai-item { text-align: center; }
.ai-num { font-size: 30px; font-weight: 800; color: #38ef7d; }
.ai-num small { font-size: 13px; margin-left: 2px; color: rgba(255,255,255,0.5); }
.ai-desc { font-size: 12px; color: rgba(255,255,255,0.6); margin-top: 2px; }

@media screen and (max-width: 1280px) {
  .kpi-row { grid-template-columns: repeat(3, 1fr); }
  .grid { grid-template-columns: repeat(2, 1fr); }
  .panel-trend { grid-column: span 2; }
}
@media screen and (max-width: 768px) {
  .kpi-row { grid-template-columns: repeat(2, 1fr); }
  .grid { grid-template-columns: 1fr; }
  .panel-trend, .panel-gauge { grid-column: span 1; }
}
</style>

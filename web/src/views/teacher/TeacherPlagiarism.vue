<template>
  <PageLayout
    title="作业查重"
    description="基于主观作答文本的字符级相似度比对，并可叠加 AI 原创性检测。"
    :breadcrumb="[{ label: '教学管理' }, { label: '作业查重' }]"
  >
    <SectionCard title="检测设置">
      <div class="config">
        <div class="config__field">
          <label class="config__label">选择作业</label>
          <el-select v-model="homeworkid" placeholder="请选择作业" filterable class="picker" @change="onHomeworkChange">
            <el-option v-for="h in homeworks" :key="h.homeworkid" :label="h.title" :value="h.homeworkid">
              <span>{{ h.title }}</span>
              <span class="option-tag">{{ h.type === 1 ? '在线' : '文档' }}</span>
            </el-option>
          </el-select>
        </div>

        <div class="config__field">
          <label class="config__label">判定阈值 {{ threshold }}%</label>
          <el-slider v-model="threshold" :min="40" :max="95" :step="5" class="slider" />
        </div>

        <div class="config__actions">
          <el-button :loading="aiChecking" :disabled="!homeworkid" @click="runAiDetect">
            AI 原创性检测
          </el-button>
          <el-button type="primary" :loading="checking" :disabled="!homeworkid" @click="runCheck">
            开始查重
          </el-button>
        </div>
      </div>

      <el-alert type="info" :closable="false" show-icon class="config__tip">
        查重比对的是学生主观作答（简答、填空）的文本，采用 Jaccard 3-gram 字符级相似度。
        纯客观题与文档上传类作业无法比对文本。
      </el-alert>
    </SectionCard>

    <SectionCard v-if="aiResults.length" title="AI 原创性检测" :count="aiResults.length">
      <el-alert type="warning" :closable="false" show-icon class="config__tip">
        AI 检测结果仅供参考，不能作为唯一判定依据，请结合人工复核与面谈确认。
      </el-alert>

      <DataTable
        :columns="aiColumns"
        :data="aiResults"
        :show-pagination="false"
        show-index
        max-height="460"
        row-key="studentno"
      >
        <template #student="{ row }">
          {{ row.studentname }}<small class="sub">{{ row.studentno }}</small>
        </template>
        <template #ai="{ row }">
          <el-progress :percentage="row.aiScore" :color="riskColor(row.aiScore, 70, 40)" :stroke-width="12" />
        </template>
        <template #originality="{ row }">{{ row.originality }}%</template>
        <template #verdict="{ row }">
          <el-tag :type="verdictTone(row.verdict)" size="small" disable-transitions>{{ row.verdict }}</el-tag>
        </template>
      </DataTable>
    </SectionCard>

    <SectionCard v-if="report" :title="`查重报告 · ${report.homeworktitle}`">
      <template #action>
        <el-button link type="primary" :disabled="!pairs.length" @click="exportPairs">导出明细</el-button>
      </template>

      <div class="summary">
        <StatTile label="比对对数" :value="report.pairCount" />
        <StatTile label="疑似抄袭对" :value="report.flaggedCount" :tone="report.flaggedCount ? 'danger' : 'success'" />
        <StatTile label="最高相似度" :value="report.maxSimilarity" unit="%" />
        <StatTile label="判定阈值" :value="report.threshold" unit="%" />
      </div>

      <DataTable
        :columns="pairColumns"
        :data="pairs"
        :show-pagination="false"
        show-index
        max-height="460"
        empty-text="未发现可比对的相似对"
      >
        <template #a="{ row }">
          {{ row.studentnameA }}<small class="sub">{{ row.studentnoA }}</small>
        </template>
        <template #b="{ row }">
          {{ row.studentnameB }}<small class="sub">{{ row.studentnoB }}</small>
        </template>
        <template #similarity="{ row }">
          <el-progress :percentage="row.similarity" :color="riskColor(row.similarity, 85, 70)" :stroke-width="12" />
        </template>
        <template #flagged="{ row }">
          <el-tag :type="row.flagged ? 'danger' : 'success'" size="small" disable-transitions>
            {{ row.flagged ? '疑似' : '正常' }}
          </el-tag>
        </template>
      </DataTable>
    </SectionCard>

    <SectionCard v-if="history.length" title="历史查重记录" :count="history.length">
      <DataTable
        :columns="historyColumns"
        :data="history"
        :show-pagination="false"
        row-key="id"
        actions-width="100"
      >
        <template #time="{ row }">{{ formatDate(row.createTime, 'minute') }}</template>
        <template #max="{ row }">{{ row.maxSimilarity }}%</template>
        <template #threshold="{ row }">{{ row.threshold }}%</template>
        <template #actions="{ row }">
          <el-button link type="primary" @click="viewReport(row)">查看</el-button>
        </template>
      </DataTable>
    </SectionCard>
  </PageLayout>
</template>

<script setup>
/** 作业查重（教师端） */
import { onMounted, ref } from 'vue'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { DataTable, PageLayout, SectionCard, StatTile } from '@/components/ui'
import { listHomeworkByTeacher } from '@/api/homework'
import {
  getPlagiarismReport,
  listPlagiarismReports,
  runAiOriginalityDetect,
  runPlagiarismCheck
} from '@/api/plagiarism'
import { downloadCsv } from '@/utils/excel'
import { formatDate } from '@/utils/format'

const aiColumns = [
  { label: '学生', minWidth: 150, slot: 'student' },
  { label: 'AI 生成可能性', minWidth: 220, slot: 'ai' },
  { label: '原创度', width: 100, align: 'center', slot: 'originality' },
  { label: '判定', width: 130, align: 'center', slot: 'verdict' },
  { prop: 'reason', label: '依据', minWidth: 220 }
]

const pairColumns = [
  { label: '学生 A', minWidth: 150, slot: 'a' },
  { label: '学生 B', minWidth: 150, slot: 'b' },
  { label: '相似度', minWidth: 240, slot: 'similarity' },
  { label: '判定', width: 100, align: 'center', slot: 'flagged' }
]

const historyColumns = [
  { label: '检测时间', minWidth: 170, slot: 'time' },
  { prop: 'pairCount', label: '比对对数', width: 110, align: 'right' },
  { prop: 'flaggedCount', label: '疑似对数', width: 110, align: 'right' },
  { label: '最高相似度', width: 130, align: 'right', slot: 'max' },
  { label: '阈值', width: 90, align: 'right', slot: 'threshold' }
]

const store = useStore()

const homeworks = ref([])
const homeworkid = ref(null)
const threshold = ref(70)

const checking = ref(false)
const aiChecking = ref(false)

const report = ref(null)
const pairs = ref([])
const history = ref([])
const aiResults = ref([])

/** 进度条配色：超过 high 判红，超过 mid 判橙，否则绿 */
const riskColor = (value, high, mid) => {
  const palette = getComputedStyle(document.documentElement)
  const read = (name) => palette.getPropertyValue(name).trim()
  if (value >= high) return read('--zy-danger-base')
  if (value >= mid) return read('--zy-warning-base')
  return read('--zy-success-base')
}

const verdictTone = (verdict) => {
  if (verdict?.includes('高度')) return 'danger'
  if (verdict?.includes('疑似')) return 'warning'
  return 'success'
}

const loadHomeworks = async () => {
  const teacherid = store.state.user?.teacherid
  if (!teacherid) return
  const res = await listHomeworkByTeacher(teacherid, { page: 1, pageSize: 200 }).catch(() => null)
  homeworks.value = res?.data || []
}

onMounted(loadHomeworks)

/** 报告结果以 JSON 字符串存在 resultJson 字段里 */
const parsePairs = (json) => {
  try {
    return JSON.parse(json || '[]')
  } catch {
    return []
  }
}

const loadHistory = async () => {
  if (!homeworkid.value) {
    history.value = []
    return
  }
  const res = await listPlagiarismReports(homeworkid.value).catch(() => null)
  history.value = res?.data || []
}

/** 换作业时清空上一份作业的所有结果，避免张冠李戴 */
const onHomeworkChange = () => {
  report.value = null
  pairs.value = []
  aiResults.value = []
  loadHistory()
}

const runCheck = async () => {
  checking.value = true
  try {
    const res = await runPlagiarismCheck({ homeworkid: homeworkid.value, threshold: threshold.value })
    report.value = res.data
    pairs.value = parsePairs(res.data?.resultJson)
    ElMessage.success(res?.msg || '查重完成')
    await loadHistory()
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    checking.value = false
  }
}

const runAiDetect = async () => {
  aiChecking.value = true
  aiResults.value = []
  try {
    const res = await runAiOriginalityDetect(homeworkid.value)
    aiResults.value = res?.data || []
    ElMessage.success(res?.msg || 'AI 检测完成')
  } catch (e) {
    ElMessage.error(`AI 检测失败：${e?.msg || e?.message || '请稍后重试'}`)
  } finally {
    aiChecking.value = false
  }
}

const viewReport = async (row) => {
  const res = await getPlagiarismReport(row.id).catch(() => null)
  if (!res?.data) return
  report.value = res.data
  pairs.value = parsePairs(res.data.resultJson)
}

const exportPairs = () => {
  downloadCsv({
    headers: ['学生A学号', '学生A', '学生B学号', '学生B', '相似度(%)', '判定'],
    rows: pairs.value.map((p) => [
      p.studentnoA,
      p.studentnameA,
      p.studentnoB,
      p.studentnameB,
      p.similarity,
      p.flagged ? '疑似' : '正常'
    ]),
    fileName: `查重明细_${report.value?.homeworktitle || ''}.csv`
  })
}
</script>

<style scoped>
.config {
  display: flex;
  align-items: flex-end;
  gap: var(--zy-space-6);
  flex-wrap: wrap;
}

.config__field {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-2);
}

.config__label {
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.config__actions {
  display: flex;
  gap: var(--zy-space-2);
  margin-left: auto;
}

.config__tip {
  margin-top: var(--zy-space-4);
}

.picker {
  width: 280px;
}

.slider {
  width: 200px;
}

.option-tag {
  float: right;
  color: var(--zy-text-tertiary);
  font-size: var(--zy-font-size-xs);
}

.summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--zy-space-3);
  margin-bottom: var(--zy-space-4);
}

.sub {
  margin-left: var(--zy-space-1);
  color: var(--zy-text-tertiary);
  font-size: var(--zy-font-size-xs);
}

@media screen and (max-width: 768px) {
  .picker,
  .slider {
    width: 100%;
  }

  .config__actions {
    margin-left: 0;
    width: 100%;
  }

  .summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

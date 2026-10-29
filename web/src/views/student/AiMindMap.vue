<template>
  <PageLayout title="AI 知识思维导图" description="输入任意知识主题，AI 自动梳理为结构化知识树。">
    <div class="topic">
      <el-input
        v-model="topic"
        placeholder="例如：Java 集合框架、TCP/IP 协议、数据库索引…"
        clearable
        maxlength="40"
        class="topic__input"
        @keyup.enter="generate()"
      />
      <el-button type="primary" :loading="generating" @click="generate()">生成导图</el-button>
      <el-button v-if="tree" @click="exportImage">下载图片</el-button>
    </div>

    <div class="samples">
      <span class="samples__label">试试：</span>
      <el-tag
        v-for="sample in SAMPLES"
        :key="sample"
        class="samples__tag"
        effect="plain"
        disable-transitions
        @click="generate(sample)"
      >
        {{ sample }}
      </el-tag>
    </div>

    <div v-loading="generating" element-loading-text="AI 正在构建知识体系…" class="map">
      <div v-show="tree" ref="chartRef" class="map__chart"></div>
      <el-empty v-if="!tree && !generating" description="还没有生成思维导图" />
      <p v-if="tree" class="map__hint">点击任意节点，AI 为你详细讲解该知识点</p>
    </div>

    <el-drawer v-model="detailVisible" :title="detailNode" size="42%">
      <div v-loading="detailLoading" element-loading-text="AI 正在讲解…" class="detail">
        <MarkdownView v-if="detailContent" :content="detailContent" size="compact" />
        <el-empty v-else-if="!detailLoading" description="暂无内容" />
      </div>
    </el-drawer>
  </PageLayout>
</template>

<script setup>
/** AI 知识思维导图（学生端） */
import { nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { MarkdownView, PageLayout } from '@/components/ui'
import { useChart } from '@/composables/useChart'
import { explainMindMapNode, generateMindMap } from '@/api/aiInsight'

const SAMPLES = [
  'Java 面向对象',
  '数据结构与算法',
  '计算机网络 TCP/IP',
  '数据库索引与优化',
  'HTTP 协议'
]

const topic = ref('')
const tree = ref(null)
const generating = ref(false)
const chartRef = ref(null)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailNode = ref('')
const detailContent = ref('')

/** 一级分支各用一种配色，让同一分支的连线和节点保持一致 */
const colorize = (root, palette) => {
  if (!root?.children) return root
  return {
    ...root,
    children: root.children.map((child, index) => {
      const color = palette.series[index % palette.series.length]
      return { ...child, lineStyle: { color }, itemStyle: { color } }
    })
  }
}

const { getInstance, resize } = useChart(
  chartRef,
  (palette) => ({
    tooltip: { trigger: 'item', triggerOn: 'mousemove' },
    series: [
      {
        type: 'tree',
        data: tree.value ? [colorize(tree.value, palette)] : [],
        top: '4%',
        left: '8%',
        bottom: '4%',
        right: '22%',
        symbol: 'circle',
        symbolSize: 10,
        orient: 'LR',
        expandAndCollapse: true,
        initialTreeDepth: 3,
        label: {
          position: 'left',
          verticalAlign: 'middle',
          align: 'right',
          fontSize: 14,
          color: palette.text,
          backgroundColor: palette.surface,
          padding: [4, 8],
          borderRadius: 4
        },
        leaves: { label: { position: 'right', verticalAlign: 'middle', align: 'left' } },
        lineStyle: { width: 1.5, curveness: 0.5 },
        emphasis: { focus: 'descendant' },
        animationDuration: 550,
        animationDurationUpdate: 600
      }
    ]
  }),
  { on: { click: (params) => explainNode(params) } }
)

const generate = async (preset) => {
  if (preset) topic.value = preset
  if (!topic.value.trim()) {
    ElMessage.warning('请输入主题')
    return
  }

  generating.value = true
  try {
    const res = await generateMindMap(topic.value.trim())
    if (!res?.data?.tree) {
      ElMessage.warning('生成失败，请换个主题重试')
      return
    }
    tree.value = res.data.tree
    // 容器此前是 display:none，尺寸为 0，显示后要重新测量
    await nextTick()
    resize()
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    generating.value = false
  }
}

const explainNode = async (params) => {
  const node = params?.data?.name
  if (!node) return

  // treeAncestors 含根节点自身，长度为 1 时点的就是根，不需要讲解
  const ancestors = (params.treeAncestors || []).map((a) => a.name).filter(Boolean)
  if (ancestors.length <= 1) return

  detailVisible.value = true
  detailLoading.value = true
  detailNode.value = node
  detailContent.value = ''

  try {
    const res = await explainMindMapNode(topic.value, node, ancestors.join(' / '))
    detailContent.value = res?.data?.content || ''
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    detailLoading.value = false
  }
}

const exportImage = () => {
  const instance = getInstance()
  if (!instance) return

  const link = document.createElement('a')
  link.href = instance.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#fff' })
  link.download = `${topic.value || '思维导图'}.png`
  link.click()
}
</script>

<style scoped>
.topic {
  display: flex;
  gap: var(--zy-space-2);
  margin-bottom: var(--zy-space-3);
}

.topic__input {
  max-width: 480px;
}

.samples {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--zy-space-2);
  margin-bottom: var(--zy-space-4);
}

.samples__label {
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-tertiary);
}

.samples__tag {
  cursor: pointer;
}

.map {
  position: relative;
  min-height: 56vh;
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-lg);
}

.map__chart {
  width: 100%;
  height: 60vh;
}

.map__hint {
  position: absolute;
  right: var(--zy-space-4);
  bottom: var(--zy-space-2);
  margin: 0;
  padding: 2px var(--zy-space-2);
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
  background: var(--zy-bg-scrim);
  border-radius: var(--zy-radius-sm);
}

.detail {
  min-height: 200px;
}

@media screen and (max-width: 768px) {
  .topic {
    flex-direction: column;
  }

  .topic__input {
    max-width: 100%;
  }
}
</style>

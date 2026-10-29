<!--
  导出作业讲评 PPT

  从 TeacherHomework 拆出：模板上传与下载触发只服务于这一个入口。
  从列表某一行进入时作业已确定，从页面顶部进入时需要先选作业。
-->
<template>
  <el-dialog
    :model-value="modelValue"
    title="导出讲解PPT"
    width="520px"
    :destroy-on-close="true"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="100px">
      <el-form-item v-if="!homeworkid" label="选择作业">
        <el-select v-model="pickedId" placeholder="请选择作业" class="full-width">
          <el-option
            v-for="item in homeworks"
            :key="item.homeworkid"
            :label="item.title"
            :value="item.homeworkid"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="样式">
        <el-radio-group v-model="form.style">
          <el-radio value="ai">AI风格</el-radio>
          <el-radio value="template">使用模板</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="form.style === 'template'" label="模板文件">
        <div class="upload">
          <input type="file" accept=".ppt,.pptx" @change="onTemplateChange" />
          <span v-if="form.templateFile" class="upload__name">已选择：{{ form.templateFile.name }}</span>
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" :disabled="!targetId" @click="run">生成PPT</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { exportHomeworkPpt } from '@/api/statistics'

/** 后端返回的相对路径要换成带鉴权的下载接口 */
const UPLOAD_PREFIX = '/upload/'
const DOWNLOAD_API = '/ZhiYanAcademy/files/download?path='

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 指定作业时不再显示选择框；为空则从 homeworks 里选 */
  homeworkid: { type: [Number, String], default: null },
  homeworks: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue'])

const loading = ref(false)
const pickedId = ref(null)
const form = reactive({ style: 'ai', templateFile: null })

const targetId = computed(() => props.homeworkid ?? pickedId.value)

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    form.style = 'ai'
    form.templateFile = null
    pickedId.value = props.homeworks[0]?.homeworkid ?? null
  }
)

const onTemplateChange = (event) => {
  const file = event?.target?.files?.[0]
  if (!file) {
    form.templateFile = null
    return
  }
  if (!/\.pptx?$/i.test(file.name)) {
    ElMessage.warning('仅支持 PPT 文件（.ppt 或 .pptx）')
    form.templateFile = null
    event.target.value = ''
    return
  }
  form.templateFile = file
}

const triggerDownload = (rawUrl) => {
  const url = rawUrl.startsWith(UPLOAD_PREFIX)
    ? `${DOWNLOAD_API}${encodeURIComponent(rawUrl.slice(UPLOAD_PREFIX.length))}`
    : rawUrl

  const link = document.createElement('a')
  link.href = url
  link.target = '_blank'
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

const run = async () => {
  loading.value = true
  try {
    const payload = new FormData()
    payload.append('homeworkid', targetId.value)
    payload.append('style', form.style)
    if (form.style === 'template' && form.templateFile) {
      payload.append('template', form.templateFile)
    }

    const res = await exportHomeworkPpt(payload)
    const rawUrl = res?.data?.url || res?.url
    if (!rawUrl) {
      ElMessage.error('生成失败')
      return
    }

    triggerDownload(rawUrl)
    ElMessage.success('PPT已生成')
    emit('update:modelValue', false)
  } catch (e) {
    console.error('导出讲评 PPT 失败:', e)
    ElMessage.error('生成失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.full-width {
  width: 100%;
}

.upload {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-2);
}

.upload__name {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-secondary);
}
</style>

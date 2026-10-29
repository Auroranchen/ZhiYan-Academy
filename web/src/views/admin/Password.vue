<template>
  <PageLayout
    title="修改密码"
    description="定期更换密码可降低账号被盗用的风险。"
    :breadcrumb="[{ label: '个人设置' }, { label: '修改密码' }]"
  >
    <SectionCard title="账号安全" class="panel">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="88px"
        class="form"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" show-password placeholder="请输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading">确认修改</el-button>
        </el-form-item>
      </el-form>
    </SectionCard>
  </PageLayout>
</template>

<script setup>
/** 管理员修改密码 */
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PageLayout, SectionCard } from '@/components/ui'
import { changeAdminPassword } from '@/api/system'

const formRef = ref(null)
const loading = ref(false)

const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const validateConfirm = (_rule, value, callback) => {
  if (value !== form.newPassword) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const validateDifferent = (_rule, value, callback) => {
  if (value && value === form.oldPassword) callback(new Error('新密码不能与旧密码相同'))
  else callback()
}

const rules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' },
    { validator: validateDifferent, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await changeAdminPassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    ElMessage.success('密码已修改')
    formRef.value?.resetFields()
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.panel {
  max-width: 520px;
}

.form {
  max-width: 400px;
}
</style>

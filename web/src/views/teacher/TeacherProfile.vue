<template>
  <PageLayout
    title="个人中心"
    description="维护个人资料、查看教学数据并修改登录密码。"
    :breadcrumb="[{ label: '个人设置' }, { label: '个人中心' }]"
  >
    <template #summary>
      <StatTile label="授课门数" :value="stats.courseCount" icon="ph:book-open-duotone" />
      <StatTile label="发布作业" :value="stats.homeworkCount" icon="ph:file-text-duotone" />
      <StatTile
        label="待批改"
        :value="stats.pendingCount"
        icon="ph:pencil-line-duotone"
        :tone="stats.pendingCount ? 'warning' : 'default'"
      />
      <StatTile label="在线考试" :value="stats.examCount" icon="ph:exam-duotone" />
    </template>

    <div class="panels">
      <SectionCard title="个人资料">
        <el-form
          ref="infoFormRef"
          :model="infoForm"
          :rules="infoRules"
          label-width="88px"
          class="form"
          @submit.prevent="saveInfo"
        >
          <el-form-item label="工号">
            <el-input v-model="infoForm.teacherno" disabled />
          </el-form-item>
          <el-form-item label="姓名" prop="teachername">
            <el-input v-model="infoForm.teachername" />
          </el-form-item>
          <el-form-item label="性别">
            <el-radio-group v-model="infoForm.sex">
              <el-radio value="男">男</el-radio>
              <el-radio value="女">女</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="联系电话">
            <el-input v-model="infoForm.phone" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="infoForm.email" />
          </el-form-item>
          <el-form-item label="职称">
            <el-input v-model="infoForm.title" />
          </el-form-item>
          <el-form-item label="所属系">
            <el-input v-model="infoForm.department" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" native-type="submit" :loading="savingInfo">保存修改</el-button>
          </el-form-item>
        </el-form>
      </SectionCard>

      <SectionCard title="修改密码">
        <el-form
          ref="pwdFormRef"
          :model="pwdForm"
          :rules="pwdRules"
          label-width="88px"
          class="form"
          @submit.prevent="savePassword"
        >
          <el-form-item label="旧密码" prop="oldPassword">
            <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 6 位" />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" native-type="submit" :loading="savingPwd">修改密码</el-button>
          </el-form-item>
        </el-form>
      </SectionCard>
    </div>
  </PageLayout>
</template>

<script setup>
/** 教师个人中心 */
import { onMounted, reactive, ref } from 'vue'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { PageLayout, SectionCard, StatTile } from '@/components/ui'
import { changeTeacherPassword, getTeacherInfo, updateTeacherProfile } from '@/api/teacher'
import { listCoursesByTeacher } from '@/api/course'
import { listHomeworkByCourse, listStudentHomeworkByTeacher } from '@/api/homework'
import { listTeacherExams } from '@/api/exam'

const store = useStore()

/* ---------- 个人资料 ---------- */

const infoFormRef = ref(null)
const savingInfo = ref(false)

const infoForm = reactive({
  teacherno: '',
  teachername: '',
  sex: '',
  phone: '',
  email: '',
  title: '',
  department: ''
})

const infoRules = {
  teachername: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const loadInfo = async () => {
  const res = await getTeacherInfo().catch(() => null)
  if (res?.data) Object.assign(infoForm, res.data)
}

const saveInfo = async () => {
  const valid = await infoFormRef.value?.validate().catch(() => false)
  if (!valid) return

  savingInfo.value = true
  try {
    await updateTeacherProfile(infoForm)
    ElMessage.success('已保存')
    // 顶栏显示的是 store 里的名字，改名后要同步，否则要刷新才生效
    store.commit('setUser', { ...store.state.user, teachername: infoForm.teachername })
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    savingInfo.value = false
  }
}

/* ---------- 修改密码 ---------- */

const pwdFormRef = ref(null)
const savingPwd = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const validateConfirm = (_rule, value, callback) => {
  if (value !== pwdForm.newPassword) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const validateDifferent = (_rule, value, callback) => {
  if (value && value === pwdForm.oldPassword) callback(new Error('新密码不能与旧密码相同'))
  else callback()
}

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' },
    { validator: validateDifferent, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

const savePassword = async () => {
  const valid = await pwdFormRef.value?.validate().catch(() => false)
  if (!valid) return

  savingPwd.value = true
  try {
    await changeTeacherPassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    ElMessage.success('密码已修改')
    pwdFormRef.value?.resetFields()
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    savingPwd.value = false
  }
}

/* ---------- 教学数据 ---------- */

const stats = reactive({ courseCount: 0, homeworkCount: 0, pendingCount: 0, examCount: 0 })

const loadStats = async () => {
  const teacherid = store.state.user?.teacherid
  if (!teacherid) return

  const courseRes = await listCoursesByTeacher(teacherid).catch(() => null)
  const courses = courseRes?.data || []
  stats.courseCount = courses.length

  // 原实现按课程串行请求且只统计前 20 门，这里改为全量并发
  const [perCourse, submissions, exams] = await Promise.all([
    Promise.all(
      courses.map((c) => listHomeworkByCourse(c.courseid).then((r) => r?.data?.length || 0).catch(() => 0))
    ),
    listStudentHomeworkByTeacher(teacherid, { page: 1, pageSize: 10000 }).catch(() => null),
    listTeacherExams({ page: 1, pageSize: 1 }).catch(() => null)
  ])

  stats.homeworkCount = perCourse.reduce((sum, n) => sum + n, 0)
  stats.pendingCount = (submissions?.data || []).filter((r) => Number(r.status) === 1).length
  stats.examCount = exams?.total || 0
}

onMounted(() => {
  loadInfo()
  loadStats()
})
</script>

<style scoped>
.panels {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
  gap: var(--zy-stack-gap);
  align-items: start;
}

.form {
  max-width: 420px;
}
</style>

<template>
  <PageLayout title="个人中心" description="维护个人资料、查看学习数据并管理账号安全。">
    <template #summary>
      <StatTile label="提交作业" :value="stats.homeworkCount" icon="ph:file-text-duotone" />
      <StatTile label="平均成绩" :value="stats.avgScore" icon="ph:medal-duotone" tone="brand" />
      <StatTile label="出勤率" :value="stats.attendRate" unit="%" icon="ph:calendar-check-duotone" />
      <StatTile label="在学课程" :value="stats.courseCount" icon="ph:book-open-duotone" />
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
          <el-form-item label="学号">
            <el-input v-model="infoForm.studentno" disabled />
          </el-form-item>
          <el-form-item label="姓名" prop="studentname">
            <el-input v-model="infoForm.studentname" />
          </el-form-item>
          <el-form-item label="班级">
            <el-input v-model="infoForm.classname" disabled />
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
          <el-form-item label="地址">
            <el-input v-model="infoForm.address" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" native-type="submit" :loading="savingInfo">保存修改</el-button>
          </el-form-item>
        </el-form>
      </SectionCard>

      <div class="panels__side">
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

        <SectionCard title="微信绑定">
          <template #action>
            <el-tag :type="wechatBound ? 'success' : 'info'" size="small" disable-transitions>
              {{ wechatBound ? '已绑定' : '未绑定' }}
            </el-tag>
          </template>

          <div v-loading="wechatLoading" class="wechat">
            <p class="wechat__tip">
              绑定与微信登录在智枢学堂小程序内完成，这里只能查看状态与解绑。解绑需要验证账号密码。
            </p>
            <div class="wechat__actions">
              <el-button :loading="wechatLoading" @click="loadWechatStatus">刷新状态</el-button>
              <el-button v-if="wechatBound" type="danger" plain @click="openUnbind">解绑微信</el-button>
            </div>
          </div>
        </SectionCard>
      </div>
    </div>

    <el-dialog v-model="unbindVisible" title="解绑微信" width="420px" destroy-on-close>
      <el-form label-width="88px" @submit.prevent="confirmUnbind">
        <el-form-item label="账号密码" required>
          <el-input
            v-model="unbindPassword"
            type="password"
            show-password
            placeholder="请输入当前学生账号密码"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="unbindVisible = false">取消</el-button>
        <el-button type="danger" :loading="unbinding" @click="confirmUnbind">确认解绑</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>

<script setup>
/** 学生个人中心 */
import { onMounted, reactive, ref } from 'vue'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { PageLayout, SectionCard, StatTile } from '@/components/ui'
import {
  changeStudentPassword,
  getStudentInfo,
  getWechatBindStatus,
  unbindWechat,
  updateStudentProfile
} from '@/api/student'
import { listStudentHomeworkByStudent } from '@/api/homework'
import { listCoursesByClass } from '@/api/course'
import { listAttendanceByStudent } from '@/api/attendance'
import { formatScore } from '@/utils/format'

const store = useStore()

/* ---------- 个人资料 ---------- */

const infoFormRef = ref(null)
const savingInfo = ref(false)

const infoForm = reactive({
  studentno: '',
  studentname: '',
  classname: '',
  sex: '',
  phone: '',
  email: '',
  address: ''
})

const infoRules = {
  studentname: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const loadInfo = async () => {
  const res = await getStudentInfo().catch(() => null)
  if (res?.data) Object.assign(infoForm, res.data)
}

const saveInfo = async () => {
  const valid = await infoFormRef.value?.validate().catch(() => false)
  if (!valid) return

  savingInfo.value = true
  try {
    await updateStudentProfile(infoForm)
    ElMessage.success('已保存')
    // 顶栏展示的是 store 里的名字，改名后同步一次，避免要刷新才生效
    store.commit('setUser', { ...store.state.user, studentname: infoForm.studentname })
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
    await changeStudentPassword({
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

/* ---------- 学习数据 ---------- */

const stats = reactive({ homeworkCount: 0, avgScore: '—', attendRate: '0.0', courseCount: 0 })

const loadStats = async () => {
  const { studentid, classid } = store.state.user || {}
  if (!studentid) return

  const [submissionRes, courseRes, attendanceRes] = await Promise.all([
    listStudentHomeworkByStudent(studentid, { page: 1, pageSize: 10000 }).catch(() => null),
    classid ? listCoursesByClass(classid).catch(() => null) : Promise.resolve(null),
    listAttendanceByStudent(studentid, { page: 1, pageSize: 10000 }).catch(() => null)
  ])

  const submissions = submissionRes?.data || []
  stats.homeworkCount = submissions.filter((s) => s.status >= 1).length

  const scored = submissions.filter((s) => s.score != null)
  stats.avgScore = scored.length
    ? formatScore(scored.reduce((sum, s) => sum + Number(s.score), 0) / scored.length)
    : '—'

  stats.courseCount = (courseRes?.data || []).length

  // 出勤率把迟到也算作到场，与考勤页口径保持一致
  const attendance = attendanceRes?.data || []
  if (attendance.length) {
    const present = attendance.filter((a) => a.status === 1 || a.status === 2).length
    stats.attendRate = ((present / attendance.length) * 100).toFixed(1)
  }
}

/* ---------- 微信绑定 ---------- */

const wechatBound = ref(false)
const wechatLoading = ref(false)
const unbindVisible = ref(false)
const unbinding = ref(false)
const unbindPassword = ref('')

const loadWechatStatus = async () => {
  wechatLoading.value = true
  try {
    const res = await getWechatBindStatus().catch(() => null)
    wechatBound.value = Boolean(res?.data?.bound)
  } finally {
    wechatLoading.value = false
  }
}

const openUnbind = () => {
  unbindPassword.value = ''
  unbindVisible.value = true
}

const confirmUnbind = async () => {
  if (!unbindPassword.value) {
    ElMessage.warning('请输入当前学生账号密码')
    return
  }

  unbinding.value = true
  try {
    await unbindWechat(unbindPassword.value)
    ElMessage.success('微信已解绑')
    unbindVisible.value = false
    wechatBound.value = false
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    unbinding.value = false
  }
}

onMounted(() => {
  loadInfo()
  loadStats()
  loadWechatStatus()
})
</script>

<style scoped>
.panels {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--zy-stack-gap);
  align-items: start;
}

.panels__side {
  display: flex;
  flex-direction: column;
  gap: var(--zy-stack-gap);
}

.form {
  max-width: 420px;
}

.wechat__tip {
  margin: 0 0 var(--zy-space-4);
  font-size: var(--zy-font-size-sm);
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-tertiary);
}

.wechat__actions {
  display: flex;
  gap: var(--zy-space-2);
}

@media screen and (max-width: 1024px) {
  .panels {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>

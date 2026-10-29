<template>
  <!-- 登录页面 -->
  <div class="login-page">
    <div class="login-card">
      <!-- 顶部品牌区域 -->
      <div class="login-brand">
        <img src="@/assets/images/logo.png" alt="logo" class="brand-logo" />
        <h1 class="brand-name">智枢学堂</h1>
        <p class="brand-sub">Agentic Learning Cloud</p>
      </div>

      <!-- 账号由学校统一开通，关闭匿名自助注册 -->
      <div class="mode-tabs">
        <button :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
        <span class="account-tip">学生账号请联系管理员开通</span>
      </div>

      <!-- 登录表单 -->
      <transition name="form-switch" mode="out-in">
        <el-form v-if="mode === 'login'" key="login" ref="loginFormRef" :model="loginForm" :rules="loginRules"
          class="login-form" hide-required-asterisk>
          <el-form-item prop="username">
            <el-input v-model="loginForm.username" :placeholder="usernamePlaceholder" size="large" clearable>
              <template #prefix><el-icon>
                  <User />
                </el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" size="large" show-password>
              <template #prefix><el-icon>
                  <Lock />
                </el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="captcha">
            <div class="captcha-row">
              <el-input v-model="loginForm.captcha" placeholder="请输入验证码" size="large" maxlength="4" clearable
                @keyup.enter="handleLogin">
                <template #prefix><el-icon>
                    <Key />
                  </el-icon></template>
              </el-input>
              <div class="captcha-box" title="点击刷新验证码" @click="generateCaptcha">{{ captchaCode }}</div>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="handleLogin">
              {{ loading ? '登录中...' : '登 录' }}
            </el-button>
          </el-form-item>
          <div class="login-hint">支持教务人员、教师工号、学生学号登录</div>
        </el-form>

        <!-- 注册表单 -->
        <el-form v-else key="register" ref="registerFormRef" :model="registerForm" :rules="registerRules"
          class="login-form" hide-required-asterisk>
          <el-form-item prop="studentno">
            <el-input v-model="registerForm.studentno" placeholder="请输入学号" size="large">
              <template #prefix><el-icon>
                  <User />
                </el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="studentname">
            <el-input v-model="registerForm.studentname" placeholder="请输入姓名" size="large">
              <template #prefix><el-icon>
                  <User />
                </el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="registerForm.password" type="password" placeholder="请输入密码（至少6位）" size="large"
              show-password>
              <template #prefix><el-icon>
                  <Lock />
                </el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="majorid">
            <el-select v-model="registerForm.majorid" placeholder="请选择专业" style="width: 100%" size="large"
              @change="onMajorChange">
              <el-option v-for="item in majorList" :key="item.majorid" :label="item.majorname" :value="item.majorid" />
            </el-select>
          </el-form-item>
          <el-form-item prop="classid">
            <el-select v-model="registerForm.classid" placeholder="请选择班级" style="width: 100%" size="large">
              <el-option v-for="item in classList" :key="item.classid" :label="item.classname" :value="item.classid" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="handleRegister">
              {{ loading ? '注册中...' : '注 册' }}
            </el-button>
          </el-form-item>
        </el-form>
      </transition>
    </div>

    <!-- 底部版权 -->
    <div class="login-footer">© 2027 智枢学堂 · 大模型学习多智能体平台</div>

    <!-- 滑动安全验证弹窗 -->
    <el-dialog v-model="captchaVisible" width="372px" align-center append-to-body :close-on-click-modal="false"
      :show-close="true" class="captcha-dialog" @closed="onCaptchaClosed">
      <template #header>
        <div class="cap-dlg-header">
          <el-icon class="cap-dlg-shield">
            <Lock />
          </el-icon>
          <span>安全验证</span>
        </div>
      </template>
      <div class="cap-dlg-tip">拖动滑块完成拼图，验证后自动登录</div>
      <div class="cap-dlg-body">
        <SliderCaptcha v-if="captchaVisible" ref="sliderRef" @success="onCaptchaSuccess" @fail="onCaptchaFail" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { User, Lock, Key } from '@element-plus/icons-vue'
import { login, registerStudent } from '@/api/auth'
import { listClassesByMajor } from '@/api/classes'
import { listAllMajors } from '@/api/major'
import SliderCaptcha from '@/components/SliderCaptcha.vue'

/** 账号密码 + 图形验证码 + 滑动验证的两步登录，兼学生自助注册 */

/** 图形验证码去掉了易混淆的 0O1I */
const CAPTCHA_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
const CAPTCHA_LENGTH = 4
/** 后端限流的返回码，提示语气要缓和一些 */
const CODE_TOO_MANY_REQUESTS = 429

const router = useRouter()
const store = useStore()

const mode = ref('login')
const loading = ref(false)
const loginFormRef = ref(null)
const registerFormRef = ref(null)

const majorList = ref([])
const classList = ref([])

const loginForm = reactive({ username: '', password: '', captcha: '' })
const registerForm = reactive({ studentno: '', studentname: '', password: '', majorid: null, classid: null })

const usernamePlaceholder = computed(() => '请输入账号')

// 图形验证码（客户端校验，防误触/脚本）
const captchaCode = ref('')
const generateCaptcha = () => {
  captchaCode.value = Array.from(
    { length: CAPTCHA_LENGTH },
    () => CAPTCHA_CHARS[Math.floor(Math.random() * CAPTCHA_CHARS.length)]
  ).join('')
}

// 滑动验证（点击登录后弹窗）
const sliderRef = ref(null)
const captchaVisible = ref(false)
const captchaPassed = ref(false)
const onCaptchaFail = () => { }
const onCaptchaClosed = () => {
  // 关闭弹窗时如未验证通过，则取消本次登录的 loading 状态
  if (!captchaPassed.value) loading.value = false
  captchaPassed.value = false
}

const loginRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captcha: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (!value || value.toUpperCase() !== captchaCode.value) {
          callback(new Error('验证码错误'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const registerRules = {
  studentno: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  studentname: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  majorid: [{ required: true, message: '请选择专业', trigger: 'change' }],
  classid: [{ required: true, message: '请选择班级', trigger: 'change' }]
}

const loadMajors = async () => {
  try {
    const res = await listAllMajors()
    majorList.value = res.data || []
  } catch (error) {
    console.error('加载专业失败:', error)
  }
}

// 自助注册目前由学校策略关闭，切到注册态时才拉专业列表，
// 避免绝大多数只登录的用户白白多发一次请求
watch(mode, (value) => {
  if (value === 'register' && !majorList.value.length) loadMajors()
})

const onMajorChange = async (majorid) => {
  registerForm.classid = null
  try {
    const res = await listClassesByMajor(majorid)
    classList.value = res.data || []
  } catch (error) {
    console.error('加载班级失败:', error)
  }
}

// 第一步：校验账号密码表单，通过后弹出滑动验证窗口
const handleLogin = async () => {
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return
  captchaPassed.value = false
  captchaVisible.value = true
}

/** 一次登录失败后要换一张图形验证码，防脚本重放 */
const resetCaptchaInput = () => {
  loginForm.captcha = ''
  generateCaptcha()
}

// 第二步：滑动验证通过，携带一次性 token 真正登录
const onCaptchaSuccess = async (token) => {
  captchaPassed.value = true
  // 稍作停留展示「验证通过」动效后关闭弹窗
  setTimeout(() => { captchaVisible.value = false }, 350)
  loading.value = true
  try {
    const res = await login({
      username: loginForm.username,
      password: loginForm.password,
      captchaToken: token
    })

    if (res.code === 200 && res.data) {
      store.dispatch('login', res.data)
      ElMessage.success('登录成功')
      router.push(`/${res.data.role}/home`)
      return
    }
    ElMessage.error(res.msg || '登录失败')
    resetCaptchaInput()
  } catch (error) {
    // 后端通过 code 区分：423 账号锁定、429 请求过于频繁、412 验证失效
    const msg = error?.msg || error?.message || '账号或密码错误'
    if (error?.code === CODE_TOO_MANY_REQUESTS) ElMessage.warning(msg)
    else ElMessage.error(msg)
    resetCaptchaInput()
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  const valid = await registerFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await registerStudent(registerForm)
    ElMessage.success('注册成功，请登录')
    mode.value = 'login'
    loginForm.username = registerForm.studentno
  } catch (error) {
    console.error('注册失败:', error)
  } finally {
    loading.value = false
  }
}

onMounted(generateCaptcha)
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #1a1a2e;
  background-image: url('@/assets/images/login-bg.jpg');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  padding: 20px;
  position: relative;
}

/* 背景遮罩层 */
.login-page::before {
  content: '';
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.55);
  z-index: 0;
}

.login-page>* {
  position: relative;
  z-index: 1;
}

.login-card {
  width: 100%;
  max-width: 420px;
  /* 必须由令牌派生：写死 rgba(255,255,255,.95) 的话，暗色主题下
     卡片仍是白的，而里面的输入框已经跟着 --el-bg-color 变黑，
     就成了「黑框白卡」。 */
  background: color-mix(in srgb, var(--zy-bg-surface) 95%, transparent);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: var(--zy-radius-xl);
  padding: 40px 36px 32px;
  box-shadow: var(--zy-shadow-xl);
}

/* 品牌区域 */
.login-brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 32px;
  padding-top: 4px;
}

.brand-logo {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  object-fit: cover;
  margin-bottom: 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.brand-name {
  font-size: 22px;
  font-weight: 700;
  color: var(--zy-text-primary);
  margin: 0;
  line-height: 1.3;
  letter-spacing: 1px;
}

.brand-sub {
  font-size: 12px;
  font-family: var(--zy-font-mono);
  color: var(--zy-text-tertiary);
  margin: 4px 0 0 0;
  letter-spacing: 0.5px;
}

/* 切换标签 */
.mode-tabs {
  display: flex;
  gap: 0;
  margin-bottom: 28px;
  background: #f1f5f9;
  border-radius: 10px;
  padding: 4px;
}

.mode-tabs button {
  flex: 1;
  padding: 10px 0;
  border: none;
  background: transparent;
  font-size: 14px;
  font-weight: 500;
  color: #64748b;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.mode-tabs button.active {
  background: #fff;
  color: #0f172a;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.mode-tabs button:hover:not(.active) {
  color: #334155;
}

.account-tip {
  flex: 1.5;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
  font-size: 12px;
}

/* 表单 */
.login-form {
  width: 100%;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 10px;
  padding: 4px 12px;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  transition: box-shadow 0.15s ease;
}

.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #cbd5e1 inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px #0ea5e9 inset;
}

.login-form :deep(.el-input__prefix .el-icon) {
  color: #94a3b8;
}

/* 图形验证码 */
.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.captcha-row .el-input {
  flex: 1;
}

.captcha-box {
  width: 110px;
  height: 40px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: 6px;
  color: #fff;
  cursor: pointer;
  user-select: none;
  font-family: var(--zy-font-mono);
  font-style: italic;
  text-shadow: 1px 1px 2px rgba(0, 0, 0, 0.3);
  /* 原来是青→靛→紫的自造渐变，不属于品牌色 */
  background: linear-gradient(135deg, var(--zy-brand-hover) 0%, var(--zy-brand-base) 100%);
  background-size: 200% 200%;
  transition: filter 0.15s ease;
}

.captcha-box:hover {
  filter: brightness(1.08);
}

/* 提交按钮 */
.submit-btn {
  width: 100%;
  height: 46px;
  font-size: 15px;
  font-weight: 600;
  border-radius: var(--zy-radius-control) !important;
  /* 原来起点是青色 #0ea5e9，与品牌蓝不一致 */
  background: var(--zy-brand-base) !important;
  border: none !important;
  letter-spacing: 4px;
  box-shadow: 0 6px 16px color-mix(in srgb, var(--zy-brand-base) 32%, transparent);
  transition: transform var(--zy-duration-fast) var(--zy-ease-standard),
              box-shadow var(--zy-duration-fast) var(--zy-ease-standard),
              background-color var(--zy-duration-fast) var(--zy-ease-standard);
}

.submit-btn:hover {
  background: var(--zy-brand-hover) !important;
  transform: translateY(-1px);
  box-shadow: 0 8px 20px color-mix(in srgb, var(--zy-brand-base) 40%, transparent);
}

.submit-btn:active {
  transform: translateY(0);
}

/* 滑动验证弹窗 */
.captcha-dialog :deep(.el-dialog) {
  border-radius: 16px;
  overflow: hidden;
  padding: 0;
}

.captcha-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 16px 20px;
  border-bottom: 1px solid #f1f5f9;
}

.captcha-dialog :deep(.el-dialog__body) {
  padding: 18px 20px 22px;
}

.cap-dlg-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
}

.cap-dlg-shield {
  color: #0ea5e9;
  font-size: 18px;
}

.cap-dlg-tip {
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 14px;
  text-align: center;
}

.cap-dlg-body {
  display: flex;
  justify-content: center;
}

/* 提示文字 */
.login-hint {
  text-align: center;
  font-size: 12px;
  color: #94a3b8;
  margin-top: -4px;
}

/* 表单切换动画 */
.form-switch-enter-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.form-switch-leave-active {
  transition: opacity 0.15s ease;
}

.form-switch-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.form-switch-leave-to {
  opacity: 0;
}

/* 底部 */
.login-footer {
  margin-top: 24px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

/* 移动端 */
@media screen and (max-width: 768px) {
  .login-page {
    padding: 16px;
  }

  .login-card {
    padding: 28px 24px 24px;
    border-radius: 12px;
  }

  .brand-logo {
    width: 52px;
    height: 52px;
    border-radius: 14px;
  }

  .brand-name {
    font-size: 19px;
  }
}
</style>

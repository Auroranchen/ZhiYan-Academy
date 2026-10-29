<template>
  <div class="lp">
    <a class="lp-skip" href="#top">跳到主要内容</a>

    <!-- 顶部公告条 -->
    <div class="lp-topbar">
      <span class="tb-dot"></span>
      <span>智枢学堂 v4.0· 大模型驱动的学习多智能体系统全新发布</span>
      <span class="tb-sep">/</span>
      <button class="lp-link" @click="openApply">高校合作通道开放中 →</button>
    </div>

    <!-- ===== 导航 ===== -->
    <header class="lp-nav" :class="{ scrolled }">
      <div class="lp-nav__inner">
        <a class="lp-brand" href="#top" @click.prevent="scrollTo('top')">
          <img src="@/assets/images/logo.png" alt="智枢学堂" class="lp-brand__logo" />
          <div class="lp-brand__txt">
            <span class="lp-brand__name">智枢学堂</span>
            <span class="lp-brand__sub">AGENTIC LEARNING CLOUD</span>
          </div>
        </a>
        <nav class="lp-nav__links">
          <a v-for="l in NAV_LINKS" :key="l.id" :href="`#${l.id}`" @click.prevent="scrollTo(l.id)">{{ l.label }}</a>
        </nav>
        <div class="lp-nav__actions">
          <button class="btn btn-ghost" @click="goLogin">{{ enterLabel }}</button>
          <button class="btn btn-brand" @click="openApply">申请入驻</button>
          <button
            class="lp-nav__burger"
            aria-label="打开导航菜单"
            :aria-expanded="navOpen"
            @click="navOpen = true"
          >
            <Icon icon="ph:list-bold" />
          </button>
        </div>
      </div>
    </header>

    <!-- ===== 英雄区 ===== -->
    <section id="top" class="hero">
      <div class="hero__grid"></div>
      <div class="hero__glow"></div>
      <div class="hero__grain"></div>
      <div class="hero__watermark">智学</div>

      <div class="hero__inner">
        <div class="hero__text">
          <div class="kicker reveal">// LLM-NATIVE MULTI-AGENT SYSTEM</div>
          <h1 class="hero__title reveal" style="--d:.05s">
            让 <span class="mark">多智能体</span><br />
            协作生成，<br />
            每个人的<span class="ul">专属学习资源</span>
          </h1>
          <p class="hero__desc reveal" style="--d:.12s">
            基于大模型的个性化资源生成与学习多智能体系统——诊断、规划、资源、思维导图、出题、拓展阅读、代码实操、编排
            八大智能体协同工作，依据真实学情与「对话式学习画像」一键产出含
            <b>讲义 / 思维导图 / 练习 / 拓展阅读 / 代码实操 5 类资源</b>的个性化学习包，
            内置防幻觉与内容安全机制，并融合查重代写识别、在线考试、学情周报与游戏化激励，贯通教与学全链路。
          </p>
          <div class="hero__cta reveal" style="--d:.2s">
            <button class="btn btn-brand btn-lg" @click="goLogin">
              <Icon icon="ph:arrow-right-bold" /> {{ isLoggedIn ? '进入控制台' : '立即登录体验' }}
            </button>
            <button class="btn btn-line btn-lg" @click="openApply">
              <Icon icon="ph:buildings-duotone" /> 高校合作入驻
            </button>
          </div>
          <div class="hero__mini reveal" style="--d:.28s">
            <div class="mini"><b>8</b><span>协作智能体</span></div>
            <div class="mini"><b>5</b><span>类个性化资源</span></div>
            <div class="mini"><b>22+</b><span>功能模块</span></div>
            <div class="mini"><b>私有化</b><span>可部署</span></div>
          </div>
        </div>

        <div class="hero__visual reveal" style="--d:.18s">
          <div class="shot">
            <div class="shot__bar">
              <span class="tl tl-r"></span><span class="tl tl-y"></span><span class="tl tl-g"></span>
              <em>agentic-learning.cloud</em>
            </div>
            <div class="shot__tag">/ 学习多智能体工作台</div>
            <img :src="heroShot" alt="学习多智能体工作台" />
            <div class="shot__corner">01</div>
          </div>
          <div class="float-pill fp-a"><Icon icon="ph:stethoscope-duotone" /> 诊断智能体</div>
          <div class="float-pill fp-b"><Icon icon="ph:book-open-text-duotone" /> 资源智能体</div>
          <div class="float-pill fp-c"><Icon icon="ph:stack-duotone" /> 编排智能体</div>
        </div>
      </div>
    </section>

    <!-- ===== 关键词跑马灯 ===== -->
    <div class="marquee">
      <div class="marquee__track">
        <span v-for="(k, i) in marqueeKeys" :key="'m'+i" class="mq-item">{{ k }}<i>✦</i></span>
        <span v-for="(k, i) in marqueeKeys" :key="'m2'+i" class="mq-item">{{ k }}<i>✦</i></span>
      </div>
    </div>

    <!-- ===== 数据条（夸张数据 + 数字滚动） ===== -->
    <section ref="statsRef" class="stats">
      <div class="stats__head">[ PLATFORM IN NUMBERS ]</div>
      <div class="stats__inner">
        <div v-for="(s, i) in stats" :key="s.label" class="stat">
          <div class="stat__num">{{ statView[i] }}<span>{{ s.suffix }}</span></div>
          <div class="stat__mono">{{ s.mono }}</div>
          <div class="stat__label">{{ s.label }}</div>
        </div>
      </div>
    </section>

    <!-- ===== 学习多智能体系统 ===== -->
    <section id="agents" class="sec sec-dark">
      <div class="sec-head reveal">
        <div class="sec-kicker brand">// MULTI-AGENT ENGINE</div>
        <h2 class="sec-title light">八大智能体协同，<span class="mark">自动编排</span>个性化学习资源</h2>
        <p class="sec-sub">区别于单点 AI 功能，平台以「多智能体编排」为内核：每个智能体各司其职、依次接力，从一份真实学情与对话式画像出发，自动生成含 5 类资源的完整学习资源包，全程实时日志可视化。</p>
      </div>

      <!-- 流水线 -->
      <div class="agents-flow reveal">
        <template v-for="(a, i) in agents" :key="a.t">
          <div class="aflow">
            <div class="aflow__ic" :style="{ '--c': a.c }"><Icon :icon="a.icon" /></div>
            <div class="aflow__no">AGENT 0{{ i + 1 }}</div>
            <h3>{{ a.t }}</h3>
            <p>{{ a.d }}</p>
            <div class="aflow__io"><span>IN</span> {{ a.in }}</div>
            <div class="aflow__io out"><span>OUT</span> {{ a.out }}</div>
          </div>
          <div v-if="i < agents.length - 1" class="aflow-arrow"><Icon icon="ph:arrow-right-bold" /></div>
        </template>
      </div>

      <div class="agents-cta reveal">
        <div class="agents-cta__txt">
          <Icon icon="ph:sparkle-duotone" />
          一次点击，五个智能体接力工作，约 1 分钟产出「诊断报告 + 学习路径 + 知识讲义 + 针对练习」并自动入库。
        </div>
        <button class="btn btn-brand" @click="goLogin">
          <Icon icon="ph:play-duotone" /> 进入工作台体验
        </button>
      </div>
    </section>

    <!-- ===== 核心能力（编号交错展示） ===== -->
    <section id="features" class="sec sec-light">
      <div class="sec-head reveal">
        <div class="sec-kicker">// CORE CAPABILITIES</div>
        <h2 class="sec-title">六大旗舰 AI 能力，<br class="mb-br" />重塑教与学的每一环</h2>
      </div>

      <div
        v-for="(f, i) in features" :key="f.title"
        class="frow reveal" :class="{ rev: i % 2 === 1 }"
      >
        <div class="frow__media">
          <div class="frame">
            <div class="frame__bar"><span></span><span></span><span></span></div>
            <img :src="f.img" :alt="f.title" :width="f.w" :height="f.h" loading="lazy" />
          </div>
          <div class="frame__idx">{{ String(i + 1).padStart(2, '0') }}</div>
        </div>
        <div class="frow__text">
          <div class="frow__no">{{ String(i + 1).padStart(2, '0') }} / {{ String(features.length).padStart(2, '0') }}</div>
          <h3>{{ f.title }}</h3>
          <p>{{ f.desc }}</p>
          <ul>
            <li v-for="b in f.bullets" :key="b"><i></i>{{ b }}</li>
          </ul>
        </div>
      </div>

      <!-- 更多 AI 能力（紧凑网格） -->
      <div class="more-head reveal">
        <div class="sec-kicker">// AND MORE</div>
        <h3>还有更多 AI 学习闭环能力</h3>
      </div>
      <div class="more-grid">
        <div v-for="(m, i) in moreFeatures" :key="m.title" class="more-card reveal" :style="{ '--d': (i % 3) * 0.06 + 's' }">
          <div class="more-card__ic"><Icon :icon="m.icon" /></div>
          <h4>{{ m.title }}</h4>
          <p>{{ m.desc }}</p>
          <div class="more-card__tags">
            <span v-for="t in m.tags" :key="t">{{ t }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 能力全景矩阵 ===== -->
    <section id="matrix" class="sec sec-dark">
      <div class="sec-head reveal">
        <div class="sec-kicker brand">// FULL CAPABILITY MAP</div>
        <h2 class="sec-title light">一个平台，<span class="mark">二十+</span> 核心模块</h2>
        <p class="sec-sub">从教学、考试、学情洞察、协作互评到数据与安全，覆盖高校数字化教学的方方面面。</p>
      </div>
      <div class="matrix">
        <div v-for="(m, i) in matrix" :key="m.t" class="mcard reveal" :style="{ '--d': (i % 4) * 0.05 + 's' }">
          <div class="mcard__ic"><Icon :icon="m.icon" /></div>
          <h3>{{ m.t }}</h3>
          <p>{{ m.d }}</p>
        </div>
      </div>
    </section>

    <!-- ===== 商业价值 ===== -->
    <section id="value" class="sec sec-light">
      <div class="sec-head reveal">
        <div class="sec-kicker">// BUSINESS VALUE</div>
        <h2 class="sec-title">为高校创造可量化的价值</h2>
      </div>
      <div class="vgrid">
        <div v-for="(v, i) in values" :key="v.title" class="vcard reveal" :style="{ '--d': i * 0.06 + 's' }">
          <div class="vcard__no">0{{ i + 1 }}</div>
          <div class="vcard__ic"><Icon :icon="v.icon" /></div>
          <h3>{{ v.title }}</h3>
          <p>{{ v.desc }}</p>
        </div>
      </div>
    </section>

    <!-- ===== 入驻流程 ===== -->
    <section id="join" class="sec sec-light pt0">
      <div class="sec-head reveal">
        <div class="sec-kicker">// GET STARTED</div>
        <h2 class="sec-title">四步开启智慧教学</h2>
      </div>
      <div class="steps">
        <div v-for="(st, i) in steps" :key="st.t" class="step reveal" :style="{ '--d': i * 0.06 + 's' }">
          <div class="step__no">0{{ i + 1 }}</div>
          <div class="step__ic"><Icon :icon="st.icon" /></div>
          <h3>{{ st.t }}</h3>
          <p>{{ st.d }}</p>
        </div>
      </div>
    </section>

    <!-- ===== CTA ===== -->
    <section class="cta">
      <div class="cta__grain"></div>
      <div class="cta__inner reveal">
        <div class="kicker brand center">// READY?</div>
        <h2>让贵校的课堂，<br />进入 AI 时代</h2>
        <p>立即登录体验，或预约一次面向贵校的专属方案演示。</p>
        <div class="cta__btns">
          <button class="btn btn-brand btn-lg" @click="goLogin"><Icon icon="ph:sign-in-duotone" /> {{ enterLabel }}</button>
          <button class="btn btn-line btn-lg" @click="openApply"><Icon icon="ph:paper-plane-tilt-duotone" /> 申请入驻 / 预约演示</button>
        </div>
      </div>
    </section>

    <!-- ===== 页脚 ===== -->
    <footer class="foot">
      <div class="foot__inner">
        <div class="foot__brand">
          <img src="@/assets/images/logo.png" alt="智枢学堂" />
          <div>
            <div class="foot__name">智枢学堂</div>
            <div class="foot__slogan">基于大模型的个性化资源生成与学习多智能体平台</div>
          </div>
        </div>
        <div class="foot__links">
          <a href="#features" @click.prevent="scrollTo('features')">核心能力</a>
          <a href="#matrix" @click.prevent="scrollTo('matrix')">能力全景</a>
          <a href="#value" @click.prevent="scrollTo('value')">商业价值</a>
          <button class="lp-link" @click="goLogin">{{ enterLabel }}</button>
        </div>
      </div>
      <div class="foot__copy">© 2027 智枢学堂 · AGENTIC LEARNING CLOUD · 保留所有权利</div>
    </footer>

    <!-- ===== 移动端导航抽屉（≤1000px 汉堡打开） ===== -->
    <el-drawer v-model="navOpen" direction="rtl" size="78%" :with-header="false" class="lp-drawer">
      <nav class="lp-drawer__nav">
        <a v-for="l in NAV_LINKS" :key="l.id" :href="`#${l.id}`" @click.prevent="jumpTo(l.id)">{{ l.label }}</a>
      </nav>
      <div class="lp-drawer__actions">
        <button class="btn btn-ghost" @click="jumpLogin">{{ enterLabel }}</button>
        <button class="btn btn-brand" @click="jumpApply">申请入驻</button>
      </div>
    </el-drawer>

    <!-- ===== 申请入驻弹窗 ===== -->
    <el-dialog v-model="applyVisible" title="申请高校入驻 / 预约演示" width="520px" align-center class="lp-dialog" destroy-on-close @closed="resetApplySecurity">
      <el-form ref="applyRef" :model="applyForm" :rules="applyRules" label-position="top">
        <el-form-item label="学校 / 机构名称" prop="school">
          <el-input v-model="applyForm.school" placeholder="如：示例大学计算机学院" />
        </el-form-item>
        <div class="lp-dialog__row">
          <el-form-item label="联系人" prop="name">
            <el-input v-model="applyForm.name" placeholder="您的姓名" />
          </el-form-item>
          <el-form-item label="职务" prop="role">
            <el-input v-model="applyForm.role" placeholder="如：教务处/教师" />
          </el-form-item>
        </div>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="applyForm.phone" placeholder="便于我们与您联系" maxlength="11" />
        </el-form-item>
        <el-form-item label="需求说明">
          <el-input v-model="applyForm.remark" type="textarea" :rows="3" placeholder="师生规模、关注的功能、期望时间等（选填）" />
        </el-form-item>
        <el-form-item label="需求文档（选填）">
          <el-upload
            class="lp-upload"
            :auto-upload="false"
            :limit="1"
            accept=".doc,.docx,.pdf"
            :file-list="applyFileList"
            :on-change="onApplyFileChange"
            :on-remove="onApplyFileRemove"
            :on-exceed="onApplyFileExceed"
          >
            <el-button plain><Icon icon="ph:paperclip" style="margin-right:6px" /> 选择文件</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 Word（.doc / .docx）或 PDF，大小不超过 10MB</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="安全验证" required>
          <SliderCaptcha @success="onApplyCaptchaSuccess" @fail="onApplyCaptchaFail" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="applying" @click="submitApply">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { Icon } from '@iconify/vue'
import { ElMessage } from 'element-plus'
import { submitEnrollApplication, uploadEnrollFile } from '@/api/system'
import SliderCaptcha from '@/components/SliderCaptcha.vue'
import {
  agents,
  features,
  heroShot,
  marqueeKeys,
  matrix,
  moreFeatures,
  stats,
  steps,
  values
} from '@/config/landing'
import { ROLE_HOME } from '@/config/menus'

/** 官网首页：产品介绍 + 入驻申请 */

/** 数字滚动动画时长 */
const COUNT_DURATION = 1500
/** 顶部导航变色的滚动阈值 */
const NAV_SCROLL_THRESHOLD = 30
/** 需求文档大小上限，与后端一致 */
const APPLY_FILE_MAX_BYTES = 10 * 1024 * 1024

/** 导航锚点。id 必须与各区块的 id 一致，否则 scrollTo 会退化为滚回顶部 */
const NAV_LINKS = [
  { id: 'agents', label: '多智能体' },
  { id: 'features', label: '核心能力' },
  { id: 'matrix', label: '能力全景' },
  { id: 'value', label: '商业价值' },
  { id: 'join', label: '高校入驻' }
]

const router = useRouter()
const store = useStore()
const scrolled = ref(false)
/** 移动端导航抽屉是否打开 */
const navOpen = ref(false)

const isLoggedIn = computed(() => store.getters.isLoggedIn && !!store.state.role)
const roleHome = computed(() => ROLE_HOME[store.state.role] || '/login')
const enterLabel = computed(() => (isLoggedIn.value ? '进入控制台' : '登录'))

const statView = ref(stats.map(() => '0'))
let counted = false

/** 数字从 0 滚到目标值，缓出曲线，只跑一次 */
const runCount = () => {
  if (counted) return
  counted = true
  const start = performance.now()
  const tick = (now) => {
    const progress = Math.min(1, (now - start) / COUNT_DURATION)
    const eased = 1 - Math.pow(1 - progress, 3)
    statView.value = stats.map((s) => {
      const value = s.target * eased
      return s.decimals ? value.toFixed(s.decimals) : Math.round(value).toLocaleString('en-US')
    })
    if (progress < 1) requestAnimationFrame(tick)
  }
  requestAnimationFrame(tick)
}

const goLogin = () => router.push(isLoggedIn.value ? roleHome.value : '/login')

const scrollTo = (id) => {
  const el = document.getElementById(id)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  else window.scrollTo({ top: 0, behavior: 'smooth' })
}

/* 抽屉内的跳转：一律先收起抽屉，避免抽屉关闭动画与滚动动画互相干扰 */

const jumpTo = (id) => {
  navOpen.value = false
  scrollTo(id)
}

const jumpLogin = () => {
  navOpen.value = false
  goLogin()
}

const jumpApply = () => {
  navOpen.value = false
  openApply()
}

/* ---------- 申请入驻 ---------- */

const applyVisible = ref(false)
const applying = ref(false)
const applyRef = ref(null)
const applyCaptchaToken = ref('')
const applyForm = reactive({ school: '', name: '', role: '', phone: '', remark: '' })
const applyRules = {
  school: [{ required: true, message: '请填写学校 / 机构名称', trigger: 'blur' }],
  name: [{ required: true, message: '请填写联系人', trigger: 'blur' }],
  phone: [
    { required: true, message: '请填写手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号', trigger: 'blur' }
  ]
}

const openApply = () => {
  applyCaptchaToken.value = ''
  applyVisible.value = true
}
const onApplyCaptchaSuccess = (token) => { applyCaptchaToken.value = token || '' }
const onApplyCaptchaFail = () => { applyCaptchaToken.value = '' }
const resetApplySecurity = () => { applyCaptchaToken.value = '' }

// 需求文档上传（Word / PDF，最大 10MB）
const applyFileList = ref([])
const onApplyFileChange = (file) => {
  if (!/\.(docx?|pdf)$/i.test(file.name || '')) {
    ElMessage.warning('仅支持 Word（.doc/.docx）或 PDF 文档')
    applyFileList.value = []
    return
  }
  if (file.size > APPLY_FILE_MAX_BYTES) {
    ElMessage.warning('文件大小不能超过 10MB')
    applyFileList.value = []
    return
  }
  applyFileList.value = [file]
}
const onApplyFileRemove = () => { applyFileList.value = [] }
const onApplyFileExceed = (files) => {
  // 超出 limit 时用新文件替换旧文件
  const [picked] = files || []
  if (picked) onApplyFileChange({ name: picked.name, size: picked.size, raw: picked })
}

const submitApply = async () => {
  const valid = await applyRef.value.validate().catch(() => false)
  if (!valid) return
  if (!applyCaptchaToken.value) {
    ElMessage.warning('请先完成滑动验证')
    return
  }
  applying.value = true
  try {
    let attachment = null
    let attachmentName = null

    const picked = applyFileList.value[0]
    if (picked?.raw) {
      const fd = new FormData()
      fd.append('file', picked.raw)
      fd.append('captchaToken', applyCaptchaToken.value)
      const up = await uploadEnrollFile(fd)
      attachment = up.data?.path || null
      attachmentName = up.data?.name || picked.name
    }

    await submitEnrollApplication({
      ...applyForm,
      attachment,
      attachmentName,
      captchaToken: applyCaptchaToken.value
    })

    applyVisible.value = false
    ElMessage.success('提交成功！我们的顾问将尽快与您联系 🎉')
    Object.assign(applyForm, { school: '', name: '', role: '', phone: '', remark: '' })
    applyFileList.value = []
    applyCaptchaToken.value = ''
  } catch (e) {
    ElMessage.error(e?.msg || e?.message || '提交失败，请稍后重试')
  } finally {
    applying.value = false
  }
}

/* ---------- 滚动揭示、数字滚动与导航变色 ---------- */

let observer = null
let statsObserver = null
const statsRef = ref(null)
const onScroll = () => { scrolled.value = window.scrollY > NAV_SCROLL_THRESHOLD }

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })

  // 元素进入视口后加 in 类触发动画，触发过一次就不再观察
  observer = new IntersectionObserver((entries) => {
    entries.forEach((e) => {
      if (!e.isIntersecting) return
      e.target.classList.add('in')
      observer.unobserve(e.target)
    })
  }, { threshold: 0.12 })
  document.querySelectorAll('.reveal').forEach((el) => observer.observe(el))

  if (statsRef.value) {
    statsObserver = new IntersectionObserver((entries) => {
      entries.forEach((e) => {
        if (!e.isIntersecting) return
        runCount()
        statsObserver.disconnect()
      })
    }, { threshold: 0.4 })
    statsObserver.observe(statsRef.value)
  }
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  if (observer) observer.disconnect()
  if (statsObserver) statsObserver.disconnect()
})
</script>

<style scoped src="./styles/landing.css"></style>

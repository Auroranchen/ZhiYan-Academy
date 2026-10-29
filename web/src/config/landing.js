/**
 * 落地页展示内容
 *
 * 这些是市场文案与配图编排，不是业务逻辑。原先以数组字面量的形式占了
 * Landing.vue 近 130 行，改一句宣传语就要动组件。抽出来后组件只留交互。
 */
import imgHomework from '@/assets/landing/homework-publish.png'
import imgPlagiarism from '@/assets/landing/plagiarism.png'
import imgAgentWorkbench from '@/assets/landing/agent-workbench.png'
import imgLearningProfile from '@/assets/landing/learning-profile.png'
import imgAiMicrolesson from '@/assets/landing/ai-microlesson.png'
import imgWeeklyReport from '@/assets/landing/ai-weekly-report.png'

/** 首屏主视觉截图，模板里单独引用 */
export const heroShot = imgAgentWorkbench

export const marqueeKeys = [
  '对话式学习画像', '多智能体协同', 'AI 讲解动画', '流式输出', '防幻觉机制',
  'AI 智能出题', '作业查重', 'AI 代写识别', 'AI 学习周报', 'AI 班级学情周报',
  'AI 智能练习', 'AI 知识思维导图', '作业互评', '个性化学习路径', '游戏化激励',
  '智能错题本', '虚拟老师讲解', '在线考试', '智能阅卷', '课堂签到',
  '实时消息推送', '截止日历提醒', '数据可视化大屏', '全端响应式', '安全风控', '私有化部署'
]

// 夸张的市场化数据 + 数字滚动
export const stats = [
  { target: 360, suffix: '+', decimals: 0, mono: 'PARTNERS', label: '合作院校与学院' },
  { target: 86, suffix: '万+', decimals: 0, mono: 'ACTIVE USERS', label: '覆盖师生用户' },
  { target: 1280, suffix: '万+', decimals: 0, mono: 'AI GRADING', label: 'AI 智能批改次数' },
  { target: 520, suffix: '万+', decimals: 0, mono: 'AI INSIGHTS', label: 'AI 学情报告生成' }
]

export const agents = [
  { t: '诊断智能体', icon: 'ph:stethoscope-duotone', c: '#22d3ee',
    d: '聚合作业、考试、错题数据，定位薄弱知识点与优先级。', in: '学情数据', out: '诊断报告 + 薄弱点' },
  { t: '规划智能体', icon: 'ph:map-trifold-duotone', c: '#818cf8',
    d: '依据诊断结果制定分阶段、可执行的学习路径。', in: '薄弱点', out: '分阶段学习路径' },
  { t: '资源智能体', icon: 'ph:book-open-text-duotone', c: '#a78bfa',
    d: '为每个薄弱知识点生成讲义：要点、讲解、示例、易错。', in: '薄弱点', out: '知识讲义' },
  { t: '思维导图智能体', icon: 'ph:tree-structure-duotone', c: '#34d399',
    d: '围绕主题构建层级知识树，理清概念脉络与关联。', in: '薄弱点', out: '知识思维导图' },
  { t: '出题智能体', icon: 'ph:exam-duotone', c: '#f472b6',
    d: '生成针对性练习题，含答案与解析，巩固薄弱环节。', in: '薄弱点', out: '针对性练习' },
  { t: '拓展阅读智能体', icon: 'ph:books-duotone', c: '#fbbf24',
    d: '推荐教材章节、经典论文、官方文档等优质延伸材料。', in: '薄弱点', out: '拓展阅读清单' },
  { t: '代码实操智能体', icon: 'ph:code-duotone', c: '#38bdf8',
    d: '设计可动手的代码案例与实践步骤，做中学、学中练。', in: '薄弱点', out: '代码实操案例' },
  { t: '编排智能体', icon: 'ph:stack-duotone', c: '#3b82f6',
    d: '整合各智能体产物，编排导读并打包入库，可复用、可推送。', in: '全部产物', out: '资源包 + 导读' }
]

export const features = [
  {
    title: '对话式学习画像 · 6 维动态建模', img: imgLearningProfile, w: 1024, h: 474,
    desc: '摒弃繁琐表单，和 AI「小枢」自然对话即可自动抽取特征，构建知识基础、认知风格、易错点偏好、学习目标、学习节奏、兴趣方向 6 维画像，并随学情数据随学随新、雷达图可视化。',
    bullets: ['自然语言对话自动抽取画像', '6 维雷达图动态可视化', '学习效果评估 → 重点动态调整']
  },
  {
    title: '多智能体协同 · 5 类个性化资源', img: imgAgentWorkbench, w: 1024, h: 449,
    desc: '诊断 → 规划 → 资源 → 思维导图 → 出题 → 拓展阅读 → 代码实操 → 编排，八大智能体协作可视化（实时日志 + 打字机），一键产出讲义、思维导图、练习、拓展阅读、代码实操 5 类资源包，可入库复用、导出 PDF。',
    bullets: ['8 智能体流水线实时可视化', '5 类多模态学习资源一键生成', '教师班级资源包一键推送学生']
  },
  {
    title: 'AI 讲解动画 · 多模态微课', img: imgAiMicrolesson, w: 1024, h: 544,
    desc: '输入任意知识点，AI 自动编排分镜脚本，前端以 SVG/动效逐镜演示并配合浏览器语音讲解，把抽象概念变成可看、可听的「动态微课」，无需录制即可生成多模态学习资源。',
    bullets: ['AI 分镜脚本自动编排', '动效演示 + 语音讲解 + 实时字幕', '5 种版式：要点 / 步骤 / 对比 / 公式']
  },
  {
    title: 'AI 学情周报 · 数据驱动教学', img: imgWeeklyReport, w: 1024, h: 422,
    desc: '学生端自动生成个人学习周报，汇总作业、考试、错题与积分数据；教师端按课程输出班级学情周报，含成绩分布、共性薄弱点与教学建议，支持导出 PDF。',
    bullets: ['学生成长周报（可按课程聚焦）', '教师班级学情周报 + 图表', '薄弱点分析 · PDF 一键导出']
  },
  {
    title: 'AI 智能出题与作业发布', img: imgHomework, w: 1024, h: 693,
    desc: '依据知识点与难度一键生成题目，支持 AI 出题、PPT 出题、复制识别与手动添加，几分钟产出一份作业或试卷。',
    bullets: ['多题型自动生成与校对', 'Word / PPT / 文本智能识别导入', '在线答题与文档上传双模式']
  },
  {
    title: '作业查重 + AI 原创性 / 代写识别', img: imgPlagiarism, w: 1024, h: 240,
    desc: '字符级相似度查重叠加 AI 原创性分析，识别 AI 代写痕迹，守护学术诚信，并给出可复核的判定依据。',
    bullets: ['两两相似度比对与阈值告警', 'AI 生成可能性 / 原创度评分', '判定依据透明，支持人工复核']
  }
]

export const moreFeatures = [
  { icon: 'ph:path-duotone', title: 'AI 个性化学习路径',
    desc: '结合错题本与课程掌握度，智能规划分阶段提升计划。',
    tags: ['动态规划', '阶段目标', '资源推荐'] },
  { icon: 'ph:game-controller-duotone', title: '游戏化学习激励中心',
    desc: '积分、等级、徽章墙与排行榜，把坚持学习变成有成就感的事。',
    tags: ['每日打卡', '积分等级', '全校榜单'] },
  { icon: 'ph:notebook-duotone', title: '智能错题本与知识点画像',
    desc: '错题自动归集，掌握度雷达可视化，一键 AI 变式练习攻克。',
    tags: ['自动归集', '掌握度雷达', '练习闭环'] },
  { icon: 'ph:chalkboard-teacher-duotone', title: 'AI 作业讲解 · 虚拟老师',
    desc: '图文 + 视频虚拟老师逐题讲解，课后答疑不受时间限制。',
    tags: ['逐题讲解', '视频演示', '知识点提炼'] },
  { icon: 'ph:tree-structure-duotone', title: 'AI 思维导图 · 智能练习',
    desc: '任意主题生成知识树，节点 AI 详解；错题变式练习自动判分。',
    tags: ['知识树', '节点讲解', '自动判分'] },
  { icon: 'ph:users-three-duotone', title: '作业互评 · 协作学习',
    desc: '匿名互评自动分配，进度可追踪，均分可一键回填成绩。',
    tags: ['匿名互评', '自动分配', '成绩回填'] }
]

export const matrix = [
  { icon: 'ph:identification-card-duotone', t: '对话式学习画像', d: '自然语言对话自动构建 6 维动态画像（知识基础 / 认知风格 / 易错点偏好 / 学习目标 / 学习节奏 / 兴趣方向），随学随新、雷达图可视化。' },
  { icon: 'ph:circles-three-plus-duotone', t: '学习多智能体工作台', d: '八大智能体协作流水线可视化，一键产出讲义 / 思维导图 / 练习 / 拓展阅读 / 代码实操 5 类资源包，入库复用、导出与推送。' },
  { icon: 'ph:gauge-duotone', t: 'AI 学习效果评估', d: '基于学情与画像输出整体掌握度、趋势、风险与下一步重点，评估结果回写画像，形成「评估→调整→生成」闭环。' },
  { icon: 'ph:film-strip-duotone', t: 'AI 讲解动画微课', d: '输入知识点即生成分镜脚本，前端动效逐镜演示 + 浏览器语音讲解 + 实时字幕，零录制产出多模态学习资源。' },
  { icon: 'ph:lightning-duotone', t: '流式输出 · 即时呈现', d: 'AI 助手基于 SSE 真·流式逐 token 回传，边生成边显示，符合现代 AI 产品交互规范，避免长时间白屏等待。' },
  { icon: 'ph:shield-check-duotone', t: '防幻觉与内容安全', d: '所有 AI 生成均叠加防幻觉约束、输入清洗与敏感内容出口过滤，并显著标注「AI 生成内容，仅供参考」。' },
  { icon: 'ph:chart-line-up-duotone', t: 'AI 学生学习周报', d: '自动汇总作业均分、考试表现、错题掌握率与积分打卡，生成个性化成长报告，支持按课程聚焦与 PDF 导出。' },
  { icon: 'ph:presentation-chart-duotone', t: 'AI 班级学情周报', d: '教师选定课程即可生成班级整体学情：成绩分布、待关注学生、共性薄弱点与 AI 教学改进建议。' },
  { icon: 'ph:target-duotone', t: 'AI 个性化智能练习', d: '基于未掌握错题生成变式题，支持按课程筛选、自动判分与积分奖励，答对自动归档至错题本。' },
  { icon: 'ph:tree-structure-duotone', t: 'AI 知识思维导图', d: '输入任意知识主题，AI 构建层级知识树；点击节点获取核心定义、示例与易错点详解，可下载 PNG。' },
  { icon: 'ph:users-three-duotone', t: '作业互评', d: '教师配置互评人数与匿名规则，系统自动分配评阅任务；学生完成互评获积分，教师可一键回填均分为成绩。' },
  { icon: 'ph:exam-duotone', t: '在线考试与智能阅卷', d: '支持选择/填空/简答及程序填空、编程、代码解释题，代码高亮 + AI 辅助评阅。' },
  { icon: 'ph:hand-tap-duotone', t: '课堂实时签到', d: '基于 WebSocket 的实时签到，发起、成功、代签即时同步到大屏与学生端。' },
  { icon: 'ph:bell-ringing-duotone', t: '实时消息推送', d: '站内消息即时红点与弹窗提醒，互评待办徽章同步，断线自动重连。' },
  { icon: 'ph:calendar-check-duotone', t: '截止日历与提醒', d: '作业/考试截止可视化日历，临期自动提醒，告别错过 deadline。' },
  { icon: 'ph:chart-pie-slice-duotone', t: '数据可视化大屏', d: '成绩分布、提交趋势、课程画像一屏掌握，支撑教学决策。' },
  { icon: 'ph:folder-open-duotone', t: '课程资源中心', d: '课件、资料统一管理与分发，师生随时随地获取学习资源。' },
  { icon: 'ph:chart-bar-duotone', t: '学情统计报表', d: '班级与个人多维报表，与 AI 学情周报联动，量化学习投入与掌握程度。' },
  { icon: 'ph:device-mobile-duotone', t: '全端响应式适配', d: '手机、平板、电脑网页自适应布局，考试作答、互评、日历等核心页面移动端可用。' },
  { icon: 'ph:shield-check-duotone', t: '安全风控体系', d: '滑动验证 + 登录失败锁定 + IP 限流 + 操作审计，全方位保障安全。' },
  { icon: 'ph:identification-card-duotone', t: '多角色协同', d: '教务人员 / 教师 / 学生三端分权协同，权限隔离清晰。' },
  { icon: 'ph:hard-drives-duotone', t: '私有化部署', d: '支持云端或校内私有化部署，数据牢牢掌握在校方手中。' }
]

export const values = [
  { icon: 'ph:trend-up-duotone', title: '降本增效', desc: 'AI 承担出题、批改、学情分析与答疑等重复劳动，教师人均备课与批改时间大幅降低。' },
  { icon: 'ph:chart-line-up-duotone', title: '数据驱动决策', desc: '学情周报、班级报告、错题画像全程沉淀，让教学评估从「凭感觉」变为「看数据」。' },
  { icon: 'ph:arrows-clockwise-duotone', title: '学习闭环', desc: '错题归集 → AI 变式练习 → 自动掌握 → 学情周报反馈，学生提升路径清晰可追踪。' },
  { icon: 'ph:lock-key-duotone', title: '安全合规', desc: '滑动验证、登录风控、权限隔离，支持私有化部署，数据安全可控。' }
]

export const steps = [
  { icon: 'ph:chat-circle-text-duotone', t: '提交需求', d: '填写入驻申请，告诉我们贵校的规模与诉求。' },
  { icon: 'ph:presentation-chart-duotone', t: '专属演示', d: '提供针对性的方案演示与试用环境。' },
  { icon: 'ph:gear-duotone', t: '部署开通', d: '支持云端或私有化部署，快速完成数据初始化。' },
  { icon: 'ph:graduation-cap-duotone', t: '上线运营', d: '师生培训与持续支持，平台正式投入教学。' }
]

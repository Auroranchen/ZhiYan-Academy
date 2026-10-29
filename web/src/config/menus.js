/**
 * 三端导航菜单配置
 *
 * 菜单原先以数组字面量的形式写死在三个布局组件里，加一个页面要同时改路由和布局，
 * 而且三份结构虽然一样却各自演化。这里作为唯一来源。
 *
 * 菜单项字段：
 *   path       跳转路径；有 children 时作为该组的默认入口
 *   label      显示名称
 *   icon       Iconify 图标名，仅一级项需要
 *   children   二级菜单
 *   quick      渲染为顶栏右侧的图标快捷入口，不占一级菜单位
 *   mobileOnly 仅在移动端抽屉里展示
 *
 * 与后端 @RequireRole 保持同一套角色划分，不引入额外的权限点模型。
 */

const ADMIN_MENUS = [
  { path: '/admin/home', label: '首页', icon: 'ph:house-duotone' },
  {
    path: '/admin/major',
    label: '基础数据',
    icon: 'ph:gear-duotone',
    children: [
      { path: '/admin/major', label: '专业管理' },
      { path: '/admin/class', label: '班级管理' },
      { path: '/admin/academic-term', label: '学年学期管理' }
    ]
  },
  { path: '/admin/student', label: '学生管理', icon: 'ph:user-duotone' },
  { path: '/admin/teacher', label: '教师管理', icon: 'ph:chalkboard-teacher-duotone' },
  { path: '/admin/course', label: '课程管理', icon: 'ph:book-open-duotone' },
  {
    path: '/admin/homework',
    label: '作业管理',
    icon: 'ph:file-text-duotone',
    children: [
      { path: '/admin/homework', label: '作业列表' },
      { path: '/admin/studenthomework', label: '学生作业' }
    ]
  },
  { path: '/admin/statistics', label: '统计报表', icon: 'ph:chart-bar-duotone' },
  {
    path: '/admin/dashboard',
    label: '系统监管',
    icon: 'ph:shield-check-duotone',
    children: [
      { path: '/admin/dashboard', label: '数据可视化大屏' },
      { path: '/admin/enroll', label: '入学申请' },
      { path: '/admin/audit', label: '操作审计日志' },
      { path: '/admin/aimonitor', label: 'AI 调用监控' }
    ]
  },
  { path: '/admin/notice', label: '通知公告', icon: 'ph:bell-duotone', quick: true },
  { path: '/admin/message', label: '消息中心', icon: 'ph:chat-circle-dots-duotone', quick: true },
  { path: '/admin/password', label: '修改密码', icon: 'ph:lock-duotone', mobileOnly: true }
]

const TEACHER_MENUS = [
  { path: '/teacher/home', label: '首页', icon: 'ph:house-duotone' },
  {
    path: '/teacher/course',
    label: '我的课程',
    icon: 'ph:book-open-duotone',
    children: [
      { path: '/teacher/course', label: '课程列表' },
      { path: '/teacher/resource', label: '课程资源' }
    ]
  },
  {
    path: '/teacher/homework',
    label: '作业管理',
    icon: 'ph:file-text-duotone',
    children: [
      { path: '/teacher/homework', label: '作业发布' },
      { path: '/teacher/studenthomework', label: '作业批改' },
      { path: '/teacher/plagiarism', label: '作业查重' },
      { path: '/teacher/peer', label: '作业互评' },
      { path: '/teacher/calendar', label: '截止日历' }
    ]
  },
  { path: '/teacher/exam', label: '在线考试', icon: 'ph:exam-duotone' },
  {
    path: '/teacher/agent',
    label: 'AI 教学',
    icon: 'ph:sparkle-duotone',
    children: [
      { path: '/teacher/agent', label: '多智能体工作台' },
      { path: '/teacher/class-report', label: 'AI学情周报' },
      { path: '/teacher/ai-mindmap', label: 'AI思维导图' }
    ]
  },
  { path: '/teacher/attendance', label: '学生考勤', icon: 'ph:calendar-duotone' },
  { path: '/teacher/statistics', label: '统计报表', icon: 'ph:chart-bar-duotone' },
  { path: '/teacher/notice', label: '通知公告', icon: 'ph:bell-duotone', quick: true },
  { path: '/teacher/message', label: '消息中心', icon: 'ph:chat-circle-dots-duotone', quick: true },
  { path: '/teacher/profile', label: '个人中心', icon: 'ph:user-duotone', mobileOnly: true }
]

const STUDENT_MENUS = [
  { path: '/student/home', label: '首页', icon: 'ph:house-duotone' },
  {
    path: '/student/course',
    label: '我的课程',
    icon: 'ph:book-open-duotone',
    children: [
      { path: '/student/course', label: '课程列表' },
      { path: '/student/resource', label: '课程资源' },
      { path: '/student/evaluation', label: '课程评价' }
    ]
  },
  {
    path: '/student/homework/course',
    label: '作业考试',
    icon: 'ph:file-text-duotone',
    children: [
      { path: '/student/homework/course', label: '待完成作业' },
      { path: '/student/myhomework', label: '已完成作业' },
      { path: '/student/exam', label: '在线考试' },
      { path: '/student/peer', label: '作业互评' },
      { path: '/student/wrongbook', label: '错题本' },
      { path: '/student/favorites', label: '我的收藏' },
      { path: '/student/calendar', label: '截止日历' }
    ]
  },
  {
    path: '/student/agent',
    label: 'AI 学习',
    icon: 'ph:sparkle-duotone',
    children: [
      { path: '/student/agent', label: '多智能体工作台' },
      { path: '/student/learning-profile', label: '学习画像' },
      { path: '/student/ai-practice', label: 'AI智能练习' },
      { path: '/student/ai-explainer', label: 'AI讲解动画' },
      { path: '/student/ai-mindmap', label: 'AI思维导图' },
      { path: '/student/ai-report', label: 'AI学习周报' },
      { path: '/student/learning-path', label: 'AI学习路径' }
    ]
  },
  {
    path: '/student/checkin',
    label: '考勤签到',
    icon: 'ph:calendar-duotone',
    children: [
      { path: '/student/checkin', label: '课堂签到' },
      { path: '/student/attendance', label: '我的考勤' }
    ]
  },
  { path: '/student/learning-center', label: '激励中心', icon: 'ph:trophy-duotone' },
  { path: '/student/notice', label: '通知公告', icon: 'ph:bell-duotone', quick: true },
  { path: '/student/message', label: '消息中心', icon: 'ph:chat-circle-dots-duotone', quick: true },
  { path: '/student/profile', label: '个人中心', icon: 'ph:user-duotone', mobileOnly: true }
]

const MENUS_BY_ROLE = {
  admin: ADMIN_MENUS,
  teacher: TEACHER_MENUS,
  student: STUDENT_MENUS
}

/** 各角色的路径前缀，路由守卫与菜单校验共用 */
export const ROLE_PATH_PREFIX = {
  admin: '/admin',
  teacher: '/teacher',
  student: '/student'
}

/** 各角色登录后的落地页 */
export const ROLE_HOME = {
  admin: '/admin/home',
  teacher: '/teacher/home',
  student: '/student/home'
}

export const ROLES = Object.keys(MENUS_BY_ROLE)

/**
 * 取某个角色的菜单。
 * @param {'admin' | 'teacher' | 'student'} role
 * @returns {Array} 未知角色返回空数组，布局组件会渲染成空导航而不是报错
 */
export const getMenus = (role) => MENUS_BY_ROLE[role] || []

/** 根据路径反查所属角色，供路由守卫判断越权访问 */
export const roleOfPath = (path) =>
  ROLES.find((role) => path.startsWith(ROLE_PATH_PREFIX[role])) || null

/** 展平成路径列表，便于测试菜单与路由是否对得上 */
export const flattenMenuPaths = (role) =>
  getMenus(role).flatMap((item) => [item.path, ...(item.children || []).map((c) => c.path)])

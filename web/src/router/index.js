/**
 * 路由配置
 * 定义系统的所有路由规则 */
import { createRouter, createWebHistory } from 'vue-router'
import store from '@/store'
import { ROLE_HOME, roleOfPath } from '@/config/menus'

// 路由配置
const routes = [
  {
    path: '/',
    name: 'Welcome',
    component: () => import('@/views/Landing.vue'),
    meta: { title: '智枢学堂 · 基于大模型的个性化资源生成与学习多智能体平台' }
  },
  {
    path: '/welcome',
    redirect: '/'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/admin',
    name: 'Main',
    component: () => import('@/views/Main.vue'),
    redirect: '/admin/home',
    children: [
      {
        path: '/admin/home',
        name: 'Home',
        component: () => import('@/views/admin/Home.vue'),
        meta: { title: '首页' }
      },
      {
        path: '/admin/major',
        name: 'MajorList',
        component: () => import('@/views/admin/MajorList.vue'),
        meta: { title: '专业管理' }
      },
      {
        path: '/admin/class',
        name: 'ClassList',
        component: () => import('@/views/admin/ClassList.vue'),
        meta: { title: '班级管理' }
      },
      {
        path: '/admin/academic-term',
        name: 'AcademicTermList',
        component: () => import('@/views/admin/AcademicTermList.vue'),
        meta: { title: '学年学期管理' }
      },
      {
        path: '/admin/student',
        name: 'StudentList',
        component: () => import('@/views/admin/StudentList.vue'),
        meta: { title: '学生管理' }
      },
      {
        path: '/admin/teacher',
        name: 'TeacherList',
        component: () => import('@/views/admin/TeacherList.vue'),
        meta: { title: '教师管理' }
      },
      {
        path: '/admin/notice',
        name: 'NoticeList',
        component: () => import('@/views/admin/NoticeList.vue'),
        meta: { title: '通知公告管理' }
      },
      {
        path: '/admin/message',
        name: 'AdminMessage',
        component: () => import('@/views/admin/AdminMessage.vue'),
        meta: { title: '消息中心' }
      },
      {
        path: '/admin/course',
        name: 'CourseList',
        component: () => import('@/views/admin/CourseList.vue'),
        meta: { title: '课程管理' }
      },
      {
        path: '/admin/homework',
        name: 'HomeworkList',
        component: () => import('@/views/admin/HomeworkList.vue'),
        meta: { title: '作业管理' }
      },
      {
        path: '/admin/studenthomework',
        name: 'StudentHomeworkList',
        component: () => import('@/views/admin/StudentHomeworkList.vue'),
        meta: { title: '学生作业管理' }
      },
      {
        path: '/admin/statistics',
        name: 'Statistics',
        component: () => import('@/views/admin/Statistics.vue'),
        meta: { title: '统计报表' }
      },
      {
        path: '/admin/dashboard',
        name: 'DashboardScreen',
        component: () => import('@/views/admin/DashboardScreen.vue'),
        meta: { title: '数据可视化大屏' }
      },
      {
        path: '/admin/audit',
        name: 'AuditLog',
        component: () => import('@/views/admin/AuditLog.vue'),
        meta: { title: '操作审计日志' }
      },
      {
        path: '/admin/aimonitor',
        name: 'AiMonitor',
        component: () => import('@/views/admin/AiMonitor.vue'),
        meta: { title: 'AI调用监控' }
      },
      {
        path: '/admin/enroll',
        name: 'EnrollList',
        component: () => import('@/views/admin/EnrollList.vue'),
        meta: { title: '入学申请' }
      },
      {
        path: '/admin/password',
        name: 'Password',
        component: () => import('@/views/admin/Password.vue'),
        meta: { title: '修改密码' }
      }
    ]
  },
  // 教师模块路由
  {
    path: '/teacher',
    name: 'TeacherMain',
    component: () => import('@/views/teacher/TeacherMain.vue'),
    redirect: '/teacher/home',
    children: [
      { path: '/teacher/home', name: 'TeacherHome', component: () => import('@/views/teacher/TeacherHome.vue'), meta: { title: '教师首页' } },
      { path: '/teacher/notice', name: 'TeacherNotice', component: () => import('@/views/common/NoticeBoard.vue'), meta: { title: '通知公告' } },
      { path: '/teacher/course', name: 'MyCourse', component: () => import('@/views/teacher/MyCourse.vue'), meta: { title: '我的课程' } },
      { path: '/teacher/homework', name: 'TeacherHomework', component: () => import('@/views/teacher/TeacherHomework.vue'), meta: { title: '作业管理' } },
      { path: '/teacher/studenthomework', name: 'TeacherStudentHomework', component: () => import('@/views/teacher/TeacherStudentHomework.vue'), meta: { title: '学生作业批改' } },
      { path: '/teacher/exam', name: 'TeacherExam', component: () => import('@/views/teacher/TeacherExam.vue'), meta: { title: '在线考试' } },
      { path: '/teacher/calendar', name: 'TeacherCalendar', component: () => import('@/views/common/DeadlineCalendar.vue'), meta: { title: '截止日历' } },
      { path: '/teacher/plagiarism', name: 'TeacherPlagiarism', component: () => import('@/views/teacher/TeacherPlagiarism.vue'), meta: { title: '作业查重' } },
      { path: '/teacher/peer', name: 'TeacherPeerReview', component: () => import('@/views/teacher/TeacherPeerReview.vue'), meta: { title: '作业互评' } },
      { path: '/teacher/agent', name: 'TeacherAgentStudio', component: () => import('@/views/teacher/AgentStudio.vue'), meta: { title: '学习多智能体工作台' } },
      { path: '/teacher/class-report', name: 'TeacherClassReport', component: () => import('@/views/teacher/TeacherClassReport.vue'), meta: { title: 'AI班级学情周报' } },
      { path: '/teacher/ai-mindmap', name: 'TeacherAiMindMap', component: () => import('@/views/student/AiMindMap.vue'), meta: { title: 'AI思维导图' } },
      { path: '/teacher/attendance', name: 'TeacherAttendance', component: () => import('@/views/teacher/TeacherAttendance.vue'), meta: { title: '学生考勤' } },
      { path: '/teacher/resource', name: 'TeacherResource', component: () => import('@/views/teacher/TeacherResource.vue'), meta: { title: '课程资源' } },
      { path: '/teacher/message', name: 'TeacherMessage', component: () => import('@/views/common/MessageCenter.vue'), meta: { title: '消息中心' } },
      { path: '/teacher/statistics', name: 'TeacherStatistics', component: () => import('@/views/teacher/TeacherStatistics.vue'), meta: { title: '统计报表' } },
      { path: '/teacher/profile', name: 'TeacherProfile', component: () => import('@/views/teacher/TeacherProfile.vue'), meta: { title: '个人中心' } }
    ]
  },
  // 学生模块路由
  {
    path: '/student',
    name: 'StudentMain',
    component: () => import('@/views/student/StudentMain.vue'),
    redirect: '/student/home',
    children: [
      { path: '/student/home', name: 'StudentHome', component: () => import('@/views/student/StudentHome.vue'), meta: { title: '学生首页' } },
      { path: '/student/notice', name: 'StudentNotice', component: () => import('@/views/common/NoticeBoard.vue'), meta: { title: '通知公告' } },
      { path: '/student/course', name: 'StudentCourse', component: () => import('@/views/student/StudentCourse.vue'), meta: { title: '我的课程' } },
      { path: '/student/homework/course', name: 'CourseHomework', component: () => import('@/views/student/CourseHomework.vue'), meta: { title: '待完成作业' } },
      { path: '/student/myhomework', name: 'StudentHomework', component: () => import('@/views/student/StudentHomework.vue'), meta: { title: '已完成作业' } },
      { path: '/student/homework/explain', name: 'AiExplainHomework', component: () => import('@/views/student/AiExplainHomework.vue'), meta: { title: 'AI作业讲解' } },
      { path: '/student/wrongbook', name: 'WrongBook', component: () => import('@/views/student/WrongBook.vue'), meta: { title: '我的错题本' } },
      { path: '/student/favorites', name: 'MyFavorites', component: () => import('@/views/student/MyFavorites.vue'), meta: { title: '我的收藏' } },
      { path: '/student/peer', name: 'StudentPeerReview', component: () => import('@/views/student/StudentPeerReview.vue'), meta: { title: '作业互评' } },
      { path: '/student/learning-profile', name: 'StudentLearningProfile', component: () => import('@/views/student/LearningProfile.vue'), meta: { title: '对话式学习画像' } },
      { path: '/student/agent', name: 'StudentAgentStudio', component: () => import('@/views/student/AgentStudio.vue'), meta: { title: '学习多智能体工作台' } },
      { path: '/student/ai-report', name: 'AiLearningReport', component: () => import('@/views/student/AiLearningReport.vue'), meta: { title: 'AI学习周报' } },
      { path: '/student/ai-practice', name: 'AiSmartPractice', component: () => import('@/views/student/AiSmartPractice.vue'), meta: { title: 'AI智能练习' } },
      { path: '/student/ai-mindmap', name: 'AiMindMap', component: () => import('@/views/student/AiMindMap.vue'), meta: { title: 'AI思维导图' } },
      { path: '/student/ai-explainer', name: 'AiExplainer', component: () => import('@/views/student/AiExplainer.vue'), meta: { title: 'AI讲解动画' } },
      { path: '/student/learning-center', name: 'LearningCenter', component: () => import('@/views/student/LearningCenter.vue'), meta: { title: '学习激励中心' } },
      { path: '/student/exam', name: 'StudentExamList', component: () => import('@/views/student/StudentExamList.vue'), meta: { title: '在线考试' } },
      { path: '/student/exam/take', name: 'ExamTake', component: () => import('@/views/student/ExamTake.vue'), meta: { title: '考试作答' } },
      { path: '/student/calendar', name: 'StudentCalendar', component: () => import('@/views/common/DeadlineCalendar.vue'), meta: { title: '截止日历' } },
      { path: '/student/learning-path', name: 'LearningPath', component: () => import('@/views/student/LearningPath.vue'), meta: { title: 'AI学习路径' } },
      { path: '/student/homework/submit', name: 'SubmitHomework', component: () => import('@/views/student/SubmitHomework.vue'), meta: { title: '提交作业' } },
      { path: '/student/resource', name: 'StudentResource', component: () => import('@/views/student/StudentResource.vue'), meta: { title: '课程资源' } },
      { path: '/student/evaluation', name: 'StudentEvaluation', component: () => import('@/views/student/StudentEvaluation.vue'), meta: { title: '课程评价' } },
      { path: '/student/attendance', name: 'StudentAttendance', component: () => import('@/views/student/StudentAttendance.vue'), meta: { title: '我的考勤' } },
      { path: '/student/checkin', name: 'StudentCheckin', component: () => import('@/views/student/StudentCheckin.vue'), meta: { title: '课堂签到' } },
      { path: '/student/message', name: 'StudentMessage', component: () => import('@/views/common/MessageCenter.vue'), meta: { title: '消息中心' } },
      { path: '/student/profile', name: 'StudentProfile', component: () => import('@/views/student/StudentProfile.vue'), meta: { title: '个人中心' } }
    ]
  }
]

// 创建路由实例
const router = createRouter({
  history: createWebHistory(),
  routes
})

/** 无需登录即可访问的页面 */
const PUBLIC_PATHS = ['/', '/welcome', '/login']

// 路由守卫：检查登录状态与角色归属
router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 智枢学堂` : '智枢学堂'

  if (PUBLIC_PATHS.includes(to.path)) {
    next()
    return
  }

  const role = store.state.role
  if (!store.getters.isLoggedIn || !role) {
    // 未登录先回落地页，而不是直接弹登录框
    next('/')
    return
  }

  // 路径前缀决定这个页面属于哪个角色，与后端 @RequireRole 是同一套划分
  const owner = roleOfPath(to.path)
  if (owner && owner !== role) {
    next(ROLE_HOME[role] || '/login')
    return
  }

  next()
})

export default router

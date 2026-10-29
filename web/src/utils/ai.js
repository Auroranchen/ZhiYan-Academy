/**
 * AI助手工具类
 * 通过后端代理调用智谱AI，避免CORS和API Key暴露
 */
import { chatWithAssistant, chatWithHomeworkAssistant } from '@/api/ai'

/** 流式端点地址（流式用 fetch + ReadableStream，不走 http 封装） */
const CHAT_STREAM_URL = '/ZhiYanAcademy/ai/chat-stream'
const HOMEWORK_CHAT_STREAM_URL = '/ZhiYanAcademy/ai/homework-chat-stream'

/**
 * 根据用户角色获取系统提示词
 */
const getSystemPrompt = (role, name) => {
  const basePrompt = `你是智枢学堂的AI学习助手"小枢"。请用友好、专业的语气回答问题。
【智能辅导规范】
1. 用 Markdown 输出；较复杂的问题分「① 思路 → ② 分步讲解 → ③ 小结」三段，步骤清晰。
2. 善用「图解」：可用文字结构图/ASCII 示意/表格/流程箭头(A → B → C)/生活化类比 帮助理解抽象概念。
3. 代码和公式放进代码块；关键代码加中文注释。
4. 适当推荐可进一步学习的「多模态资源类型」（如"可搜索 XX 的可视化动画/短视频讲解"），但不要编造具体链接或不存在的资源。
5. 防幻觉：只讲有把握的内容，不确定就说明并给出查证方向；不编造数据、文献、API。`
  
  const rolePrompts = {
    admin: `${basePrompt}
当前用户是教务人员${name ? `（${name}）` : ''}。
你可以帮助教务人员解决以下问题：
1. 专业管理：如何添加、编辑、删除专业
2. 班级管理：班级的增删改查操作
3. 学生管理：学生信息的管理、批量导入学生
4. 教师管理：教师账号的管理
5. 课程管理：课程的分配和管理
6. 通知公告：如何发布和管理系统通知
7. 统计报表：查看系统数据统计
8. 系统问题：密码重置、权限管理等

请根据教务人员的问题，给出具体的操作指导。`,

    teacher: `${basePrompt}
当前用户是教师${name ? `（${name}老师）` : ''}。
你可以帮助教师解决以下问题：
1. 课程管理：查看我的课程列表
2. 作业发布：如何创建和发布新作业，设置截止时间
3. 作业批改：如何批改学生提交的作业，给出评分和评语
4. 学生作业查看：查看某门课程的学生作业提交情况
5. 统计分析：查看班级作业完成情况统计
6. 通知查看：查看系统通知公告
7. 个人信息：修改个人信息和密码

请根据教师的问题，给出具体的操作指导和教学建议。`,

    student: `${basePrompt}
当前用户是学生${name ? `（${name}同学）` : ''}。
你可以帮助学生解决以下问题：
1. 课程查看：查看我的课程列表
2. 作业查看：查看课程作业要求和截止时间
3. 作业提交：如何提交作业，支持的文件格式
4. 作业状态：查看作业批改结果和评语
5. 通知公告：查看老师和教务发布的通知
6. 个人信息：修改个人信息和密码
7. 学习建议：提供学习方法和时间管理建议

请用鼓励的语气帮助学生解决问题，并适当给予学习上的鼓励。`
  }
  
  return rolePrompts[role] || basePrompt
}

/**
 * 根据用户角色获取欢迎语
 */
export const getWelcomeMessage = (role, name) => {
  const messages = {
    admin: `您好${name ? `，${name}` : ''}！我是您的AI助手小枢 🤖
作为教务人员，我可以帮您解答：
• 专业/班级/学生/教师管理
• 课程分配与通知发布
• 系统设置与数据统计
请问有什么可以帮您的？`,

    teacher: `您好${name ? `，${name}老师` : ''}！我是AI助手小枢 🤖
作为教师，我可以帮您解答：
• 课程管理与作业发布
• 作业批改与成绩管理
• 班级统计与学情分析
请问有什么可以帮您的？`,

    student: `您好${name ? `，${name}同学` : ''}！我是AI助手小枢 🤖
我可以为你提供「分步讲解 + 图解 + 多模态学习建议」式答疑：
• 课程与作业查询
• 知识点分步精讲与举例图解
• 学习方法与多模态资源推荐
有任何问题都可以问我哦！加油！💪`
  }
  
  return messages[role] || `您好！我是AI助手小枢，有什么可以帮您的？`
}

/**
 * 发送消息到AI助手（通过后端代理）
 * @param {Array} messages - 对话历史
 * @param {string} userRole - 用户角色
 * @param {string} userName - 用户名称
 * @param {string} homeworkContext - 当前作业上下文（可选）
 */
/**
 * 构造发送给后端的 systemPrompt 与拼接消息
 *
 * @param homeworkId 当前作业 ID。**这是作业加固的唯一依据**——服务端会拿它查库判定。
 */
const buildPayload = (messages, userRole, userName, homeworkContext, extraContext, homeworkId) => {
  let systemPrompt = getSystemPrompt(userRole, userName)
  if (extraContext) {
    systemPrompt += `\n\n【当前学习上下文】\n${extraContext}`
  }
  if (homeworkContext) {
    // 仅作为对话上下文，**不承担任何安全职责**。
    //
    // 改造前这里会拼一段「学术诚信」规则，而服务端靠它是否出现来判断要不要加固——
    // 于是防线由客户端开关：直接 curl 省略这段文本即可完全绕过。
    // 现在加固由服务端查库决定，这段文字只帮助学生看到自己在哪份作业里提问。
    systemPrompt += `\n\n【${homeworkContext}】`
  }
  const lastUserMsg = messages.filter(m => m.role === 'user').pop()
  if (!lastUserMsg) throw new Error('没有用户消息')
  const contextMessages = messages.slice(-6)
  let fullMessage = ''
  if (contextMessages.length > 1) {
    fullMessage = contextMessages.map(m => `${m.role === 'user' ? '用户' : 'AI'}：${m.content}`).join('\n') + '\n请回复最后一条用户消息。'
  } else {
    fullMessage = lastUserMsg.content
  }
  const payload = { systemPrompt, message: fullMessage }
  if (homeworkId) payload.homeworkId = String(homeworkId)
  return payload
}

export const sendMessage = async (
  messages,
  userRole,
  userName,
  homeworkContext,
  courseId = null,
  homeworkId = null
) => {
  try {
    const payload = buildPayload(messages, userRole, userName, homeworkContext, null, homeworkId)
    payload.courseId = courseId ? String(courseId) : null
    // 有 homeworkId 就必须走作业端点：通用端点不带作业语义，走错了等于没有加固
    const res = homeworkId
      ? await chatWithHomeworkAssistant(payload, { suppressMessage: true })
      : await chatWithAssistant(payload, { suppressMessage: true })
    return res.data
  } catch (error) {
    console.error('AI助手请求失败:', error)
    throw new Error('AI助手暂时无法响应，请稍后重试')
  }
}

/**
 * 真·流式发送（SSE）：通过 fetch 读取后端 SseEmitter，逐片回调 onChunk。
 * 失败时自动降级为一次性请求。
 * @param {Object} options 可选 { signal: AbortSignal, courseId, homeworkId }
 *   - signal：中断后返回已生成的部分内容
 *   - homeworkId：带上则改走作业辅导端点，加固由服务端查库判定
 */
export const sendMessageStream = async (messages, userRole, userName, onChunk, homeworkContext, options = {}) => {
  let payload
  try {
    payload = buildPayload(
      messages,
      userRole,
      userName,
      homeworkContext,
      options.extraContext,
      options.homeworkId
    )
    if (options.courseId) payload.courseId = String(options.courseId)
  } catch (e) {
    throw new Error('AI助手暂时无法响应，请稍后重试')
  }

  let full = ''
  try {
    const streamUrl = options.homeworkId ? HOMEWORK_CHAT_STREAM_URL : CHAT_STREAM_URL
    const resp = await fetch(streamUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
      credentials: 'include',
      body: JSON.stringify(payload),
      signal: options.signal
    })
    if (!resp.ok || !resp.body) throw new Error('stream not available')

    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let done = false

    const handleLine = (line) => {
      const t = line.trim()
      if (!t.startsWith('data:')) return
      const raw = t.slice(5).trim()
      if (!raw) return
      let val
      try { val = JSON.parse(raw) } catch (e) { val = raw }
      if (val === '[DONE]') { done = true; return }
      if (val === '[ERROR]') { throw new Error('AI 响应中断') }
      if (typeof val === 'string' && val) {
        full += val
        if (onChunk) onChunk(val)
      }
    }

    while (!done) {
      const { value, done: rd } = await reader.read()
      if (rd) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() // 末尾可能是半行，保留
      for (const ln of lines) handleLine(ln)
    }
    if (buffer) handleLine(buffer)

    if (!full) throw new Error('empty stream')
    return full
  } catch (e) {
    // 用户主动停止：返回已生成的部分内容，不降级重试
    if (e && (e.name === 'AbortError' || (options.signal && options.signal.aborted))) {
      return full
    }
    // 降级：一次性返回
    const reply = await sendMessage(
      messages,
      userRole,
      userName,
      homeworkContext,
      options.courseId,
      options.homeworkId
    )
    if (onChunk && reply) onChunk(reply)
    return reply
  }
}

export default {
  getWelcomeMessage,
  sendMessage,
  sendMessageStream
}

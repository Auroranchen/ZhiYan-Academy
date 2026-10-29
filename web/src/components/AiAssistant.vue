<template>
  <!-- AI助手组件 -->
  <div 
    v-if="isVisible"
    class="ai-assistant-container" 
    :style="containerStyle"
    ref="containerRef"
  >
    <!-- 悬浮按钮 (支持拖拽) -->
    <div 
      class="ai-float-btn" 
      @mousedown="e => startDrag(e, 'btn')"
      @touchstart.passive="e => startTouchDrag(e, 'btn')"
      :class="{ 'has-unread': hasUnread, 'is-dragging': isDragging }"
      title="点击打开/关闭，按住拖动位置"
    >
      <el-icon :size="28"><Service /></el-icon>
      <span class="btn-text">AI助手</span>
    </div>
    
    <!-- 聊天窗口 (支持调整大小) -->
    <transition name="fade">
      <div 
        v-if="isOpen" 
        class="ai-chat-window"
        :style="windowStyle"
        @mousedown="e => startDrag(e, 'window')"
      >
        <!-- 头部 -->
        <div class="chat-header">
          <div class="header-title">
            <el-icon :size="20"><Service /></el-icon>
            <span>AI助手 - 小枢</span>
          </div>
          <div class="header-actions">
            <el-button link @click.stop="clearHistory" title="清空对话" style="color: white">
              <el-icon><Delete /></el-icon>
            </el-button>
            <el-button link @click.stop="toggleChat" title="关闭" style="color: white">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </div>
        
        <!-- 消息列表 -->
        <div class="chat-messages" ref="messagesContainer">
          <div 
            v-for="(msg, index) in visibleMessages" 
            :key="index" 
            class="message-item"
            :class="msg.role"
          >
            <div class="message-avatar">
              <Icon v-if="msg.role === 'assistant'" icon="ph:robot-duotone" :width="20" :height="20" />
              <UserAvatar v-else :name="userDisplayName" :avatar-url="userAvatarUrl" size="small" />
            </div>
            <div class="message-content">
              <div class="message-text" v-safe-html="formatMessage(msg.content)"></div>
              <div v-if="msg.role === 'assistant' && msg.messageKey && msg.content" class="message-feedback">
                <span>这条回答有帮助吗？</span>
                <button :class="{ active: msg.feedback === 1 }" :disabled="msg.feedbackLoading"
                  title="有帮助" @click.stop="submitFeedback(msg, index, 1)">
                  <Icon icon="ph:thumbs-up" :width="15" :height="15" />
                </button>
                <button :class="{ active: msg.feedback === -1 }" :disabled="msg.feedbackLoading"
                  title="需要改进" @click.stop="submitFeedback(msg, index, -1)">
                  <Icon icon="ph:thumbs-down" :width="15" :height="15" />
                </button>
                <span v-if="msg.feedback" class="feedback-done">已反馈</span>
              </div>
            </div>
          </div>
          
          <!-- 加载中（仅在 AI 尚未开始输出内容时显示） -->
          <div v-if="showThinking" class="message-item assistant">
            <div class="message-avatar">
              <Icon icon="ph:robot-duotone" :width="20" :height="20" />
            </div>
            <div class="message-content">
              <div class="message-text loading-dots">
                <span></span><span></span><span></span>
              </div>
            </div>
          </div>
        </div>
        
        <!-- 输入区域 -->
        <div class="chat-input">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="2"
            :placeholder="recognizing ? '正在聆听，请说话…' : '请输入您的问题…'"
            resize="none"
            @keydown.enter.exact.prevent="sendMessage"
            :disabled="loading"
          />
          <div class="input-actions">
            <VoiceButton
              class="mic-btn"
              :supported="speechSupported"
              :recording="recognizing"
              :disabled="loading"
              @toggle="toggleVoiceInput"
            />
            <el-button
              v-if="loading"
              type="danger"
              plain
              @click="stopGeneration"
              title="停止生成"
            >
              <Icon icon="ph:stop-circle-bold" :width="16" :height="16" style="margin-right: 4px" />
              停止
            </el-button>
            <el-button
              v-else
              type="primary"
              @click="sendMessage"
              :disabled="!inputText.trim()"
            >
              发送
            </el-button>
          </div>
        </div>

        <!-- 调整大小的手柄 -->
        <div class="resize-handle" @mousedown.stop.prevent="startResize">
          <el-icon><BottomRight /></el-icon>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted, reactive } from 'vue'
import { useStore } from 'vuex'
import { useRoute } from 'vue-router'
import { Service, Close, Delete, BottomRight } from '@element-plus/icons-vue'
import { Icon } from '@iconify/vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getWelcomeMessage, sendMessageStream } from '@/utils/ai'
import UserAvatar from '@/components/UserAvatar.vue'
import { VoiceButton } from '@/components/ui'
import { useVoiceInput } from '@/utils/useVoiceInput'
import { sendAiFeedback } from '@/api/ai'
import { escapeHtml } from '@/utils/safeHtml'

/** 全局悬浮 AI 助手：可拖拽、可缩放的流式对话窗口 */
const store = useStore()
const route = useRoute()

// --- 显示控制 ---
// 在登录页和注册页不显示
const isVisible = computed(() => {
  const hiddenRoutes = ['/login', '/register']
  return !hiddenRoutes.includes(route.path)
})

const isOpen = ref(false)
const loading = ref(false)
const inputText = ref('')
const messages = ref([])

// 流式回复期间：占位的空 AI 消息不渲染（用「···」气泡代替），有内容后再显示
const visibleMessages = computed(() => {
  const list = messages.value
  if (loading.value && list.length) {
    const last = list[list.length - 1]
    if (last.role === 'assistant' && !last.content) return list.slice(0, -1)
  }
  return list
})
// 仅在 AI 还没输出任何内容时显示「···」加载气泡
const showThinking = computed(() => {
  if (!loading.value) return false
  const last = messages.value[messages.value.length - 1]
  return !(last && last.role === 'assistant' && last.content)
})
const messagesContainer = ref(null)
const hasUnread = ref(false)

// --- 拖拽与定位逻辑 ---
// 默认位置：右下角
const position = reactive({ x: window.innerWidth - 100, y: window.innerHeight - 150 })
const isDragging = ref(false)
const containerRef = ref(null)
const dragOffset = { x: 0, y: 0 }

// --- 窗口大小调整逻辑 ---
const windowSize = reactive({ width: 380, height: 520 })
const isResizing = ref(false)
const resizeStart = { x: 0, y: 0, width: 0, height: 0 }

// 加载保存的位置和大小
const loadConfig = () => {
  const saved = localStorage.getItem('ai-assistant-config')
  if (saved) {
    try {
      const config = JSON.parse(saved)
      if (config.x && config.y) {
        // 确保位置在屏幕内
        position.x = Math.min(Math.max(0, config.x), window.innerWidth - 60)
        position.y = Math.min(Math.max(0, config.y), window.innerHeight - 60)
      }
      if (config.width && config.height) {
        windowSize.width = Math.max(300, config.width)
        windowSize.height = Math.max(400, config.height)
      }
    } catch (e) { console.error('Load config failed', e) }
  }
}

const saveConfig = () => {
  localStorage.setItem('ai-assistant-config', JSON.stringify({
    x: position.x,
    y: position.y,
    width: windowSize.width,
    height: windowSize.height
  }))
}

// 容器样式 (控制悬浮球位置)
const containerStyle = computed(() => ({
  left: `${position.x}px`,
  top: `${position.y}px`
}))

// 窗口样式 (控制大小和相对位置)
const windowStyle = computed(() => ({
  width: `${windowSize.width}px`,
  height: `${windowSize.height}px`,
  // 智能计算展开方向：如果太靠右，向左展开；如果太靠下，向上展开
  right: (window.innerWidth - position.x < windowSize.width + 20) ? '0px' : 'auto',
  left: (window.innerWidth - position.x < windowSize.width + 20) ? 'auto' : '0px',
  bottom: (window.innerHeight - position.y < windowSize.height + 20) ? '70px' : 'auto',
  top: (window.innerHeight - position.y < windowSize.height + 20) ? 'auto' : '70px',
}))

// 触摸拖拽（移动端）
let touchDragSource = 'btn'
const touchStartPos = { x: 0, y: 0 }
let touchDragged = false

const startTouchDrag = (e, source = 'btn') => {
  if (isResizing.value) return
  const touch = e.touches[0]
  touchDragSource = source
  touchDragged = false
  dragOffset.x = touch.clientX - position.x
  dragOffset.y = touch.clientY - position.y
  touchStartPos.x = touch.clientX
  touchStartPos.y = touch.clientY

  document.addEventListener('touchmove', onTouchDragMove, { passive: false })
  document.addEventListener('touchend', onTouchDragEnd)
}

const onTouchDragMove = (e) => {
  const touch = e.touches[0]
  const moveX = Math.abs(touch.clientX - touchStartPos.x)
  const moveY = Math.abs(touch.clientY - touchStartPos.y)

  if (moveX > 5 || moveY > 5) {
    touchDragged = true
    e.preventDefault() // 阻止页面滚动
  }

  let newX = touch.clientX - dragOffset.x
  let newY = touch.clientY - dragOffset.y
  const maxX = window.innerWidth - 60
  const maxY = window.innerHeight - 60
  newX = Math.min(Math.max(0, newX), maxX)
  newY = Math.min(Math.max(0, newY), maxY)
  position.x = newX
  position.y = newY
}

const onTouchDragEnd = () => {
  document.removeEventListener('touchmove', onTouchDragMove)
  document.removeEventListener('touchend', onTouchDragEnd)

  if (touchDragged) {
    saveConfig()
  } else {
    if (touchDragSource === 'btn') {
      toggleChat()
    }
  }
}

// 开始拖拽
const dragStartPosition = { x: 0, y: 0 }
let dragSource = 'btn' // 'btn' or 'header'

const startDrag = (e, source = 'btn') => {
  // 只有左键点击且没有在调整大小时才触发
  if (e.button !== 0 || isResizing.value) return
  
  // 如果是在窗口模式，检查点击目标是否为交互元素
  if (source === 'window') {
    const target = e.target
    // 如果点击的是输入框、按钮或消息内容文本，不触发拖拽
    // 注意：允许拖拽 .chat-messages 背景，但不允许拖拽 .message-text（以便选择文本）
    if (
      target.tagName === 'INPUT' || 
      target.tagName === 'TEXTAREA' || 
      target.tagName === 'BUTTON' ||
      target.closest('button') ||
      target.closest('.message-text') ||
      target.closest('.el-input')
    ) {
      return
    }
  }
  
  e.preventDefault() // 防止默认拖拽行为
  
  dragSource = source
  isDragging.value = false 
  dragOffset.x = e.clientX - position.x
  dragOffset.y = e.clientY - position.y
  
  // 记录起始位置，用于判断是点击还是拖拽
  dragStartPosition.x = e.clientX
  dragStartPosition.y = e.clientY
  
  document.addEventListener('mousemove', onDragMove)
  document.addEventListener('mouseup', onDragEnd)
}

const onDragMove = (e) => {
  // 计算移动距离
  const moveX = Math.abs(e.clientX - dragStartPosition.x)
  const moveY = Math.abs(e.clientY - dragStartPosition.y)
  
  // 只有移动超过一定阈值才视为拖拽
  if (moveX > 3 || moveY > 3) {
    isDragging.value = true
  }
  
  let newX = e.clientX - dragOffset.x
  let newY = e.clientY - dragOffset.y
  
  // 边界限制
  const maxX = window.innerWidth - 60
  const maxY = window.innerHeight - 60
  newX = Math.min(Math.max(0, newX), maxX)
  newY = Math.min(Math.max(0, newY), maxY)
  
  position.x = newX
  position.y = newY
}

const onDragEnd = () => {
  document.removeEventListener('mousemove', onDragMove)
  document.removeEventListener('mouseup', onDragEnd)
  
  if (isDragging.value) {
    // 如果是拖拽结束，保存位置
    saveConfig()
    setTimeout(() => { isDragging.value = false }, 0)
  } else {
    // 如果移动距离很小，视为点击
    // 只有当源是悬浮球时，才切换窗口开关
    if (dragSource === 'btn') {
      toggleChat()
    }
  }
}

// 开始调整大小
const startResize = (e) => {
  isResizing.value = true
  resizeStart.x = e.clientX
  resizeStart.y = e.clientY
  resizeStart.width = windowSize.width
  resizeStart.height = windowSize.height
  
  document.addEventListener('mousemove', onResizeMove)
  document.addEventListener('mouseup', onResizeEnd)
}

const onResizeMove = (e) => {
  if (!isResizing.value) return
  const deltaX = e.clientX - resizeStart.x
  const deltaY = e.clientY - resizeStart.y
  
  // 限制最小尺寸
  windowSize.width = Math.max(300, resizeStart.width + deltaX)
  windowSize.height = Math.max(400, resizeStart.height + deltaY)
}

const onResizeEnd = () => {
  isResizing.value = false
  document.removeEventListener('mousemove', onResizeMove)
  document.removeEventListener('mouseup', onResizeEnd)
  saveConfig()
}

// --- AI 逻辑 ---

// 获取用户角色和名称
const userRole = computed(() => store.state.role || 'student')
const userName = computed(() => {
  const user = store.state.user
  if (!user) return ''
  return user.realname || user.teachername || user.studentname || ''
})
const homeworkContext = computed(() => store.state.homeworkContext || '')
// 作业加固依据：带上它才会走作业辅导端点，由服务端查库决定是否加固
const homeworkId = computed(() => store.state.homeworkId || null)
const aiCourseId = computed(() => store.state.aiCourseId || null)

// 用户头像数据（从 Vuex 获取）
const userDisplayName = computed(() => store.getters.displayName || '')
const userAvatarUrl = computed(() => store.getters.userAvatar || '')

// 初始化欢迎消息
const initWelcome = () => {
  const welcome = getWelcomeMessage(userRole.value, userName.value)
  messages.value = [{ role: 'assistant', content: welcome }]
}

// 切换聊天窗口
const toggleChat = () => {
  if (isDragging.value) return // 如果是拖拽结束，不触发切换
  isOpen.value = !isOpen.value
  hasUnread.value = false
  if (isOpen.value && messages.value.length === 0) {
    initWelcome()
  }
  nextTick(() => scrollToBottom())
}

// --- 生成中断控制 ---
let abortController = null
let genCtl = null // { stopped, typeQueue }

const stopGeneration = () => {
  if (genCtl) {
    genCtl.stopped = true
    genCtl.typeQueue.length = 0 // 丢弃未打字的内容
  }
  if (abortController) {
    try { abortController.abort() } catch (e) { /* ignore */ }
  }
}

// 发送消息
const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  // 发送前停止语音聆听
  if (recognizing.value) stopVoiceInput()
  
  // 添加用户消息
  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  loading.value = true
  
  nextTick(() => scrollToBottom())
  
  const ctl = { stopped: false, typeQueue: [] }
  genCtl = ctl
  abortController = new AbortController()

  try {
    // 构建对话历史（不包含欢迎消息）
    const validHistory = messages.value
      .filter((_, i) => i > 0) // 跳过欢迎消息
      .map(m => ({ role: m.role, content: m.content }))

    // 添加一个空的AI消息占位
    const messageKey = typeof crypto !== 'undefined' && crypto.randomUUID
      ? crypto.randomUUID()
      : `${Date.now()}_${Math.random().toString(36).slice(2, 12)}`
    const aiMsg = { role: 'assistant', content: '', messageKey, feedback: null, feedbackLoading: false }
    messages.value.push(aiMsg)
    
    // 获取响应式对象
    const reactiveAiMsg = messages.value[messages.value.length - 1]
    
    // 打字机队列
    const typeQueue = ctl.typeQueue
    let isTyping = false
    
    const typeNext = () => {
      if (typeQueue.length > 0 && !ctl.stopped) {
        isTyping = true
        const char = typeQueue.shift()
        reactiveAiMsg.content += char
        nextTick(() => scrollToBottom())
        
        // 控制打字速度，例如 30ms 一个字
        setTimeout(typeNext, 30)
      } else {
        isTyping = false
      }
    }
    
    await sendMessageStream(validHistory, userRole.value, userName.value, (chunk) => {
      if (ctl.stopped) return // 已停止：忽略后续内容
      // 将接收到的文本拆分为字符推入队列
      const chars = chunk.split('')
      typeQueue.push(...chars)
      
      if (!isTyping) {
        typeNext()
      }
    }, homeworkContext.value, {
      signal: abortController.signal,
      courseId: aiCourseId.value,
      homeworkId: homeworkId.value
    })
    
    // 等待队列清空
    while ((typeQueue.length > 0 || isTyping) && !ctl.stopped) {
      await new Promise(resolve => setTimeout(resolve, 100))
    }

    // 用户主动停止：标注已停止
    if (ctl.stopped) {
      reactiveAiMsg.content += reactiveAiMsg.content ? '\n\n[已停止生成]' : '[已停止生成]'
    }
    
  } catch (error) {
    // 如果出错，且当前AI消息为空，则显示错误信息
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg.role === 'assistant' && !lastMsg.content) {
         lastMsg.content = `抱歉，${error.message || '请求失败，请稍后重试'}`
    } else {
         messages.value.push({ 
          role: 'assistant', 
          content: `\n\n[系统提示: ${error.message || '请求中断'}]` 
        })
    }
  } finally {
    loading.value = false
    genCtl = null
    abortController = null
    nextTick(() => scrollToBottom())
  }
}

// 清空对话历史
const clearHistory = () => {
  initWelcome()
}

const submitFeedback = async (msg, index, rating) => {
  if (!msg || !msg.messageKey || msg.feedbackLoading) return
  let reason = ''
  if (rating === -1) {
    try {
      const result = await ElMessageBox.prompt('请简要说明需要改进的地方（可选）', '回答反馈', {
        confirmButtonText: '提交',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：内容不准确、没有回答问题、解释不清楚',
        inputValidator: value => !value || value.length <= 200 || '最多输入 200 个字符'
      })
      reason = result.value || ''
    } catch (e) {
      return
    }
  }
  let question = ''
  for (let i = index - 1; i >= 0; i--) {
    if (messages.value[i]?.role === 'user') {
      question = messages.value[i].content || ''
      break
    }
  }
  msg.feedbackLoading = true
  try {
    const res = await sendAiFeedback({
      messageKey: msg.messageKey,
      scene: 'assistant.chat',
      rating,
      reason,
      question,
      answer: msg.content
    })
    if (res.code === 200) {
      msg.feedback = rating
      ElMessage.success('感谢反馈')
    } else {
      ElMessage.error(res.msg || '反馈提交失败')
    }
  } catch (e) {
    ElMessage.error('反馈提交失败')
  } finally {
    msg.feedbackLoading = false
  }
}

// --- 语音识别（讯飞语音听写 WebSocket 为主，浏览器 Web Speech 兜底）---
const { speechSupported, recognizing, toggleVoiceInput, stopVoiceInput } = useVoiceInput(inputText)

// 滚动到底部
const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 格式化消息（支持换行和列表）
const formatMessage = (text) => {
  if (!text) return ''
  return escapeHtml(text)
    .replace(/\n/g, '<br>')
    .replace(/•/g, '<span class="bullet">•</span>')
}

// 监听用户变化，重新初始化欢迎语
watch([userRole, userName], () => {
  if (messages.value.length > 0) {
    initWelcome()
  }
})

onMounted(() => {
  loadConfig()
  initWelcome()
  window.addEventListener('resize', () => {
    // 窗口大小改变时确保悬浮球不跑出去
    position.x = Math.min(position.x, window.innerWidth - 60)
    position.y = Math.min(position.y, window.innerHeight - 60)
  })
})

onUnmounted(() => {
  // 清理可能的事件监听（语音识别由 useVoiceInput 自行清理）
  document.removeEventListener('mousemove', onDragMove)
  document.removeEventListener('mouseup', onDragEnd)
  document.removeEventListener('mousemove', onResizeMove)
  document.removeEventListener('mouseup', onResizeEnd)
  document.removeEventListener('touchmove', onTouchDragMove)
  document.removeEventListener('touchend', onTouchDragEnd)
})

</script>

<style scoped>
.ai-assistant-container {
  position: fixed;
  z-index: 9999;
  user-select: none; /* 防止拖拽时选中文本 */
}

/* 悬浮按钮 */
.ai-float-btn {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: grab;
  box-shadow: 0 4px 15px rgba(64, 158, 255, 0.4);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.ai-float-btn:active {
  cursor: grabbing;
}

.ai-float-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.5);
}

.ai-float-btn.is-dragging {
  transition: none; /* 拖拽时取消过渡动画，避免延迟 */
  transform: scale(1.1);
}

.ai-float-btn .btn-text {
  font-size: 10px;
  margin-top: 2px;
}

.ai-float-btn.has-unread::after {
  content: '';
  position: absolute;
  top: 5px;
  right: 5px;
  width: 12px;
  height: 12px;
  background: #f56c6c;
  border-radius: 50%;
  border: 2px solid #fff;
}

/* 聊天窗口 */
.ai-chat-window {
  position: absolute;
  background: var(--zy-bg-surface);
  border-radius: 16px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  /* 默认大小，会被内联样式覆盖 */
  width: 380px;
  height: 520px;
}

/* Resize Handle */
.resize-handle {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 20px;
  height: 20px;
  cursor: nwse-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  background: rgba(255, 255, 255, 0.8);
  border-top-left-radius: 4px;
}

.resize-handle:hover {
  color: #409eff;
}

/* 头部 */
.chat-header {
  padding: 15px 20px;
  background: #409eff;
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
  cursor: move; /* 可拖动光标 */
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}

/* 消息列表 */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 15px;
  display: flex;
  flex-direction: column;
  gap: 15px;
  background: #f5f7fa;
}

.message-item {
  display: flex;
  gap: 10px;
  max-width: 90%;
}

.message-item.user {
  flex-direction: row-reverse;
  align-self: flex-end;
}

.message-item.assistant {
  align-self: flex-start;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--zy-bg-surface);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.user .message-avatar {
  background: #e6f1fc;
  color: #409eff;
}

.assistant .message-avatar {
  background: var(--zy-bg-surface);
  color: var(--zy-success-base);
}

.assistant .message-avatar .iconify {
  color: #67c23a;
}

.message-content {
  background: var(--zy-bg-surface);
  padding: 10px 15px;
  border-radius: 12px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
  font-size: 14px;
  line-height: 1.6;
  color: #303133;
}

.user .message-content {
  background: #409eff;
  color: #fff;
  border-bottom-right-radius: 2px;
}

.assistant .message-content {
  border-bottom-left-radius: 2px;
}

.message-feedback {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 7px;
  padding-top: 5px;
  border-top: 1px solid rgba(144, 147, 153, .18);
  font-size: 11px;
  color: #909399;
}

.message-feedback button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 25px;
  height: 23px;
  padding: 0;
  border: 1px solid var(--zy-border-strong);
  border-radius: 6px;
  color: var(--zy-text-tertiary);
  background: var(--zy-bg-surface);
  cursor: pointer;
}

.message-feedback button:hover,
.message-feedback button.active {
  color: var(--zy-brand-base);
  border-color: var(--zy-brand-base);
  background: var(--zy-brand-soft);
}

.message-feedback button:disabled { cursor: wait; opacity: .6; }
.feedback-done { color: var(--zy-success-base); }

/* 输入区域 */
.chat-input {
  padding: 15px;
  background: var(--zy-bg-surface);
  border-top: 1px solid var(--zy-border-default);
  display: flex;
  gap: 10px;
  align-items: flex-end;
  flex-shrink: 0;
}

.input-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: stretch;
}

/* 侧边助手的输入区是纵向排列，语音按钮要占满整行 */
.mic-btn {
  width: 100%;
  height: 28px;
}

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

/* 加载动画 */
.loading-dots {
  display: flex;
  gap: 4px;
  padding: 5px 0;
}

.loading-dots span {
  width: 6px;
  height: 6px;
  background: #909399;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
}

.loading-dots span:nth-child(1) { animation-delay: -0.32s; }
.loading-dots span:nth-child(2) { animation-delay: -0.16s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

.bullet {
  color: #409eff;
  margin-right: 4px;
  font-weight: bold;
}

/* 移动端适配 */
@media screen and (max-width: 768px) {
  .ai-chat-window {
    width: 95vw !important;
    height: 70vh !important;
    max-height: 80vh;
    position: fixed !important;
    left: 2.5vw !important;
    right: 2.5vw !important;
    bottom: 60px !important;
    top: auto !important;
  }

  .ai-float-btn {
    width: 48px;
    height: 48px;
  }

  .ai-float-btn .btn-text {
    display: none;
  }

  .ai-float-btn .el-icon {
    font-size: 22px !important;
  }

  .resize-handle {
    display: none !important;
  }
}
</style>

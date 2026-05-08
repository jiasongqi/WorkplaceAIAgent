<template>
  <div class="advisor-layout">
    <!-- 历史会话侧边栏 -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <span v-if="!sidebarCollapsed" class="sidebar-title">历史对话</span>
        <button class="collapse-btn" @click="sidebarCollapsed = !sidebarCollapsed">
          {{ sidebarCollapsed ? '›' : '‹' }}
        </button>
      </div>

      <template v-if="!sidebarCollapsed">
        <button class="new-chat-btn" @click="createNewSession">
          <span>＋</span> 新对话
        </button>

        <div class="session-list">
          <div
            v-for="session in sessions"
            :key="session.chatId"
            class="session-item"
            :class="{ active: session.chatId === currentChatId }"
            @click="switchSession(session.chatId)"
          >
            <span class="session-title">{{ session.title }}</span>
            <button class="delete-btn" @click.stop="removeSession(session.chatId)">×</button>
          </div>
          <div v-if="sessions.length === 0" class="empty-sessions">暂无历史对话</div>
        </div>
      </template>
    </aside>

    <!-- 主聊天区域 -->
    <div class="main-area">
      <div class="header">
        <div class="back-button" @click="goBack">← 返回</div>
        <div class="header-center">
          <h1 class="title">💼 职场顾问</h1>
          <div class="agent-badge" :class="currentAgent.type">{{ currentAgent.name }}</div>
        </div>
        <div class="chat-id-display">{{ currentChatId.slice(0, 8) }}...</div>
      </div>

      <div class="chat-messages" ref="messagesContainer">
        <div v-for="(msg, index) in messages" :key="index" class="message-wrapper">
          <!-- 路由提示 -->
          <div v-if="msg.type === 'routing'" class="routing-badge">
            🔀 {{ msg.content }}
          </div>

          <!-- AI 消息 -->
          <div v-else-if="!msg.isUser" class="message ai-message">
            <div class="avatar ai-avatar">🤖</div>
            <div class="message-bubble ai-bubble">
              <div class="message-content" v-html="renderMarkdown(msg.content)"></div>
              <span v-if="isStreaming && index === messages.length - 1" class="typing-cursor">▋</span>
              <div class="message-time">{{ formatTime(msg.time) }}</div>
            </div>
          </div>

          <!-- 用户消息 -->
          <div v-else class="message user-message">
            <div class="message-bubble user-bubble">
              <div class="message-content">{{ msg.content }}</div>
              <div class="message-time">{{ formatTime(msg.time) }}</div>
            </div>
            <div class="avatar user-avatar">我</div>
          </div>
        </div>

        <!-- 思考动画 -->
        <div v-if="isThinking" class="thinking-bubble">
          <div class="avatar ai-avatar">🤖</div>
          <div class="thinking-dots">
            <span></span><span></span><span></span>
          </div>
        </div>
      </div>

      <div class="input-area">
        <textarea
          v-model="inputMessage"
          @keydown.enter.exact.prevent="sendMessage"
          @keydown.shift.enter="inputMessage += '\n'"
          placeholder="描述你的职场困惑... (Enter 发送，Shift+Enter 换行)"
          :disabled="isStreaming"
          rows="2"
        ></textarea>
        <button @click="sendMessage" :disabled="isStreaming || !inputMessage.trim()" class="send-btn">
          {{ isStreaming ? '回复中...' : '发送' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import { marked } from 'marked'
import { login, createSession, listSessions, deleteSession, chatWithOrchestrator } from '../api'

useHead({ title: '职场顾问 - 职场生存智囊' })

const router = useRouter()
const messagesContainer = ref(null)
const inputMessage = ref('')
const messages = ref([])
const sessions = ref([])
const currentChatId = ref('')
const isStreaming = ref(false)
const isThinking = ref(false)
const sidebarCollapsed = ref(false)
const currentAgent = ref({ name: '智能路由中', type: 'general' })

let eventSource = null

// 初始化：登录 + 加载会话
onMounted(async () => {
  await ensureLogin()
  await loadSessions()
  if (sessions.value.length > 0) {
    switchSession(sessions.value[0].chatId)
  } else {
    await createNewSession()
  }
  addMessage('你好！我是职场生存智囊，会根据你的问题自动匹配最合适的专家为你解答。\n\n你可以问我：\n- 📄 简历怎么写才能通过 HR 筛选？\n- 💰 如何跟公司谈涨薪？\n- 🚪 想离职但不知道怎么开口？\n- 📈 如何规划晋升路径？', false)
})

onBeforeUnmount(() => { if (eventSource) eventSource.close() })

const ensureLogin = async () => {
  if (!localStorage.getItem('token')) {
    try {
      const res = await login('游客')
      localStorage.setItem('token', res.data.data.token)
      localStorage.setItem('userId', res.data.data.userId)
    } catch (e) { console.error('登录失败', e) }
  }
}

const loadSessions = async () => {
  try {
    const res = await listSessions()
    sessions.value = res.data.data || []
  } catch (e) { sessions.value = [] }
}

const createNewSession = async () => {
  try {
    const res = await createSession('新对话')
    const session = res.data.data
    sessions.value.unshift(session)
    currentChatId.value = session.chatId
    messages.value = []
  } catch (e) {
    // 降级：本地生成 chatId
    currentChatId.value = 'local_' + Math.random().toString(36).slice(2, 10)
    messages.value = []
  }
}

const switchSession = (chatId) => {
  if (eventSource) { eventSource.close(); isStreaming.value = false }
  currentChatId.value = chatId
  messages.value = []
  addMessage('已切换到该对话，请继续提问。', false)
}

const removeSession = async (chatId) => {
  try {
    await deleteSession(chatId)
    sessions.value = sessions.value.filter(s => s.chatId !== chatId)
    if (currentChatId.value === chatId) {
      if (sessions.value.length > 0) switchSession(sessions.value[0].chatId)
      else await createNewSession()
    }
  } catch (e) { console.error('删除失败', e) }
}

const addMessage = (content, isUser, type = '') => {
  messages.value.push({ content, isUser, type, time: Date.now() })
  scrollToBottom()
}

const sendMessage = () => {
  if (!inputMessage.value.trim() || isStreaming.value) return
  const msg = inputMessage.value.trim()
  inputMessage.value = ''
  addMessage(msg, true)
  isThinking.value = true
  isStreaming.value = true
  currentAgent.value = { name: '分析中...', type: 'general' }

  if (eventSource) eventSource.close()
  eventSource = chatWithOrchestrator(msg, currentChatId.value)

  let aiMsgIndex = -1

  eventSource.addEventListener('routing', (e) => {
    isThinking.value = false
    const routingText = e.data
    addMessage(routingText, false, 'routing')
    // 解析 Agent 名称
    if (routingText.includes('简历')) currentAgent.value = { name: '简历优化专家', type: 'resume' }
    else if (routingText.includes('薪资')) currentAgent.value = { name: '薪资谈判专家', type: 'negotiation' }
    else if (routingText.includes('离职')) currentAgent.value = { name: '离职规划专家', type: 'escape' }
    else currentAgent.value = { name: '职场通用顾问', type: 'general' }
  })

  eventSource.addEventListener('message', (e) => {
    isThinking.value = false
    if (aiMsgIndex === -1) {
      messages.value.push({ content: '', isUser: false, type: '', time: Date.now() })
      aiMsgIndex = messages.value.length - 1
    }
    messages.value[aiMsgIndex].content += e.data
    scrollToBottom()
  })

  eventSource.onmessage = (e) => {
    isThinking.value = false
    if (e.data === '[DONE]') {
      isStreaming.value = false
      eventSource.close()
      return
    }
    if (aiMsgIndex === -1) {
      messages.value.push({ content: '', isUser: false, type: '', time: Date.now() })
      aiMsgIndex = messages.value.length - 1
    }
    messages.value[aiMsgIndex].content += e.data
    scrollToBottom()
  }

  eventSource.onerror = () => {
    isThinking.value = false
    isStreaming.value = false
    eventSource.close()
    if (aiMsgIndex === -1 || !messages.value[aiMsgIndex]?.content) {
      addMessage('连接出现问题，请重试。', false)
    }
  }
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

const goBack = () => router.push('/')

const formatTime = (ts) => new Date(ts).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })

// 完整 Markdown 渲染（marked.js）
const renderMarkdown = (text) => {
  if (!text) return ''
  return marked.parse(text)
}
</script>

<style scoped>
.advisor-layout {
  display: flex;
  height: 100vh;
  background: #f0f2f5;
  overflow: hidden;
}

/* 侧边栏 */
.sidebar {
  width: 260px;
  min-width: 260px;
  background: #1a1f2e;
  display: flex;
  flex-direction: column;
  transition: width 0.3s, min-width 0.3s;
  overflow: hidden;
}

.sidebar.collapsed { width: 48px; min-width: 48px; }

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 12px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
}

.sidebar-title { color: rgba(255,255,255,0.8); font-size: 14px; font-weight: 600; white-space: nowrap; }

.collapse-btn {
  background: none;
  border: none;
  color: rgba(255,255,255,0.6);
  font-size: 18px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.2s;
}
.collapse-btn:hover { background: rgba(255,255,255,0.1); }

.new-chat-btn {
  margin: 12px;
  padding: 10px;
  background: rgba(0, 136, 255, 0.2);
  border: 1px solid rgba(0, 136, 255, 0.4);
  color: #60a5fa;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.2s;
}
.new-chat-btn:hover { background: rgba(0, 136, 255, 0.35); }

.session-list { flex: 1; overflow-y: auto; padding: 0 8px 8px; }

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  color: rgba(255,255,255,0.65);
  font-size: 13px;
  transition: all 0.2s;
  margin-bottom: 2px;
}
.session-item:hover { background: rgba(255,255,255,0.08); color: white; }
.session-item.active { background: rgba(0, 136, 255, 0.2); color: #60a5fa; }

.session-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.delete-btn {
  background: none;
  border: none;
  color: rgba(255,255,255,0.3);
  cursor: pointer;
  font-size: 16px;
  padding: 0 4px;
  opacity: 0;
  transition: opacity 0.2s;
}
.session-item:hover .delete-btn { opacity: 1; }
.delete-btn:hover { color: #f87171; }

.empty-sessions { color: rgba(255,255,255,0.3); font-size: 13px; text-align: center; padding: 20px; }

/* 主区域 */
.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  background: #1e40af;
  color: white;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}

.back-button { cursor: pointer; font-size: 15px; opacity: 0.85; transition: opacity 0.2s; }
.back-button:hover { opacity: 1; }

.header-center { display: flex; align-items: center; gap: 12px; }

.title { font-size: 18px; font-weight: bold; margin: 0; }

.agent-badge {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 12px;
  font-weight: 500;
}
.agent-badge.resume { background: rgba(16, 185, 129, 0.2); color: #34d399; border: 1px solid rgba(16,185,129,0.4); }
.agent-badge.negotiation { background: rgba(245, 158, 11, 0.2); color: #fbbf24; border: 1px solid rgba(245,158,11,0.4); }
.agent-badge.escape { background: rgba(239, 68, 68, 0.2); color: #f87171; border: 1px solid rgba(239,68,68,0.4); }
.agent-badge.general { background: rgba(99, 102, 241, 0.2); color: #a5b4fc; border: 1px solid rgba(99,102,241,0.4); }

.chat-id-display { font-size: 12px; opacity: 0.5; font-family: monospace; }

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.routing-badge {
  text-align: center;
  font-size: 13px;
  color: #6366f1;
  background: rgba(99, 102, 241, 0.08);
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 20px;
  padding: 6px 16px;
  margin: 0 auto;
}

.message { display: flex; align-items: flex-start; gap: 10px; max-width: 80%; }
.ai-message { align-self: flex-start; }
.user-message { align-self: flex-end; flex-direction: row-reverse; }

.avatar {
  width: 36px; height: 36px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}
.user-avatar {
  background: #1e40af;
  color: white;
  font-size: 13px;
  font-weight: bold;
}

.message-bubble { padding: 12px 16px; border-radius: 16px; max-width: 100%; }
.ai-bubble { background: white; color: #1f2937; border-bottom-left-radius: 4px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); }
.user-bubble { background: #1e40af; color: white; border-bottom-right-radius: 4px; }

.message-content { font-size: 15px; line-height: 1.6; word-break: break-word; }

/* marked.js 渲染的 Markdown 元素样式 */
.ai-bubble :deep(h1),
.ai-bubble :deep(h2),
.ai-bubble :deep(h3) { font-weight: 600; margin: 10px 0 6px; color: #111827; }
.ai-bubble :deep(h1) { font-size: 1.2em; }
.ai-bubble :deep(h2) { font-size: 1.1em; }
.ai-bubble :deep(h3) { font-size: 1em; }
.ai-bubble :deep(p) { margin: 6px 0; }
.ai-bubble :deep(ul),
.ai-bubble :deep(ol) { padding-left: 20px; margin: 6px 0; }
.ai-bubble :deep(li) { margin: 3px 0; }
.ai-bubble :deep(strong) { font-weight: 600; color: #111827; }
.ai-bubble :deep(em) { font-style: italic; }
.ai-bubble :deep(code) {
  background: #f3f4f6; border-radius: 4px;
  padding: 1px 5px; font-size: 0.88em; font-family: monospace; color: #374151;
}
.ai-bubble :deep(pre) {
  background: #1f2937; border-radius: 8px;
  padding: 12px 14px; overflow-x: auto; margin: 8px 0;
}
.ai-bubble :deep(pre code) {
  background: none; color: #e5e7eb; padding: 0; font-size: 0.85em;
}
.ai-bubble :deep(blockquote) {
  border-left: 3px solid #d1d5db; padding-left: 12px;
  color: #6b7280; margin: 6px 0;
}
.ai-bubble :deep(hr) { border: none; border-top: 1px solid #e5e7eb; margin: 10px 0; }
.ai-bubble :deep(a) { color: #1e40af; text-decoration: underline; }
.message-time { font-size: 11px; opacity: 0.5; margin-top: 6px; text-align: right; }

.typing-cursor { display: inline-block; animation: blink 0.7s infinite; margin-left: 2px; }
@keyframes blink { 0%, 100% { opacity: 0; } 50% { opacity: 1; } }

/* 思考动画 */
.thinking-bubble { display: flex; align-items: center; gap: 10px; align-self: flex-start; }
.thinking-dots {
  background: white;
  border-radius: 16px;
  border-bottom-left-radius: 4px;
  padding: 14px 18px;
  display: flex;
  gap: 5px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
}
.thinking-dots span {
  width: 8px; height: 8px;
  background: #9ca3af;
  border-radius: 50%;
  animation: bounce 1.2s infinite;
}
.thinking-dots span:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-8px); }
}

/* 输入区域 */
.input-area {
  display: flex;
  gap: 12px;
  padding: 16px 20px;
  background: white;
  border-top: 1px solid #e5e7eb;
  align-items: flex-end;
}

.input-area textarea {
  flex: 1;
  border: 1px solid #d1d5db;
  border-radius: 12px;
  padding: 10px 14px;
  font-size: 15px;
  resize: none;
  outline: none;
  font-family: inherit;
  transition: border-color 0.2s;
  line-height: 1.5;
}
.input-area textarea:focus { border-color: #1e40af; }
.input-area textarea:disabled { background: #f9fafb; }

.send-btn {
  background: #1e40af;
  color: white;
  border: none;
  border-radius: 12px;
  padding: 10px 22px;
  font-size: 15px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
  height: 44px;
}
.send-btn:hover:not(:disabled) { background: #1d4ed8; }
.send-btn:disabled { opacity: 0.5; cursor: not-allowed; }

@media (max-width: 768px) {
  .sidebar { display: none; }
  .header { padding: 12px 16px; }
  .chat-messages { padding: 12px; }
  .message { max-width: 92%; }
}
</style>

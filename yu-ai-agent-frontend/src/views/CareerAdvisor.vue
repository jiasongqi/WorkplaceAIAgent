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
        <div class="header-right">
          <button class="profile-btn" @click="openProfile" title="查看我的画像">
            👤 我的画像
          </button>
          <button class="profile-btn" @click="loadTraceHistory" title="查看执行轨迹历史">
            📊 轨迹
          </button>
          <div class="chat-id-display">{{ currentChatId.slice(0, 8) }}...</div>
        </div>
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

      <!-- Real-time trace panel -->
      <div class="trace-panel" v-if="traceSteps.length > 0">
        <div class="trace-panel-header">
          <span class="trace-panel-title">⚡ 执行轨迹</span>
          <span class="trace-step-count">{{ traceSteps.length }} 步</span>
        </div>
        <div class="trace-steps-list">
          <div
            v-for="step in traceSteps"
            :key="step.sequence"
            class="trace-step"
            :class="step.status.toLowerCase()"
          >
            <div class="trace-step-dot">
              <span v-if="step.status === 'SUCCESS'">✓</span>
              <span v-else-if="step.status === 'RUNNING'" class="spin">⟳</span>
              <span v-else-if="step.status === 'FAILED'">✗</span>
              <span v-else>⊘</span>
            </div>
            <div class="trace-step-info">
              <span class="trace-step-type">{{ step.stepTypeDisplayName || step.stepType }}</span>
              <span class="trace-step-label">{{ step.label }}</span>
            </div>
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

    <!-- 我的画像弹窗 -->
    <div v-if="profileVisible" class="profile-overlay" @click.self="closeProfile">
      <div class="profile-panel">
        <div class="profile-header">
          <h2 class="profile-title">👤 我的画像</h2>
          <button class="profile-close" @click="closeProfile">×</button>
        </div>

        <div class="profile-body">
          <!-- 加载中 -->
          <div v-if="profileLoading" class="profile-loading">画像加载中...</div>

          <!-- 加载失败 -->
          <div v-else-if="profileError" class="profile-empty">{{ profileError }}</div>

          <!-- 无画像 -->
          <div v-else-if="!profile" class="profile-empty">
            暂无画像。多与我对话后，系统会自动学习并构建你的专属画像。
          </div>

          <!-- 画像维度展示 -->
          <div v-else class="profile-content">
            <div class="profile-field">
              <span class="field-label">沟通偏好</span>
              <span class="field-value">{{ communicationPreferenceText }}</span>
            </div>
            <div class="profile-field">
              <span class="field-label">语气偏好</span>
              <span class="field-value">{{ profile.tonePreference || '暂无' }}</span>
            </div>
            <div class="profile-field">
              <span class="field-label">关注领域</span>
              <div class="field-value">
                <template v-if="profile.focusAreas && profile.focusAreas.length">
                  <span v-for="(area, i) in profile.focusAreas" :key="i" class="profile-tag">{{ area }}</span>
                </template>
                <span v-else class="field-empty">暂无</span>
              </div>
            </div>
            <div class="profile-field">
              <span class="field-label">已知背景</span>
              <span class="field-value">{{ profile.knownBackground || '暂无' }}</span>
            </div>
            <div class="profile-field">
              <span class="field-label">历史诉求</span>
              <div class="field-value">
                <template v-if="profile.historicalDemands && profile.historicalDemands.length">
                  <span v-for="(demand, i) in profile.historicalDemands" :key="i" class="profile-tag">{{ demand }}</span>
                </template>
                <span v-else class="field-empty">暂无</span>
              </div>
            </div>
            <div v-if="profile.updatedAt" class="profile-meta">
              最近更新：{{ formatDateTime(profile.updatedAt) }}
            </div>
          </div>

          <!-- 清空结果反馈 -->
          <div v-if="clearFeedback" class="profile-feedback" :class="clearFeedback.type">
            {{ clearFeedback.text }}
          </div>
        </div>

        <div class="profile-footer">
          <button
            class="clear-profile-btn"
            :disabled="profileLoading || clearing || !profile"
            @click="handleClearProfile"
          >
            {{ clearing ? '清空中...' : '🗑 清空画像' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 轨迹历史弹窗 -->
    <div v-if="traceHistoryVisible" class="profile-overlay" @click.self="traceHistoryVisible = false">
      <div class="profile-panel" style="max-width: 560px;">
        <div class="profile-header">
          <h2 class="profile-title">📊 执行轨迹历史</h2>
          <button class="profile-close" @click="traceHistoryVisible = false">×</button>
        </div>
        <div class="profile-body">
          <div v-if="traceHistoryLoading" class="profile-loading">加载中...</div>
          <div v-else-if="traceHistoryList.length === 0" class="profile-empty">暂无轨迹记录</div>
          <div v-else class="trace-history-list">
            <div
              v-for="t in traceHistoryList"
              :key="t.traceId"
              class="trace-history-item"
              @click="$router.push(`/trace/${t.traceId}`); traceHistoryVisible = false"
            >
              <span class="trace-history-status" :class="t.status?.toLowerCase()">
                {{ t.status === 'SUCCESS' ? '✓' : t.status === 'FAILED' ? '✗' : t.status === 'RUNNING' ? '⟳' : '⊘' }}
              </span>
              <span class="trace-history-id">{{ t.traceId?.slice(0, 12) }}...</span>
              <span class="trace-history-spans">{{ t.spans?.length || 0 }} 步</span>
              <span class="trace-history-time">{{ formatTime(t.startTime) }}</span>
            </div>
            <button
              v-if="traceHistoryHasMore && !traceHistoryLoading"
              class="load-more-btn"
              @click="loadMoreTraces"
            >加载更多</button>
            <div v-if="traceHistoryLoading" class="profile-loading" style="padding: 8px 0;">加载中...</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick, computed, shallowReactive } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import { marked } from 'marked'
import { login, createSession, listSessions, deleteSession, chatWithOrchestrator, getMyProfile, clearMyProfile, getTracesByChat } from '../api'

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

// 我的画像
const profileVisible = ref(false)
const profileLoading = ref(false)
const profileError = ref('')
const profile = ref(null)
const clearing = ref(false)
const clearFeedback = ref(null)

// 实时轨迹
const traceMap = shallowReactive(new Map())
const traceSteps = computed(() =>
  Array.from(traceMap.values()).sort((a, b) => a.sequence - b.sequence)
)
const traceVisible = ref(false)
const traceStatus = ref('')
const traceRequestId = ref('')

// 历史轨迹列表
const traceHistoryVisible = ref(false)
const traceHistoryList = ref([])
const traceHistoryLoading = ref(false)
const traceHistoryPage = ref(1)
const traceHistoryHasMore = ref(true)

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

  // Reset trace state for new request
  traceMap.clear()
  traceVisible.value = true
  traceRequestId.value = ''

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

  // Real-time trace events
  eventSource.addEventListener('trace', (e) => {
    try {
      const data = JSON.parse(e.data)
      switch (data.type) {
        case 'TRACE_STARTED':
          traceStatus.value = 'RUNNING'
          break
        case 'SPAN_STARTED':
          traceMap.set(data.sequence, {
            sequence: data.sequence,
            stepType: data.stepType,
            stepTypeDisplayName: data.stepTypeDisplayName,
            label: data.label,
            status: 'RUNNING',
            errorMessage: null,
            startTime: new Date().toISOString()
          })
          break
        case 'SPAN_ENDED': {
          const span = traceMap.get(data.sequence)
          if (span) {
            span.status = data.status
            span.errorMessage = data.errorMessage
            span.endTime = new Date().toISOString()
          }
          break
        }
        case 'TRACE_COMPLETED':
          traceStatus.value = 'SUCCESS'
          break
        case 'TRACE_FAILED':
          traceStatus.value = 'FAILED'
          break
        default:
          break
      }
    } catch (err) {
      console.warn('Failed to parse trace event', err)
    }
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
      traceVisible.value = false
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

// Load trace history for current chat
const loadTraceHistory = async (append = false) => {
  if (traceHistoryLoading.value) return
  traceHistoryLoading.value = true
  traceHistoryVisible.value = true
  if (!append) {
    traceHistoryPage.value = 1
    traceHistoryHasMore.value = true
  }
  try {
    const res = await getTracesByChat(currentChatId.value, traceHistoryPage.value, 20)
    const items = res.data?.data || []
    if (append) {
      traceHistoryList.value.push(...items)
    } else {
      traceHistoryList.value = items
    }
    traceHistoryHasMore.value = items.length >= 20
  } catch (e) {
    console.error('加载轨迹历史失败', e)
    if (!append) traceHistoryList.value = []
  } finally {
    traceHistoryLoading.value = false
  }
}

const loadMoreTraces = () => {
  traceHistoryPage.value++
  loadTraceHistory(true)
}

const goBack = () => router.push('/')

const formatTime = (ts) => new Date(ts).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })

// 格式化画像更新时间（后端返回 ISO 字符串或时间数组）
const formatDateTime = (value) => {
  const d = new Date(value)
  if (isNaN(d.getTime())) return value
  return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

// 沟通偏好枚举 → 中文
const communicationPreferenceText = computed(() => {
  const pref = profile.value?.communicationPreference
  if (pref === 'CONCISE') return '简洁'
  if (pref === 'DETAILED') return '详细'
  return '暂无'
})

// 打开画像弹窗并加载画像
const openProfile = async () => {
  profileVisible.value = true
  clearFeedback.value = null
  profileError.value = ''
  profileLoading.value = true
  try {
    await ensureLogin()
    const res = await getMyProfile()
    // 后端 Result<UserProfile>，无画像时 data 为 null
    profile.value = res.data?.data || null
  } catch (e) {
    console.error('加载画像失败', e)
    profileError.value = '加载画像失败，请稍后重试。'
    profile.value = null
  } finally {
    profileLoading.value = false
  }
}

const closeProfile = () => {
  profileVisible.value = false
}

// 清空画像（确认后调用 DELETE /profile/me）
const handleClearProfile = async () => {
  if (clearing.value) return
  if (!window.confirm('确定要清空你的画像吗？此操作不可恢复。')) return
  clearing.value = true
  clearFeedback.value = null
  try {
    await clearMyProfile()
    profile.value = null
    clearFeedback.value = { type: 'success', text: '✓ 画像已清空' }
  } catch (e) {
    console.error('清空画像失败', e)
    clearFeedback.value = { type: 'error', text: '清空失败，请稍后重试。' }
  } finally {
    clearing.value = false
  }
}

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

/* 头部右侧区 + 我的画像入口 */
.header-right { display: flex; align-items: center; gap: 14px; }

.profile-btn {
  background: rgba(255,255,255,0.12);
  border: 1px solid rgba(255,255,255,0.25);
  color: white;
  border-radius: 16px;
  padding: 5px 14px;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.2s;
}
.profile-btn:hover { background: rgba(255,255,255,0.22); }

/* 画像弹窗 */
.profile-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.profile-panel {
  width: 480px;
  max-width: 92vw;
  max-height: 86vh;
  background: white;
  border-radius: 16px;
  box-shadow: 0 12px 40px rgba(0,0,0,0.25);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.profile-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: #1e40af;
  color: white;
}

.profile-title { font-size: 17px; font-weight: bold; margin: 0; }

.profile-close {
  background: none;
  border: none;
  color: rgba(255,255,255,0.85);
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
  padding: 0 4px;
  transition: color 0.2s;
}
.profile-close:hover { color: white; }

.profile-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}

.profile-loading,
.profile-empty {
  text-align: center;
  color: #6b7280;
  font-size: 14px;
  padding: 32px 12px;
  line-height: 1.6;
}

.profile-content { display: flex; flex-direction: column; gap: 16px; }

.profile-field {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.field-label {
  flex-shrink: 0;
  width: 72px;
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  padding-top: 2px;
}

.field-value {
  flex: 1;
  font-size: 14px;
  color: #1f2937;
  line-height: 1.6;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.field-empty { color: #9ca3af; }

.profile-tag {
  background: rgba(30, 64, 175, 0.08);
  border: 1px solid rgba(30, 64, 175, 0.2);
  color: #1e40af;
  border-radius: 12px;
  padding: 2px 10px;
  font-size: 13px;
}

.profile-meta {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 4px;
  border-top: 1px solid #f3f4f6;
  padding-top: 12px;
}

.profile-feedback {
  margin-top: 16px;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 13px;
  text-align: center;
}
.profile-feedback.success { background: rgba(16, 185, 129, 0.1); color: #059669; border: 1px solid rgba(16,185,129,0.3); }
.profile-feedback.error { background: rgba(239, 68, 68, 0.1); color: #dc2626; border: 1px solid rgba(239,68,68,0.3); }

.profile-footer {
  padding: 14px 20px;
  border-top: 1px solid #e5e7eb;
  display: flex;
  justify-content: flex-end;
}

.clear-profile-btn {
  background: white;
  color: #dc2626;
  border: 1px solid rgba(239, 68, 68, 0.4);
  border-radius: 10px;
  padding: 9px 18px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}
.clear-profile-btn:hover:not(:disabled) { background: rgba(239, 68, 68, 0.08); }
.clear-profile-btn:disabled { opacity: 0.45; cursor: not-allowed; }

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

/* Trace panel */
.trace-panel {
  background: #1a1f2e;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  max-height: 200px;
  overflow-y: auto;
}
.trace-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.trace-panel-title { color: white; font-size: 13px; font-weight: 600; }
.trace-step-count { color: rgba(255, 255, 255, 0.4); font-size: 11px; }
.trace-steps-list { padding: 8px 16px; display: flex; flex-direction: column; gap: 4px; }
.trace-step {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}
.trace-step-dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  flex-shrink: 0;
  background: rgba(99, 102, 241, 0.2);
  color: #a5b4fc;
}
.trace-step.success .trace-step-dot { background: rgba(16, 185, 129, 0.3); color: #34d399; }
.trace-step.running .trace-step-dot { background: rgba(99, 102, 241, 0.3); color: #818cf8; }
.trace-step.failed .trace-step-dot { background: rgba(239, 68, 68, 0.3); color: #f87171; }
.trace-step-info { display: flex; gap: 6px; align-items: center; }
.trace-step-type {
  font-size: 11px;
  color: #60a5fa;
  background: rgba(96, 165, 250, 0.1);
  padding: 1px 6px;
  border-radius: 4px;
}
.trace-step-label { font-size: 12px; color: rgba(255, 255, 255, 0.6); }

.spin { display: inline-block; animation: spin 1s linear infinite; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

@media (max-width: 768px) {
  .sidebar { display: none; }
  .header { padding: 12px 16px; }
  .chat-messages { padding: 12px; }
  .message { max-width: 92%; }
  .trace-panel { display: none; }
}

/* Trace history list */
.trace-history-list { display: flex; flex-direction: column; gap: 8px; }
.trace-history-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}
.trace-history-item:hover { border-color: #93c5fd; background: #f8fafc; }
.trace-history-status {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  flex-shrink: 0;
  background: rgba(99, 102, 241, 0.15);
  color: #6366f1;
}
.trace-history-status.success { background: rgba(16, 185, 129, 0.15); color: #059669; }
.trace-history-status.failed { background: rgba(239, 68, 68, 0.15); color: #dc2626; }
.trace-history-status.running { background: rgba(99, 102, 241, 0.2); color: #6366f1; }
.trace-history-id { font-size: 12px; font-family: monospace; color: #6b7280; }
.trace-history-spans { font-size: 11px; color: #9ca3af; }
.trace-history-time { font-size: 11px; color: #9ca3af; margin-left: auto; }
.load-more-btn {
  width: 100%;
  padding: 8px;
  margin-top: 4px;
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  font-size: 13px;
  color: #374151;
  cursor: pointer;
  transition: all 0.2s;
}
.load-more-btn:hover { background: #e5e7eb; }
</style>

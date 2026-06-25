<template>
  <div class="chat-layout">
    <!-- Sidebar -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="side-head">
        <h2 v-if="!sidebarCollapsed">对话</h2>
        <button class="tb-btn" @click="createNewSession" title="新对话">+</button>
        <button class="collapse-toggle" @click="sidebarCollapsed = !sidebarCollapsed">
          {{ sidebarCollapsed ? '›' : '‹' }}
        </button>
      </div>

      <template v-if="!sidebarCollapsed">
        <div class="side-search">
          <span class="s-icon">🔍</span>
          <input v-model="searchKeyword" placeholder="搜索对话…" @input="onSearchInput" />
          <button v-if="searchKeyword" class="search-clear" @click="clearSearch">×</button>
        </div>

        <!-- Search results -->
        <div v-if="searchMode" class="conv-list">
          <div v-if="isSearching" class="search-status">搜索中...</div>
          <div v-else-if="searchResults.length === 0" class="search-status">无匹配结果</div>
          <div v-else v-for="result in searchResults" :key="result.chatId"
            class="conv" @click="switchSession(result.chatId); clearSearch()">
            <div class="c-title">{{ result.title }}</div>
            <div class="c-meta"><span>{{ result.relevance }}%</span></div>
          </div>
        </div>

        <!-- Session list with time groups -->
        <div v-if="!searchMode" class="conv-list">
          <template v-for="group in sessionGroups" :key="group.label">
            <div class="conv-group-label">{{ group.label }}</div>
            <div v-for="session in group.sessions" :key="session.chatId"
              class="conv" :class="{ on: session.chatId === currentChatId }"
              @click="switchSession(session.chatId)">
              <div class="c-title">
                <span v-if="!session.editing">{{ session.title }}</span>
                <input v-else v-model="session.newTitle" class="rename-input"
                  @blur="saveRename(session)" @keyup.enter="saveRename(session)"
                  @keyup.escape="session.editing = false" @click.stop />
              </div>
              <div class="c-meta">
                <span>{{ formatTimeAgo(session.createTime) }}</span>
                <div class="c-actions" @click.stop>
                  <button class="c-act" @click="startRename(session)" title="重命名">✏️</button>
                  <button class="c-act" @click="handleArchive(session.chatId)" title="归档">📦</button>
                  <button class="c-act" @click="removeSession(session.chatId)" title="删除">×</button>
                </div>
              </div>
            </div>
          </template>
          <div v-if="sessions.length === 0" class="empty-conv">暂无历史对话</div>
        </div>

        <!-- Undo delete toast -->
        <Transition name="toast">
          <div v-if="undoToast" class="undo-toast">
            <span>已移入回收站</span>
            <button @click="handleUndoDelete">撤销</button>
          </div>
        </Transition>

        <!-- Archived -->
        <div class="archived-section">
          <button class="archived-toggle" @click="toggleArchived">
            {{ showArchived ? '▼' : '▶' }} 归档会话
          </button>
          <div v-if="showArchived" class="conv-list archived-list">
            <div v-for="session in archivedSessions" :key="session.chatId" class="conv archived">
              <div class="c-title">{{ session.title }}</div>
              <div class="c-meta">
                <div class="c-actions" @click.stop>
                  <button class="c-act" @click="handleUnarchive(session.chatId)">📤</button>
                  <button class="c-act" @click="removeSession(session.chatId)">×</button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
    </aside>

    <!-- Chat core -->
    <div class="chat-core">
      <div class="chat-top">
        <button class="back-mobile" @click="$router.push('/')">←</button>
        <div class="chat-top-info">
          <h3>{{ currentSessionTitle }}</h3>
          <div class="agent-tag">{{ currentAgent.name }}</div>
        </div>
        <div class="chat-top-actions">
          <button class="tb-btn" @click="openProfile" title="画像">👤</button>
          <div class="more-menu-wrap">
            <button class="tb-btn" @click="showMoreMenu = !showMoreMenu" title="更多功能">⋯</button>
            <div v-if="showMoreMenu" class="more-menu" @click="showMoreMenu = false">
              <div class="menu-item" @click="openProfile">👤 我的画像</div>
              <div class="menu-item" @click="$router.push('/favorites')">⭐ 我的收藏</div>
              <div class="menu-item" @click="$router.push('/usage')">📊 使用统计</div>
              <div class="menu-item" @click="$router.push('/artifacts')">📦 交付物</div>
              <div class="menu-item" @click="$router.push('/knowledge')">📚 知识库</div>
              <div class="menu-item" @click="$router.push('/chat/super')">🤖 超级智能体</div>
            </div>
          </div>
        </div>
      </div>

      <!-- Messages -->
      <div class="msgs" ref="messagesContainer">
        <!-- AI Presence (welcome) -->
        <div v-if="messages.length === 0 && !isThinking && !isLoadingHistory" class="presence">
          <div class="ai-orb"></div>
          <div class="presence-body">
            <div class="presence-text">
              你好，我在这里。<br><br>
              不管是想聊聊工作上的烦恼，还是需要具体的建议，<br>
              <b>你可以放心说真话</b>。<br><br>
              我会帮你<span class="hl">慢慢理一下思路</span>，不急。
              <div class="presence-privacy">🔒 对话仅存储在本地，不会分享给第三方</div>
            </div>
          </div>
        </div>

        <!-- Message rows -->
        <div v-for="(msg, index) in messages" :key="index" class="msg-wrapper"
          @mouseenter="hoveredMsg = index" @mouseleave="hoveredMsg = -1">
          <!-- Routing badge with explanation -->
          <div v-if="msg.type === 'routing'" class="routing-badge">
            <span class="routing-text">{{ msg.content }}</span>
            <span class="routing-hint" v-if="msg.content.includes('路由到')">判断不对？直接告诉我就好</span>
          </div>
          <!-- Clarification -->
          <div v-else-if="msg.type === 'clarification'" class="msg-row ai">
            <div class="msg-av">❓</div>
            <div class="msg-bub" v-html="renderMarkdown(msg.content)"></div>
          </div>
          <!-- AI message -->
          <div v-else-if="!msg.isUser" class="msg-row ai" :class="'agent-' + (msg.agentType || 'GENERAL').toLowerCase()">
            <div class="msg-av">{{ getAgentAvatar(msg.agentType) }}</div>
            <div>
              <div v-if="msg.agentName" class="agent-label">{{ msg.agentName }}</div>
              <div class="msg-bub" v-html="renderMarkdown(msg.content)"></div>
              <div class="msg-actions" v-if="hoveredMsg === index">
                <button class="msg-act" @click="copyMessage(msg.content)" title="复制">📋</button>
                <button class="msg-act" @click="toggleFavorite(msg, index)">
                  {{ msg.favorited ? '⭐' : '☆' }}
                </button>
              </div>
            </div>
          </div>
          <!-- User message -->
          <div v-else class="msg-row you">
            <div class="msg-bub">{{ msg.content }}</div>
            <div class="msg-actions" v-if="hoveredMsg === index">
              <button class="msg-act" @click="toggleFavorite(msg, index)">
                {{ msg.favorited ? '⭐' : '☆' }}
              </button>
            </div>
          </div>
        </div>

        <!-- Typing indicator -->
        <div v-if="isThinking" class="typing">
          <div class="msg-av">🟡</div>
          <div class="typing-dots"><b></b><b></b><b></b></div>
          <span class="typing-label">{{ thinkingLabel }}</span>
        </div>

        <!-- Skeleton loading -->
        <template v-if="isLoadingHistory">
          <div v-for="i in 3" :key="'skel-'+i" class="skeleton-msg" :class="i % 2 === 0 ? 'skel-right' : ''">
            <div class="skel-av"></div>
            <div class="skel-bub"><div class="skel-line" :style="{ width: (50 + i*12) + '%' }"></div><div class="skel-line short"></div></div>
          </div>
        </template>
      </div>

      <!-- Trace strip -->
      <div v-if="traceVisible && traceSteps.length > 0" class="trace-strip">
        <TraceTimelineView :trace="{ status: traceStatus || 'RUNNING', spans: traceSteps }" />
      </div>

      <!-- Quality blocked -->
      <div v-if="qualityBlocked" class="quality-blocked">
        <span>🚫 {{ qualityBlocked }}</span>
      </div>

      <!-- Quality review (user-friendly) -->
      <div v-if="qualityReview && !qualityBlocked" class="quality-strip" :class="'risk-' + (qualityReview.riskLevel || '').toLowerCase()">
        <span class="quality-icon">{{ qualityIcon }}</span>
        <span class="quality-text">{{ qualityText }}</span>
        <button class="quality-detail-btn" @click="showQualityDetail = !showQualityDetail">详情</button>
        <div v-if="showQualityDetail" class="quality-detail-panel">
          <div class="qd-row"><span class="qd-label">模式</span><span>{{ qualityReview.mode || 'REVIEW' }}</span></div>
          <div class="qd-row"><span class="qd-label">准确性</span><span class="qd-bar"><span class="qd-fill" :style="{ width: qualityReview.accuracyScore + '%' }"></span></span><span>{{ qualityReview.accuracyScore }}%</span></div>
          <div class="qd-row"><span class="qd-label">完整性</span><span class="qd-bar"><span class="qd-fill" :style="{ width: qualityReview.completenessScore + '%' }"></span></span><span>{{ qualityReview.completenessScore }}%</span></div>
          <div class="qd-row"><span class="qd-label">逻辑性</span><span class="qd-bar"><span class="qd-fill" :style="{ width: qualityReview.logicScore + '%' }"></span></span><span>{{ qualityReview.logicScore }}%</span></div>
          <div class="qd-row"><span class="qd-label">幻觉风险</span><span class="qd-bar"><span class="qd-fill warn" :style="{ width: qualityReview.hallucinationScore + '%' }"></span></span><span>{{ qualityReview.hallucinationScore }}%</span></div>
          <div v-if="qualityReview.issues?.length" class="qd-issues">
            <span class="qd-label">发现问题</span>
            <ul><li v-for="(issue, i) in qualityReview.issues" :key="i">{{ issue }}</li></ul>
          </div>
          <div v-if="qualityReview.suggestions?.length" class="qd-suggestions">
            <span class="qd-label">改进建议</span>
            <ul><li v-for="(sug, i) in qualityReview.suggestions" :key="i">{{ sug }}</li></ul>
          </div>
        </div>
      </div>

      <!-- Chat input bar -->
      <div class="chat-bar">
        <div v-if="attachedFile" class="attached-file">
          <span>📎 {{ attachedFile.name }}</span>
          <button @click="attachedFile = null">×</button>
        </div>
        <div class="chat-bar-wrap" :class="{ focused: barFocused }">
          <textarea
            v-model="inputMessage"
            @keydown.enter.exact.prevent="sendMessage"
            @keydown.shift.enter="inputMessage += '\n'"
            @focus="barFocused = true"
            @blur="barFocused = false"
            placeholder="继续聊…"
            :disabled="isStreaming"
            rows="1"
          ></textarea>
          <input ref="fileInput" type="file" accept=".pdf,.doc,.docx,.txt,.md,.png,.jpg,.jpeg" style="display:none" @change="onFileSelected" />
          <button class="bar-btn" @click="$refs.fileInput.click()" :disabled="isStreaming" title="上传文件">📎</button>
          <button v-if="speechSupported" class="bar-btn" :class="{ listening: isListening }" @click="toggleVoice" :disabled="isStreaming">{{ isListening ? '⏹' : '🎤' }}</button>
          <button v-if="isStreaming" class="chat-stop" @click="stopGeneration" title="停止生成">⏹</button>
          <button v-else class="chat-send" @click="sendMessage" :disabled="!inputMessage.trim() && !attachedFile">→</button>
        </div>
      </div>
    </div>

    <!-- Artifact preview panel -->
    <div class="panel" :class="{ show: latestArtifact }">
      <div v-if="latestArtifact" class="panel-in">
        <div class="panel-head">
          <h3>{{ latestArtifact.title || '交付物' }}</h3>
          <button class="tb-btn" @click="latestArtifact = null">×</button>
        </div>
        <div class="panel-content" v-html="renderMarkdown(latestArtifact.content || '等待生成...')"></div>
      </div>
    </div>

    <!-- Profile overlay -->
    <div v-if="profileVisible" class="overlay" @click.self="closeProfile">
      <div class="overlay-panel">
        <div class="overlay-header">
          <h2>👤 我的画像</h2>
          <button class="overlay-close" @click="closeProfile">×</button>
        </div>
        <div class="overlay-body">
          <div v-if="profileLoading" class="overlay-loading">画像加载中...</div>
          <div v-else-if="profileError" class="overlay-empty">{{ profileError }}</div>
          <div v-else-if="!profile" class="overlay-empty">暂无画像。多与 AI 对话后会自动构建。</div>
          <div v-else class="profile-content">
            <div class="profile-field"><span class="field-label">沟通偏好</span><span class="field-value">{{ communicationPreferenceText }}</span></div>
            <div class="profile-field"><span class="field-label">语气偏好</span><span class="field-value">{{ profile.tonePreference || '暂无' }}</span></div>
            <div class="profile-field">
              <span class="field-label">关注领域</span>
              <div class="field-tags">
                <span v-if="profile.focusAreas?.length" v-for="(a, i) in profile.focusAreas" :key="i" class="field-tag">{{ a }}</span>
                <span v-else class="field-empty">暂无</span>
              </div>
            </div>
            <div class="profile-field">
              <span class="field-label">核心诉求</span>
              <div class="field-tags">
                <span v-if="profile.coreNeeds?.length" v-for="(n, i) in profile.coreNeeds" :key="i" class="field-tag">{{ n }}</span>
                <span v-else class="field-empty">暂无</span>
              </div>
            </div>
          </div>
        </div>
        <div class="overlay-footer">
          <button class="clear-btn" @click="handleClearProfile" :disabled="clearing">{{ clearing ? '清空中...' : '清空画像' }}</button>
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
import TraceTimelineView from '../components/TraceTimelineView.vue'
import {
  login, createSession, listSessions, deleteSession, chatWithOrchestrator,
  getMyProfile, clearMyProfile, getChatMessages, renameSession,
  archiveSession, listArchivedSessions, searchSessions, uploadDocument,
  addFavorite, removeFavorite, unarchiveSession
} from '../api'

useHead({ title: '职场顾问 - WorkPilot' })

const router = useRouter()
const messagesContainer = ref(null)
const inputMessage = ref('')
const barFocused = ref(false)
const messages = ref([])
const sessions = ref([])
const currentChatId = ref('')
const isStreaming = ref(false)
const isThinking = ref(false)
const sidebarCollapsed = ref(false)
const currentAgent = ref({ name: '智能路由中', type: 'general' })
const hoveredMsg = ref(-1)
const showMoreMenu = ref(false)
const isLoadingHistory = ref(false)
const thinkingLabel = ref('正在分析…')
const undoToast = ref(null)
let undoTimer = null

// Profile
const profileVisible = ref(false)
const profileLoading = ref(false)
const profileError = ref('')
const profile = ref(null)
const clearing = ref(false)

// Trace
const traceMap = shallowReactive(new Map())
const traceSteps = computed(() =>
  Array.from(traceMap.values()).sort((a, b) => a.sequence - b.sequence)
)
const traceVisible = ref(false)
const traceStatus = ref('')

// Quality
const qualityReview = ref(null)
const qualityBlocked = ref(null)
const showQualityDetail = ref(false)

// Artifact
const latestArtifact = ref(null)

// File upload
const attachedFile = ref(null)
const fileInput = ref(null)

// Search
const searchKeyword = ref('')
const searchResults = ref([])
const isSearching = ref(false)
const searchMode = ref(false)

// Archived
const archivedSessions = ref([])
const showArchived = ref(false)

// Voice
const speechSupported = ref('webkitSpeechRecognition' in window || 'SpeechRecognition' in window)
const isListening = ref(false)
let recognition = null

let eventSource = null

const currentSessionTitle = computed(() => {
  const s = sessions.value.find(s => s.chatId === currentChatId.value)
  return s?.title || '新对话'
})

const communicationPreferenceText = computed(() => {
  const pref = profile.value?.communicationPreference
  if (pref === 'CONCISE') return '简洁'
  if (pref === 'DETAILED') return '详细'
  return '暂无'
})

// Quality review user-friendly text
const qualityIcon = computed(() => {
  const level = qualityReview.value?.riskLevel
  if (level === 'LOW') return '✅'
  if (level === 'MEDIUM') return '⚠️'
  return '🚫'
})
const qualityText = computed(() => {
  const level = qualityReview.value?.riskLevel
  if (level === 'LOW') return '回答可靠'
  if (level === 'MEDIUM') return '仅供参考，建议交叉验证'
  if (level === 'HIGH') return '该建议风险较高，请谨慎采纳'
  if (level === 'CRITICAL') return '回答存在严重风险，不建议直接采纳'
  return ''
})

// Session time grouping
const sessionGroups = computed(() => {
  const now = Date.now()
  const day = 86400000
  const groups = [
    { label: '今天', sessions: [] },
    { label: '最近 7 天', sessions: [] },
    { label: '更早', sessions: [] },
  ]
  for (const s of sessions.value) {
    const t = new Date(s.createTime || s.lastActiveAt).getTime()
    const diff = now - t
    if (diff < day) groups[0].sessions.push(s)
    else if (diff < 7 * day) groups[1].sessions.push(s)
    else groups[2].sessions.push(s)
  }
  return groups.filter(g => g.sessions.length > 0)
})

// Init
onMounted(async () => {
  try {
    await ensureLogin()
    await loadSessions()

    // Check if resuming a session via query param
    const route = router.currentRoute.value
    const resumeId = route.query.chatId
    if (resumeId) {
      await switchSession(resumeId)
    } else if (sessions.value.length > 0) {
      await switchSession(sessions.value[0].chatId)
    } else {
      await createNewSession()
    }

    // Check for pending message from Home (via query param)
    const pendingMsg = route.query.msg
    if (pendingMsg) {
      inputMessage.value = pendingMsg
      // Clean up URL without re-triggering navigation
      router.replace({ path: '/chat/career' })
    }
  } catch (e) {
    console.error('初始化失败', e)
  }
})

onBeforeUnmount(() => { if (eventSource) eventSource.close() })

const ensureLogin = async () => {
  // Always try to login fresh to get a valid token (handles server restart)
  try {
    const oldUserId = localStorage.getItem('userId')
    const res = await login('游客', oldUserId)
    localStorage.setItem('token', res.data.data.token)
    localStorage.setItem('userId', res.data.data.userId)
  } catch (e) { console.error('登录失败', e) }
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
    currentChatId.value = 'local_' + Math.random().toString(36).slice(2, 10)
    messages.value = []
  }
}

const switchSession = async (chatId) => {
  if (eventSource) { eventSource.close(); isStreaming.value = false }
  currentChatId.value = chatId
  messages.value = []
  isLoadingHistory.value = true
  try {
    const res = await getChatMessages(chatId)
    const history = res.data?.data || []
    if (history.length > 0) {
      messages.value = history.map(m => ({
        content: m.content,
        isUser: m.role === 'user',
        type: '',
        time: m.timestamp,
        agentType: m.sourceType || (m.role === 'user' ? null : 'GENERAL'),
        agentName: m.sourceName || null
      }))
    }
  } catch (e) {
    console.error('加载历史消息失败', e)
  } finally {
    isLoadingHistory.value = false
  }
}

const removeSession = async (chatId) => {
  try {
    await deleteSession(chatId)
    const removedSession = sessions.value.find(s => s.chatId === chatId)
    sessions.value = sessions.value.filter(s => s.chatId !== chatId)
    if (currentChatId.value === chatId) {
      if (sessions.value.length > 0) switchSession(sessions.value[0].chatId)
      else await createNewSession()
    }
    // Show undo toast
    undoToast.value = { chatId, title: removedSession?.title }
    clearTimeout(undoTimer)
    undoTimer = setTimeout(() => { undoToast.value = null }, 5000)
  } catch (e) { console.error('删除失败', e) }
}

const handleUndoDelete = async () => {
  if (!undoToast.value) return
  try {
    const { restoreSession } = await import('../api')
    await restoreSession(undoToast.value.chatId)
    await loadSessions()
    undoToast.value = null
  } catch (e) { console.error('撤销失败', e) }
}

// Rename
const startRename = (session) => {
  session.editing = true
  session.newTitle = session.title
}
const saveRename = async (session) => {
  if (!session.newTitle || session.newTitle.trim() === session.title) {
    session.editing = false; return
  }
  try {
    await renameSession(session.chatId, session.newTitle.trim())
    session.title = session.newTitle.trim()
  } catch (e) { console.error('重命名失败', e) }
  session.editing = false
}

// Archive
const handleArchive = async (chatId) => {
  try {
    await archiveSession(chatId)
    sessions.value = sessions.value.filter(s => s.chatId !== chatId)
    if (currentChatId.value === chatId) {
      if (sessions.value.length > 0) switchSession(sessions.value[0].chatId)
      else await createNewSession()
    }
  } catch (e) { console.error('归档失败', e) }
}

const toggleArchived = async () => {
  if (!showArchived.value) {
    try {
      const res = await listArchivedSessions()
      archivedSessions.value = res.data?.data || []
    } catch (e) { archivedSessions.value = [] }
  }
  showArchived.value = !showArchived.value
}

const handleUnarchive = async (chatId) => {
  try {
    await unarchiveSession(chatId)
    archivedSessions.value = archivedSessions.value.filter(s => s.chatId !== chatId)
    const res = await listSessions()
    sessions.value = res.data?.data || []
  } catch (e) { console.error('取消归档失败', e) }
}

// Search
let searchTimer = null
const onSearchInput = () => {
  clearTimeout(searchTimer)
  if (!searchKeyword.value.trim()) { searchMode.value = false; searchResults.value = []; return }
  searchTimer = setTimeout(doSearch, 300)
}
const doSearch = async () => {
  const kw = searchKeyword.value.trim()
  if (!kw) return
  isSearching.value = true; searchMode.value = true
  try {
    const res = await searchSessions(kw)
    searchResults.value = res.data?.data || []
  } catch (e) { searchResults.value = [] }
  finally { isSearching.value = false }
}
const clearSearch = () => {
  searchKeyword.value = ''; searchResults.value = []; searchMode.value = false
}

// File upload
const onFileSelected = (e) => {
  const file = e.target.files[0]
  if (!file) return
  if (file.size > 10 * 1024 * 1024) { alert('文件不能超过 10MB'); return }
  attachedFile.value = file
  e.target.value = ''
}

// Favorite
const toggleFavorite = async (msg, index) => {
  if (msg.favorited && msg.favoriteId) {
    try { await removeFavorite(msg.favoriteId); msg.favorited = false; msg.favoriteId = null }
    catch (e) { console.error('取消收藏失败', e) }
  } else {
    try {
      const res = await addFavorite(currentChatId.value, msg.messageId || '', msg.content, msg.isUser ? 'user' : 'assistant')
      msg.favorited = true; msg.favoriteId = res.data?.data?.favoriteId
    } catch (e) { console.error('收藏失败', e) }
  }
}

// Voice
const toggleVoice = () => { isListening.value ? stopVoice() : startVoice() }
const startVoice = () => {
  if (!speechSupported.value) return
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition
  recognition = new SR()
  recognition.lang = 'zh-CN'; recognition.continuous = false; recognition.interimResults = true
  recognition.onresult = (e) => {
    inputMessage.value = Array.from(e.results).map(r => r[0].transcript).join('')
  }
  recognition.onerror = () => { isListening.value = false }
  recognition.onend = () => { isListening.value = false }
  recognition.start(); isListening.value = true
}
const stopVoice = () => { if (recognition) { recognition.stop(); recognition = null } isListening.value = false }

// Helpers
const addMessage = (content, isUser, type = '') => {
  messages.value.push({ content, isUser, type, time: Date.now() })
  scrollToBottom()
}
const getAgentAvatar = (agentType) => {
  const m = { RESUME: '📄', NEGOTIATION: '💰', ESCAPE: '🚪', CONSULTATION: '📅', DATA_QUERY: '📊', GENERAL: '🟡' }
  return m[agentType] || '🟡'
}
const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
}
const renderMarkdown = (text) => text ? marked.parse(text) : ''
const formatTimeAgo = (val) => {
  if (!val) return ''
  const d = new Date(val); if (isNaN(d.getTime())) return ''
  const diff = Date.now() - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

// Stop generation
const stopGeneration = () => {
  if (eventSource) { eventSource.close(); eventSource = null }
  isStreaming.value = false
  isThinking.value = false
  traceVisible.value = false
  // Append interruption marker to last AI message
  const lastMsg = messages.value[messages.value.length - 1]
  if (lastMsg && !lastMsg.isUser && lastMsg.content) {
    lastMsg.content += '\n\n…（已停止生成）'
  }
}

// Copy message content
const copyMessage = async (content) => {
  try {
    // Strip markdown for plain text copy
    const plain = content.replace(/[#*`_~\[\]()>]/g, '').trim()
    await navigator.clipboard.writeText(plain)
  } catch (e) {
    // Fallback for older browsers
    const ta = document.createElement('textarea')
    ta.value = content; document.body.appendChild(ta); ta.select()
    document.execCommand('copy'); document.body.removeChild(ta)
  }
}

// Send message (SSE streaming)
const sendMessage = async () => {
  if (isStreaming.value) return
  const msg = inputMessage.value.trim()
  const file = attachedFile.value
  if (!msg && !file) return

  inputMessage.value = ''
  attachedFile.value = null

  let finalMsg = msg
  if (file) {
    addMessage(`📎 ${file.name}`, true)
    try {
      const res = await uploadDocument(file, '简历')
      const docInfo = res.data?.data
      finalMsg = `[已上传文件: ${file.name}${docInfo ? ', 文档ID: ' + docInfo.docId : ''}]\n${msg}`
    } catch (e) {
      addMessage(`⚠️ 文件上传失败: ${e.message || '请重试'}`, false)
      if (!msg) return
    }
  }

  addMessage(msg || '请帮我分析上传的文件', true)
  isThinking.value = true
  isStreaming.value = true
  thinkingLabel.value = '正在分析…'
  currentAgent.value = { name: '分析中...', type: 'general' }

  if (eventSource) eventSource.close()
  eventSource = chatWithOrchestrator(finalMsg || msg, currentChatId.value)

  traceMap.clear()
  traceVisible.value = true
  qualityReview.value = null
  qualityBlocked.value = null
  showQualityDetail.value = false

  let aiMsgIndex = -1
  let currentAgentInfo = { agentType: 'GENERAL', agentName: '职场通用顾问' }

  eventSource.addEventListener('routing', (e) => {
    isThinking.value = false
    thinkingLabel.value = '连接专家中…'
    addMessage(e.data, false, 'routing')
    // Update agent display name from routing event
    const routeMatch = e.data.match(/路由到(.+?)[\]】]/)
    if (routeMatch) currentAgent.value = { name: routeMatch[1], type: 'general' }
  })

  eventSource.addEventListener('agent-turn', (e) => {
    try {
      const data = JSON.parse(e.data)
      currentAgentInfo = { agentType: data.agentType, agentName: data.agentName }
      currentAgent.value = { name: data.agentName, type: data.agentType }
      aiMsgIndex = -1
    } catch (err) {}
  })

  eventSource.addEventListener('trace', (e) => {
    try {
      const data = JSON.parse(e.data)
      switch (data.type) {
        case 'TRACE_STARTED': traceStatus.value = 'RUNNING'; break
        case 'SPAN_STARTED':
          traceMap.set(data.sequence, {
            sequence: data.sequence, stepType: data.stepType,
            stepTypeDisplayName: data.stepTypeDisplayName,
            label: data.label, status: 'RUNNING', errorMessage: null,
            startTime: new Date().toISOString()
          }); break
        case 'SPAN_ENDED': {
          const span = traceMap.get(data.sequence)
          if (span) { span.status = data.status; span.errorMessage = data.errorMessage; span.endTime = new Date().toISOString() }
          break }
        case 'TRACE_COMPLETED': traceStatus.value = 'SUCCESS'; break
        case 'TRACE_FAILED': traceStatus.value = 'FAILED'; break
      }
    } catch (err) {}
  })

  eventSource.addEventListener('quality-review', (e) => {
    try { qualityReview.value = JSON.parse(e.data) } catch (err) {}
  })
  eventSource.addEventListener('quality-blocked', (e) => { qualityBlocked.value = e.data })
  eventSource.addEventListener('clarification', (e) => {
    isThinking.value = false; addMessage(e.data, false, 'clarification')
    isStreaming.value = false; eventSource.close()
  })

  eventSource.onmessage = (e) => {
    isThinking.value = false
    if (e.data === '[DONE]') {
      isStreaming.value = false; traceVisible.value = false; eventSource.close()
      // Auto-name session after first AI reply
      autoNameSession(msg)
      return
    }
    if (aiMsgIndex === -1) {
      messages.value.push({
        content: '', isUser: false, type: '', time: Date.now(),
        agentType: currentAgentInfo.agentType, agentName: currentAgentInfo.agentName
      })
      aiMsgIndex = messages.value.length - 1
    }
    messages.value[aiMsgIndex].content += e.data
    scrollToBottom()
  }

  eventSource.onerror = () => {
    isThinking.value = false; isStreaming.value = false; eventSource.close()
    if (aiMsgIndex === -1 || !messages.value[aiMsgIndex]?.content) {
      addMessage('连接出现问题，请重试。', false)
    }
  }
}

// Auto-name session after first AI reply (if still "新对话")
const autoNameSession = async (userMsg) => {
  const session = sessions.value.find(s => s.chatId === currentChatId.value)
  if (!session || session.title !== '新对话') return
  // Use first 15 chars of user message as title
  const title = userMsg.length > 15 ? userMsg.slice(0, 15) + '…' : userMsg
  try {
    await renameSession(session.chatId, title)
    session.title = title
  } catch (e) { /* non-critical, ignore */ }
}

// Profile
const openProfile = async () => {
  profileVisible.value = true; profileError.value = ''; profileLoading.value = true
  try {
    await ensureLogin()
    const res = await getMyProfile()
    profile.value = res.data?.data || null
  } catch (e) {
    profileError.value = '加载画像失败，请稍后重试。'; profile.value = null
  } finally { profileLoading.value = false }
}
const closeProfile = () => { profileVisible.value = false }
const handleClearProfile = async () => {
  if (clearing.value) return
  if (!window.confirm('确定要清空你的画像吗？此操作不可恢复。')) return
  clearing.value = true
  try { await clearMyProfile(); profile.value = null }
  catch (e) { console.error('清空画像失败', e) }
  finally { clearing.value = false }
}
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
}

/* ===== Sidebar ===== */
.sidebar {
  width: 272px;
  flex-shrink: 0;
  background: var(--deep);
  border-right: 1px solid var(--glass-border);
  display: flex;
  flex-direction: column;
  transition: width 0.3s var(--ease);
}
.sidebar.collapsed { width: 48px; }

.side-head {
  padding: 18px 18px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.side-head h2 {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 1px;
  color: var(--t4);
  text-transform: uppercase;
  flex: 1;
}
.collapse-toggle {
  width: 24px; height: 24px; border-radius: 4px;
  display: flex; align-items: center; justify-content: center;
  color: var(--t4); font-size: 14px; cursor: pointer;
  background: transparent; border: none;
  transition: background 0.2s;
}
.collapse-toggle:hover { background: var(--glass-hover); }

.side-search {
  margin: 0 14px 12px;
  position: relative;
}
.side-search input {
  width: 100%;
  padding: 9px 12px 9px 34px;
  background: var(--layer1);
  border: 1px solid var(--glass-border);
  border-radius: var(--r-sm);
  color: var(--t1);
  font-size: 12px;
  outline: none;
  transition: border-color 0.2s;
}
.side-search input:focus { border-color: rgba(255,255,255,0.1); }
.side-search input::placeholder { color: var(--t4); }
.side-search .s-icon { position: absolute; left: 11px; top: 50%; transform: translateY(-50%); color: var(--t4); font-size: 13px; }
.search-clear {
  position: absolute; right: 8px; top: 50%; transform: translateY(-50%);
  background: none; border: none; color: var(--t4); cursor: pointer; font-size: 14px;
}
.search-status { padding: 12px 18px; font-size: 12px; color: var(--t4); }

.conv-list { flex: 1; overflow-y: auto; padding: 0 10px 10px; }
.conv {
  padding: 11px 13px;
  border-radius: var(--r-sm);
  cursor: pointer;
  transition: all 0.2s var(--ease);
  margin-bottom: 2px;
}
.conv:hover { background: var(--glass); }
.conv.on { background: var(--glass); border: 1px solid var(--glass-border); }
.c-title { font-size: 13px; font-weight: 500; color: var(--t1); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-bottom: 3px; }
.c-meta { display: flex; align-items: center; justify-content: space-between; font-size: 11px; color: var(--t4); }
.c-actions { display: flex; gap: 2px; opacity: 0; transition: opacity 0.2s; }
.conv:hover .c-actions { opacity: 1; }
.c-act { background: none; border: none; color: var(--t4); cursor: pointer; font-size: 11px; padding: 2px 4px; border-radius: 4px; }
.c-act:hover { background: var(--glass-hover); color: var(--t2); }
.rename-input {
  width: 100%; padding: 4px 8px; background: var(--layer1);
  border: 1px solid var(--glass-border); border-radius: 4px;
  color: var(--t1); font-size: 12px; outline: none;
}
.empty-conv { padding: 20px; text-align: center; color: var(--t4); font-size: 12px; }

.archived-section { padding: 8px 14px; border-top: 1px solid var(--glass-border); }
.archived-toggle {
  background: none; border: none; color: var(--t4); font-size: 11px;
  cursor: pointer; padding: 4px 0;
}
.archived-toggle:hover { color: var(--t3); }
.conv.archived { opacity: 0.6; }
.archived-list { max-height: 200px; overflow-y: auto; }

/* ===== Chat Core ===== */
.chat-core {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-top {
  padding: 14px 22px;
  border-bottom: 1px solid var(--glass-border);
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.back-mobile { display: none; width: 32px; height: 32px; border-radius: var(--r-sm); border: none; background: transparent; color: var(--t3); cursor: pointer; font-size: 16px; }
.chat-top-info { flex: 1; }
.chat-top-info h3 { font-size: 15px; font-weight: 600; color: var(--t1); }
.chat-top-info .agent-tag { font-size: 11px; color: var(--t4); margin-top: 1px; }
.chat-top-actions { display: flex; gap: 4px; }

/* More menu */
.more-menu-wrap { position: relative; }
.more-menu {
  position: absolute; top: 38px; right: 0; z-index: 100;
  background: var(--layer1); border: 1px solid var(--glass-border);
  border-radius: var(--r-sm); padding: 6px 0; min-width: 160px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.4);
}
.menu-item {
  padding: 10px 16px; font-size: 13px; color: var(--t2); cursor: pointer;
  transition: background 0.15s;
}
.menu-item:hover { background: var(--glass-hover); color: var(--t1); }

/* Messages */
.msgs {
  flex: 1;
  overflow-y: auto;
  padding: 24px 22px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* Presence (welcome) */
.presence { display: flex; align-items: flex-start; gap: 14px; margin-bottom: 26px; }
.ai-orb {
  flex-shrink: 0; width: 38px; height: 38px; border-radius: 50%;
  background: radial-gradient(circle at 38% 32%, rgba(245,158,11,0.75), rgba(217,119,6,0.2));
  box-shadow: 0 0 20px var(--gold-glow), inset 0 1px 0 rgba(255,255,255,0.25);
  animation: orb-pulse 6s ease-in-out infinite;
}
.presence-body {
  flex: 1; background: var(--layer1); border: 1px solid var(--glass-border);
  border-radius: var(--r-lg); border-top-left-radius: 4px; padding: 4px;
}
.presence-text {
  padding: 20px 24px; font-size: 14px; color: var(--t2); line-height: 1.8;
}
.presence-text b { color: var(--t1); font-weight: 600; }
.presence-text .hl { color: var(--gold-text); font-weight: 500; }
.presence-privacy { margin-top: 16px; font-size: 11px; color: var(--t4); opacity: 0.7; }

/* Message rows */
.msg-wrapper { margin-bottom: 6px; }
.msg-row { display: flex; gap: 14px; }
.msg-row.you { flex-direction: row-reverse; }
.msg-av { flex-shrink: 0; width: 30px; height: 30px; border-radius: 50%; background: var(--layer2); display: flex; align-items: center; justify-content: center; font-size: 13px; }
.msg-bub {
  max-width: 70%; padding: 16px 20px; border-radius: var(--r-lg);
  font-size: 14px; line-height: 1.75;
}
.msg-row.ai .msg-bub { background: var(--layer1); border: 1px solid var(--glass-border); border-top-left-radius: 4px; color: var(--t1); }
.msg-row.you .msg-bub { background: linear-gradient(135deg, rgba(245,158,11,0.12), rgba(245,158,11,0.03)); border: 1px solid rgba(245,158,11,0.08); color: var(--t1); }
.agent-label { font-size: 11px; color: var(--gold-text); margin-bottom: 4px; font-weight: 500; }
.msg-actions { margin-top: 4px; }
.msg-act { background: none; border: none; cursor: pointer; font-size: 14px; padding: 2px 6px; border-radius: 4px; color: var(--t4); }
.msg-act:hover { background: var(--glass-hover); }

/* Routing badge */
.routing-badge {
  text-align: center; padding: 8px 16px; margin: 8px auto;
  background: var(--gold-soft); border: 1px solid rgba(245,158,11,0.15);
  border-radius: var(--r-full); font-size: 11px; color: var(--gold-text);
  display: flex; align-items: center; gap: 8px; width: fit-content;
}
.routing-text { font-weight: 500; }
.routing-hint { color: var(--t4); font-size: 10px; opacity: 0.8; }

/* Agent color bands */
.msg-row.ai.agent-resume .msg-bub { border-left: 3px solid #60a5fa; }
.msg-row.ai.agent-negotiation .msg-bub { border-left: 3px solid #f59e0b; }
.msg-row.ai.agent-escape .msg-bub { border-left: 3px solid #a78bfa; }
.msg-row.ai.agent-consultation .msg-bub { border-left: 3px solid #34d399; }
.msg-row.ai.agent-general .msg-bub { border-left: 3px solid #6b7280; }

/* Stop generation button */
.chat-stop {
  width: 42px; height: 42px; border-radius: var(--r-md); border: none;
  background: linear-gradient(135deg, #ef4444, #dc2626);
  color: #fff; cursor: pointer; display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 700; transition: all 0.25s var(--spring);
  box-shadow: 0 4px 14px rgba(239,68,68,0.3);
  animation: pulse-stop 1.5s ease-in-out infinite;
}
.chat-stop:hover { transform: scale(1.06); box-shadow: 0 6px 20px rgba(239,68,68,0.4); }
.chat-stop:active { transform: scale(0.95); }
@keyframes pulse-stop { 0%, 100% { opacity: 1; } 50% { opacity: 0.8; } }

/* Typing */
.typing { display: flex; align-items: center; gap: 14px; padding: 10px 0; }
.typing-dots { display: flex; gap: 5px; }
.typing-dots b { display: block; width: 5px; height: 5px; border-radius: 50%; background: var(--t4); animation: jump 1.4s ease-in-out infinite; }
.typing-dots b:nth-child(2) { animation-delay: 0.2s; }
.typing-dots b:nth-child(3) { animation-delay: 0.4s; }

/* Trace strip */
.trace-strip {
  padding: 8px 22px;
  border-top: 1px solid var(--glass-border);
  background: var(--layer1);
  max-height: 120px;
  overflow-y: auto;
}

/* Quality */
.quality-blocked {
  padding: 8px 22px;
  background: var(--danger-bg);
  border-top: 1px solid rgba(248,113,113,0.15);
  font-size: 12px;
  color: var(--danger);
}
.quality-strip {
  padding: 8px 22px;
  border-top: 1px solid var(--glass-border);
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  font-size: 11px; color: var(--t4); position: relative;
}
.quality-strip.risk-low { border-top-color: rgba(52,211,153,0.3); }
.quality-strip.risk-medium { border-top-color: rgba(245,158,11,0.3); background: rgba(245,158,11,0.03); }
.quality-strip.risk-high { border-top-color: rgba(248,113,113,0.3); background: rgba(248,113,113,0.03); }
.quality-label { font-weight: 600; }
.quality-detail-btn {
  margin-left: auto; background: none; border: 1px solid var(--glass-border);
  border-radius: var(--r-full); padding: 2px 10px; font-size: 10px;
  color: var(--t3); cursor: pointer; transition: all 0.2s;
}
.quality-detail-btn:hover { background: var(--glass-hover); color: var(--t1); }
.quality-detail-panel {
  width: 100%; margin-top: 8px; padding: 12px 16px;
  background: var(--layer1); border: 1px solid var(--glass-border);
  border-radius: var(--r-sm); display: flex; flex-direction: column; gap: 6px;
}
.qd-row { display: flex; align-items: center; gap: 8px; font-size: 11px; color: var(--t2); }
.qd-label { font-weight: 600; color: var(--t3); min-width: 56px; }
.qd-bar { flex: 1; height: 6px; background: var(--layer2); border-radius: 3px; overflow: hidden; max-width: 120px; }
.qd-fill { display: block; height: 100%; background: var(--ok); border-radius: 3px; transition: width 0.4s; }
.qd-fill.warn { background: var(--warn); }
.qd-issues, .qd-suggestions { margin-top: 4px; }
.qd-issues ul, .qd-suggestions ul { margin: 4px 0 0 16px; padding: 0; font-size: 11px; color: var(--t2); }
.qd-issues li { color: var(--danger); }
.qd-suggestions li { color: var(--gold-text); }

/* Session group labels */
.conv-group-label { font-size: 10px; font-weight: 600; color: var(--t4); padding: 10px 13px 4px; text-transform: uppercase; letter-spacing: 0.8px; }

/* Undo toast */
.undo-toast {
  position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%);
  background: var(--layer1); border: 1px solid var(--glass-border);
  border-radius: var(--r-md); padding: 10px 18px;
  display: flex; align-items: center; gap: 12px;
  font-size: 13px; color: var(--t2); z-index: 200;
  box-shadow: 0 8px 32px rgba(0,0,0,0.4);
}
.undo-toast button {
  background: none; border: none; color: var(--gold-text); cursor: pointer;
  font-size: 13px; font-weight: 600; padding: 4px 8px; border-radius: 4px;
}
.undo-toast button:hover { background: var(--gold-soft); }
.toast-enter-active, .toast-leave-active { transition: all 0.3s var(--ease); }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(-50%) translateY(20px); }

/* Skeleton loading */
.skeleton-msg { display: flex; gap: 14px; padding: 8px 0; }
.skeleton-msg.skel-right { flex-direction: row-reverse; }
.skel-av { width: 30px; height: 30px; border-radius: 50%; background: var(--layer2); animation: skel-pulse 1.5s infinite; }
.skel-bub { padding: 16px 20px; border-radius: var(--r-lg); background: var(--layer1); border: 1px solid var(--glass-border); min-width: 180px; }
.skel-line { height: 12px; border-radius: 6px; background: var(--layer2); margin-bottom: 8px; animation: skel-pulse 1.5s infinite; }
.skel-line.short { width: 60% !important; margin-bottom: 0; }
@keyframes skel-pulse { 0%, 100% { opacity: 0.5; } 50% { opacity: 0.8; } }

/* Thinking label */
.typing-label { font-size: 11px; color: var(--t4); margin-left: 4px; animation: fade-in 0.3s; }
@keyframes fade-in { from { opacity: 0; } to { opacity: 1; } }

/* Chat input bar */
.chat-bar { padding: 16px 22px 24px; border-top: 1px solid var(--glass-border); flex-shrink: 0; }
.attached-file {
  display: flex; align-items: center; gap: 8px; padding: 6px 12px; margin-bottom: 8px;
  background: var(--layer2); border-radius: var(--r-sm); font-size: 12px; color: var(--t2);
}
.attached-file button { background: none; border: none; color: var(--t4); cursor: pointer; }
.chat-bar-wrap {
  display: flex; align-items: flex-end; gap: 8px;
  background: var(--layer1); border: 1px solid var(--glass-border);
  border-radius: var(--r-lg); padding: 8px 8px 8px 18px;
  transition: border-color 0.3s;
}
.chat-bar-wrap.focused { border-color: rgba(245,158,11,0.28); box-shadow: 0 0 0 3px var(--gold-dim); }
.chat-bar-wrap textarea {
  flex: 1; border: none; background: transparent; color: var(--t1);
  font-family: var(--font); font-size: 14px; line-height: 1.6;
  padding: 8px 0; resize: none; outline: none; min-height: 24px; max-height: 120px;
}
.chat-bar-wrap textarea::placeholder { color: var(--t4); }
.chat-bar-wrap textarea:disabled { opacity: 0.5; }
.bar-btn {
  width: 36px; height: 36px; border-radius: var(--r-sm);
  background: transparent; border: none; color: var(--t3);
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  font-size: 16px; transition: all 0.2s;
}
.bar-btn:hover { background: var(--glass-hover); color: var(--t2); }
.bar-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.bar-btn.listening { color: var(--danger); animation: live 1.5s ease-in-out infinite; }
.chat-send {
  width: 40px; height: 40px; border-radius: var(--r-md);
  border: none; background: var(--gold); color: #fff;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  font-size: 16px; font-weight: 700; flex-shrink: 0;
  transition: all 0.25s var(--spring);
  box-shadow: 0 4px 14px var(--gold-glow);
}
.chat-send:hover { transform: scale(1.05); box-shadow: 0 6px 22px rgba(245,158,11,0.35); }
.chat-send:active { transform: scale(0.95); }
.chat-send:disabled { opacity: 0.3; cursor: not-allowed; transform: none; box-shadow: none; background: var(--layer3); color: var(--t4); }

/* Panel */
.panel {
  width: 0; overflow: hidden;
  background: var(--deep); border-left: 1px solid var(--glass-border);
  transition: width 0.35s var(--ease);
}
.panel.show { width: 360px; }
.panel-in { padding: 22px; width: 360px; }
.panel-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.panel-head h3 { font-size: 14px; font-weight: 600; color: var(--t1); }
.panel-content { font-size: 13px; color: var(--t2); line-height: 1.7; overflow-y: auto; max-height: calc(100vh - 200px); }

/* Overlay (Profile) */
.overlay {
  position: fixed; inset: 0; z-index: 100;
  background: rgba(5,6,13,0.7); backdrop-filter: blur(8px);
  display: flex; align-items: center; justify-content: center;
}
.overlay-panel {
  background: var(--layer1); border: 1px solid var(--glass-border);
  border-radius: var(--r-lg); width: 420px; max-width: 90vw;
  max-height: 80vh; display: flex; flex-direction: column;
}
.overlay-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 20px 24px; border-bottom: 1px solid var(--glass-border);
}
.overlay-header h2 { font-size: 16px; font-weight: 600; color: var(--t1); }
.overlay-close { background: none; border: none; color: var(--t3); font-size: 20px; cursor: pointer; }
.overlay-body { padding: 24px; flex: 1; overflow-y: auto; }
.overlay-loading, .overlay-empty { text-align: center; color: var(--t3); font-size: 13px; padding: 20px; }
.profile-content { display: flex; flex-direction: column; gap: 16px; }
.profile-field { display: flex; flex-direction: column; gap: 4px; }
.field-label { font-size: 11px; color: var(--t4); font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; }
.field-value { font-size: 14px; color: var(--t1); }
.field-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.field-tag { padding: 4px 12px; border-radius: var(--r-full); background: var(--gold-soft); color: var(--gold-text); font-size: 12px; }
.field-empty { font-size: 12px; color: var(--t4); }
.overlay-footer { padding: 16px 24px; border-top: 1px solid var(--glass-border); }
.clear-btn {
  padding: 8px 16px; border-radius: var(--r-sm);
  background: var(--danger-bg); border: 1px solid rgba(248,113,113,0.2);
  color: var(--danger); font-size: 12px; cursor: pointer; transition: all 0.2s;
}
.clear-btn:hover { background: rgba(248,113,113,0.15); }
.clear-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* Markdown content in messages */
.msg-bub :deep(p) { margin: 0 0 8px; }
.msg-bub :deep(p:last-child) { margin: 0; }
.msg-bub :deep(strong), .msg-bub :deep(b) { color: var(--t1); font-weight: 600; }
.msg-bub :deep(em) { color: var(--t2); font-style: italic; }
.msg-bub :deep(code) { font-family: var(--mono); font-size: 12px; padding: 2px 6px; background: var(--layer2); border-radius: 4px; }
.msg-bub :deep(pre) { background: var(--layer2); border-radius: var(--r-sm); padding: 12px; overflow-x: auto; margin: 8px 0; }
.msg-bub :deep(ul), .msg-bub :deep(ol) { padding-left: 18px; margin: 6px 0; }
.msg-bub :deep(li) { margin-bottom: 4px; }

.tb-btn {
  width: 34px; height: 34px; border-radius: var(--r-sm);
  border: none; background: transparent; color: var(--t3);
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  font-size: 15px; transition: all 0.2s var(--ease);
}
.tb-btn:hover { background: var(--glass-hover); color: var(--t2); }

@media (max-width: 768px) {
  .sidebar { display: none; }
  .back-mobile { display: flex; }
  .panel.show { width: 100%; position: absolute; right: 0; top: 0; bottom: 0; z-index: 10; }
}
</style>

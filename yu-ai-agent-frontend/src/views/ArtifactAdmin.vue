<template>
  <div class="artifact-layout">
    <!-- 头部 -->
    <div class="header">
      <div class="back-button" @click="goBack">← 返回</div>
      <div class="header-center">
        <h1 class="title"><WpIcon name="artifact" :size="20" class="title-icon" /> 交付物管理</h1>
        <div class="admin-badge" :class="{ active: isAdmin }">
          {{ isAdmin ? '管理员已登录' : '未登录管理员' }}
        </div>
      </div>
      <div class="header-right">
        <button class="admin-login-btn" :disabled="loggingIn" @click="loginAsAdmin">
          {{ loggingIn ? '登录中...' : '🔑 以管理员登录' }}
        </button>
      </div>
    </div>

    <div class="content">
      <!-- 左侧：过滤 + 列表 -->
      <section class="list-panel">
        <div class="filter-bar">
          <input v-model="filters.type" class="filter-input" placeholder="按类型 type 过滤" @keyup.enter="loadList" />
          <input v-model="filters.userId" class="filter-input" placeholder="按 userId 过滤" @keyup.enter="loadList" />
          <input v-model="filters.chatId" class="filter-input" placeholder="按 chatId 过滤" @keyup.enter="loadList" />
          <button class="query-btn" :disabled="loading" @click="loadList">
            {{ loading ? '查询中...' : '查询' }}
          </button>
          <button class="reset-btn" :disabled="loading" @click="resetFilters">重置</button>
        </div>

        <!-- 提示信息 -->
        <div v-if="hint" class="hint" :class="hint.type">{{ hint.text }}</div>

        <div class="list-body">
          <div v-if="loading" class="placeholder">加载中...</div>
          <div v-else-if="artifacts.length === 0" class="placeholder">
            暂无交付物。请确认已以管理员身份登录，并调整过滤条件后重试。
          </div>
          <div
            v-else
            v-for="item in artifacts"
            :key="item.artifactId"
            class="list-item"
            :class="{ active: item.artifactId === selectedId }"
            @click="openDetail(item.artifactId)"
          >
            <div class="item-top">
              <span class="item-title">{{ item.title || '(无标题)' }}</span>
              <span class="status-tag" :class="statusClass(item.status)">{{ statusText(item.status) }}</span>
            </div>
            <div class="item-meta">
              <span class="meta-type">{{ item.type || '未知类型' }}</span>
              <span class="meta-producer">{{ item.producer || '未知生产者' }}</span>
            </div>
            <div class="item-bottom">
              <span class="meta-id">{{ item.artifactId }}</span>
              <span class="meta-time">{{ formatDateTime(item.createdAt) }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 右侧：详情 -->
      <section class="detail-panel">
        <div v-if="detailLoading" class="placeholder">详情加载中...</div>

        <div v-else-if="detailError" class="placeholder error">{{ detailError }}</div>

        <div v-else-if="!detail" class="placeholder">点击左侧交付物查看完整内容。</div>

        <div v-else class="detail-content">
          <h2 class="detail-title">{{ detail.title || '(无标题)' }}</h2>

          <div class="detail-fields">
            <div class="detail-field">
              <span class="field-label">交付物 ID</span>
              <span class="field-value mono">{{ detail.artifactId }}</span>
            </div>
            <div class="detail-field">
              <span class="field-label">类型</span>
              <span class="field-value">{{ detail.type || '—' }}</span>
            </div>
            <div class="detail-field">
              <span class="field-label">生产者</span>
              <span class="field-value">{{ detail.producer || '—' }}</span>
            </div>
            <div class="detail-field">
              <span class="field-label">状态</span>
              <span class="field-value">
                <span class="status-tag" :class="statusClass(detail.status)">{{ statusText(detail.status) }}</span>
              </span>
            </div>
            <div class="detail-field">
              <span class="field-label">作用域</span>
              <span class="field-value">{{ detail.scope || '—' }}</span>
            </div>
            <div class="detail-field">
              <span class="field-label">userId</span>
              <span class="field-value mono">{{ detail.userId || '—' }}</span>
            </div>
            <div class="detail-field">
              <span class="field-label">chatId</span>
              <span class="field-value mono">{{ detail.chatId || '—' }}</span>
            </div>
            <div class="detail-field">
              <span class="field-label">创建时间</span>
              <span class="field-value">{{ formatDateTime(detail.createdAt) }}</span>
            </div>
            <div class="detail-field">
              <span class="field-label">更新时间</span>
              <span class="field-value">{{ formatDateTime(detail.updatedAt) }}</span>
            </div>
          </div>

          <div class="content-block">
            <div class="content-label">完整内容（content）</div>
            <pre class="content-pre">{{ formattedContent }}</pre>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import { login, listArtifacts, getArtifactDetail } from '../api'
import WpIcon from '../components/WpIcon.vue'

useHead({ title: '交付物管理 - 职场生存智囊' })

const router = useRouter()

const filters = reactive({ type: '', userId: '', chatId: '' })
const artifacts = ref([])
const loading = ref(false)
const hint = ref(null)

const selectedId = ref('')
const detail = ref(null)
const detailLoading = ref(false)
const detailError = ref('')

const loggingIn = ref(false)
const isAdmin = ref(localStorage.getItem('username') === 'admin')

const goBack = () => router.push('/')

// 以管理员（username=admin）登录，获取具备管理员权限的 JWT
const loginAsAdmin = async () => {
  if (loggingIn.value) return
  loggingIn.value = true
  hint.value = null
  try {
    const res = await login('admin')
    const data = res.data?.data || {}
    localStorage.setItem('token', data.token)
    localStorage.setItem('userId', data.userId)
    localStorage.setItem('username', data.username || 'admin')
    isAdmin.value = data.username === 'admin'
    hint.value = { type: 'success', text: '✓ 已以管理员身份登录，可查询交付物' }
    await loadList()
  } catch (e) {
    console.error('管理员登录失败', e)
    hint.value = { type: 'error', text: '管理员登录失败，请稍后重试。' }
  } finally {
    loggingIn.value = false
  }
}

// 查询交付物列表（仅传入非空过滤条件）
const loadList = async () => {
  if (loading.value) return
  loading.value = true
  hint.value = null
  try {
    const params = {}
    if (filters.type.trim()) params.type = filters.type.trim()
    if (filters.userId.trim()) params.userId = filters.userId.trim()
    if (filters.chatId.trim()) params.chatId = filters.chatId.trim()
    const res = await listArtifacts(params)
    const body = res.data || {}
    if (body.code && body.code !== 0 && body.code !== 200) {
      // 后端返回错误码（如 403 需要管理员权限）
      artifacts.value = []
      hint.value = { type: 'error', text: body.message || '查询失败，请确认管理员权限。' }
      return
    }
    artifacts.value = body.data || []
    if (artifacts.value.length === 0) {
      hint.value = { type: 'info', text: '查询成功，但没有匹配的交付物。' }
    }
  } catch (e) {
    console.error('查询交付物失败', e)
    artifacts.value = []
    const status = e.response?.status
    if (status === 403) {
      hint.value = { type: 'error', text: '无管理员权限（403），请先以管理员登录。' }
    } else {
      hint.value = { type: 'error', text: '查询失败，请稍后重试。' }
    }
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  filters.type = ''
  filters.userId = ''
  filters.chatId = ''
  loadList()
}

// 查看详情
const openDetail = async (artifactId) => {
  selectedId.value = artifactId
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  try {
    const res = await getArtifactDetail(artifactId)
    const body = res.data || {}
    if (body.code && body.code !== 0 && body.code !== 200) {
      detailError.value = body.message || '加载详情失败。'
      return
    }
    detail.value = body.data || null
    if (!detail.value) {
      detailError.value = '未找到该交付物详情。'
    }
  } catch (e) {
    console.error('加载交付物详情失败', e)
    const status = e.response?.status
    detailError.value = status === 403
      ? '无管理员权限（403），请先以管理员登录。'
      : '加载详情失败，请稍后重试。'
  } finally {
    detailLoading.value = false
  }
}

// content 是 JSON 字符串，做基本格式化展示；非 JSON 时原样展示
const formattedContent = computed(() => {
  const raw = detail.value?.content
  if (raw === null || raw === undefined || raw === '') return '(空内容)'
  if (typeof raw !== 'string') {
    try {
      return JSON.stringify(raw, null, 2)
    } catch (e) {
      return String(raw)
    }
  }
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch (e) {
    return raw
  }
})

const statusText = (status) => {
  if (status === 'READY') return '可消费'
  if (status === 'PENDING') return '生产中'
  if (status === 'CONSUMED') return '已消费'
  return status || '未知'
}

const statusClass = (status) => {
  if (status === 'READY') return 'ready'
  if (status === 'PENDING') return 'pending'
  if (status === 'CONSUMED') return 'consumed'
  return 'unknown'
}

// 后端返回 LocalDateTime（ISO 字符串或时间数组），统一格式化
const formatDateTime = (value) => {
  if (!value) return '—'
  if (Array.isArray(value)) {
    // [year, month, day, hour, minute, second, nano]
    const [y, mo = 1, d = 1, h = 0, mi = 0] = value
    const dt = new Date(y, mo - 1, d, h, mi)
    return dt.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
  }
  const dt = new Date(value)
  if (isNaN(dt.getTime())) return value
  return dt.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.artifact-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg-page, #0a0a0f);
  overflow: hidden;
}

/* 头部 */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  background: var(--glass-bg); backdrop-filter: blur(var(--glass-blur)); -webkit-backdrop-filter: blur(var(--glass-blur));
  color: var(--text);
border: 0.5px solid var(--border);
}

.back-button { cursor: pointer; font-size: 15px; opacity: 0.85; transition: opacity 0.2s; }
.back-button:hover { opacity: 1; }

.header-center { display: flex; align-items: center; gap: 12px; }

.title { font-size: 18px; font-weight: bold; margin: 0; display: inline-flex; align-items: center; gap: 8px; }
.title-icon { color: var(--gold-text); }

.admin-badge {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 12px;
  font-weight: 500;
  background: rgba(239, 68, 68, 0.2);
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.4);
}
.admin-badge.active {
  background: rgba(16, 185, 129, 0.2);
  color: #34d399;
  border: 1px solid rgba(16, 185, 129, 0.4);
}

.header-right { display: flex; align-items: center; gap: 14px; }

.admin-login-btn {
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.25);
  color: var(--text);
  border-radius: 16px;
  padding: 5px 14px;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.2s;
}
.admin-login-btn:hover:not(:disabled) { background: rgba(255, 255, 255, 0.22); }
.admin-login-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* 内容主区 */
.content {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 16px;
  overflow: hidden;
}

/* 列表面板 */
.list-panel {
  width: 420px;
  min-width: 360px;
  display: flex;
  flex-direction: column;
  background: var(--bg-card, #1a1a2e);
  border-radius: 12px;
border: 0.5px solid var(--border);
  overflow: hidden;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 14px;
  border-bottom: 1px solid var(--glass-border, rgba(255,255,255,0.06));
}

.filter-input {
  flex: 1 1 100%;
  border: 1px solid var(--glass-border, rgba(255,255,255,0.1));
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 14px;
  outline: none;
  background: var(--bg-page, #0a0a0f);
  color: var(--text, #e5e7eb);
  transition: border-color 0.2s;
}
.filter-input:focus { border-color: #1e40af; }

.query-btn,
.reset-btn {
  border: none;
  border-radius: 8px;
  padding: 8px 18px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}
.query-btn { background: var(--glass-bg); backdrop-filter: blur(var(--glass-blur)); -webkit-backdrop-filter: blur(var(--glass-blur)); color: var(--text); flex: 1; }
.query-btn:hover:not(:disabled) { background: #1d4ed8; }
.query-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.reset-btn { background: var(--glass-hover, rgba(255,255,255,0.06)); color: var(--text-secondary, #a0aec0); flex: 1; }
.reset-btn:hover:not(:disabled) { background: var(--glass-border, rgba(255,255,255,0.1)); }

.hint {
  margin: 12px 14px 0;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
  text-align: center;
}
.hint.success { background: rgba(16, 185, 129, 0.1); color: #059669; border: 1px solid rgba(16, 185, 129, 0.3); }
.hint.error { background: rgba(239, 68, 68, 0.1); color: #dc2626; border: 1px solid rgba(239, 68, 68, 0.3); }
.hint.info { background: rgba(99, 102, 241, 0.08); color: #6366f1; border: 1px solid rgba(99, 102, 241, 0.2); }

.list-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.placeholder {
  text-align: center;
  color: #6b7280;
  font-size: 14px;
  padding: 40px 16px;
  line-height: 1.6;
}
.placeholder.error { color: #dc2626; }

.list-item {
  border: 1px solid var(--glass-border, rgba(255,255,255,0.06));
  border-radius: 10px;
  padding: 12px 14px;
  cursor: pointer;
  transition: all 0.2s;
}
.list-item:hover { border-color: #93c5fd; background: var(--glass-hover, rgba(255,255,255,0.04)); }
.list-item.active { border-color: #1e40af; background: rgba(30, 64, 175, 0.05); }

.item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.item-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text, #e5e7eb);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-tag {
  flex-shrink: 0;
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  font-weight: 500;
}
.status-tag.ready { background: rgba(16, 185, 129, 0.12); color: #059669; }
.status-tag.pending { background: rgba(245, 158, 11, 0.12); color: #d97706; }
.status-tag.consumed { background: rgba(107, 114, 128, 0.12); color: #6b7280; }
.status-tag.unknown { background: rgba(107, 114, 128, 0.12); color: #6b7280; }

.item-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
}
.meta-type {
  font-size: 12px;
  color: #1e40af;
  background: rgba(30, 64, 175, 0.08);
  border-radius: 8px;
  padding: 1px 8px;
}
.meta-producer {
  font-size: 12px;
  color: #6b7280;
}

.item-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.meta-id {
  font-size: 11px;
  color: #9ca3af;
  font-family: monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta-time { font-size: 11px; color: #9ca3af; flex-shrink: 0; }

/* 详情面板 */
.detail-panel {
  flex: 1;
  background: var(--bg-card, #1a1a2e);
  border-radius: 12px;
border: 0.5px solid var(--border);
  overflow-y: auto;
  padding: 24px;
}

.detail-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text, #e5e7eb);
  margin: 0 0 18px;
}

.detail-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
  margin-bottom: 20px;
}

.detail-field { display: flex; flex-direction: column; gap: 2px; }

.field-label { font-size: 12px; font-weight: 600; color: #6b7280; }

.field-value { font-size: 14px; color: var(--text-secondary, #a0aec0); word-break: break-all; }
.field-value.mono { font-family: monospace; font-size: 13px; }

.content-block { border-top: 1px solid var(--glass-border, rgba(255,255,255,0.06)); padding-top: 16px; }

.content-label { font-size: 13px; font-weight: 600; color: var(--text-secondary, #a0aec0); margin-bottom: 8px; }

.content-pre {
  background: #1f2937;
  color: #e5e7eb;
  border-radius: 8px;
  padding: 16px;
  font-size: 13px;
  line-height: 1.6;
  font-family: 'Consolas', 'Monaco', monospace;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-x: auto;
  margin: 0;
}

@media (max-width: 900px) {
  .content { flex-direction: column; overflow-y: auto; }
  .list-panel { width: 100%; min-width: 0; }
  .detail-fields { grid-template-columns: 1fr; }
}
</style>

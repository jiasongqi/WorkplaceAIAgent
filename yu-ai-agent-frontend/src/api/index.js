import axios from 'axios'

const API_BASE_URL = import.meta.env.PROD
  ? '/api'
  : 'http://localhost:8123/api'

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000
})

function saveAuth(data) {
  if (!data) return
  const access = data.accessToken || data.token
  if (access) localStorage.setItem('token', access)
  if (data.refreshToken) localStorage.setItem('refreshToken', data.refreshToken)
  if (data.userId) localStorage.setItem('userId', data.userId)
  if (data.username) localStorage.setItem('username', data.username)
  if (data.role) localStorage.setItem('role', data.role)
}

function clearAuth() {
  ;['token', 'refreshToken', 'userId', 'username', 'role'].forEach(k => localStorage.removeItem(k))
}

request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers['Authorization'] = `Bearer ${token}`
  return config
})

let isRefreshing = false
let refreshQueue = []

request.interceptors.response.use(
  response => response,
  async error => {
    const originalRequest = error.config
    const url = originalRequest?.url || ''
    const skip =
      url.includes('/session/login') ||
      url.includes('/session/refresh') ||
      url.includes('/session/register')

    if (error.response?.status === 401 && originalRequest && !originalRequest._retry && !skip) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          refreshQueue.push({ resolve, reject })
        }).then(token => {
          originalRequest.headers['Authorization'] = `Bearer ${token}`
          return request(originalRequest)
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        const refreshToken = localStorage.getItem('refreshToken')
        if (!refreshToken) {
          clearAuth()
          if (!window.location.pathname.startsWith('/login')) {
            window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`
          }
          throw error
        }
        const res = await request.post('/session/refresh', null, { params: { refreshToken } })
        const data = res.data.data
        saveAuth(data)
        const newToken = data.accessToken || data.token
        refreshQueue.forEach(p => p.resolve(newToken))
        refreshQueue = []
        originalRequest.headers['Authorization'] = `Bearer ${newToken}`
        return request(originalRequest)
      } catch (e) {
        refreshQueue.forEach(p => p.reject(e))
        refreshQueue = []
        clearAuth()
        if (!window.location.pathname.startsWith('/login')) {
          window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`
        }
        return Promise.reject(e)
      } finally {
        isRefreshing = false
      }
    }

    if (error.response?.status === 429) {
      const msg = error.response?.data?.message || '今日配额已用尽'
      console.warn('[quota]', msg)
    }
    return Promise.reject(error)
  }
)

export const connectSSE = (url, params) => {
  const queryString = Object.keys(params)
    .map(k => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
    .join('&')
  return new EventSource(`${API_BASE_URL}${url}?${queryString}`)
}

/** @deprecated prefer loginWithPassword / loginAsGuest */
export const login = (username = '游客', userId, password) =>
  request.post('/session/login', null, {
    params: { username, ...(userId ? { userId } : {}), ...(password ? { password } : {}) }
  }).then(res => {
    saveAuth(res.data.data)
    return res
  })

export const register = (username, password) =>
  request.post('/session/register', null, { params: { username, password } }).then(res => {
    saveAuth(res.data.data)
    return res
  })

export const loginWithPassword = (username, password) =>
  request.post('/session/login', null, { params: { username, password } }).then(res => {
    saveAuth(res.data.data)
    return res
  })

export const loginAsGuest = () => {
  const userId = localStorage.getItem('userId')
  return login('游客', userId || undefined)
}

export const refreshAccessToken = (refreshToken = localStorage.getItem('refreshToken')) =>
  request.post('/session/refresh', null, { params: { refreshToken } }).then(res => {
    saveAuth(res.data.data)
    return res
  })

export const logout = () => {
  const refreshToken = localStorage.getItem('refreshToken')
  return request.post('/session/logout', null, { params: { refreshToken } })
    .catch(() => {})
    .finally(() => clearAuth())
}

export const getMe = () => request.get('/session/me')

export const createSession = (title = '新对话') =>
  request.post('/session/create', null, { params: { title } })

export const listSessions = () => request.get('/session/list')

export const deleteSession = (chatId) => request.delete(`/session/${chatId}`)

export const chatWithAiChat = (message, chatId) =>
  connectSSE('/ai/ai_chat/chat/sse', { message, chatId })

export const chatWithOrchestrator = (message, chatId) => {
  const token = localStorage.getItem('token')
  const params = { message, chatId }
  if (token) params.token = token
  const queryString = Object.keys(params)
    .map(k => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
    .join('&')
  return new EventSource(`${API_BASE_URL}/ai/orchestrator/chat?${queryString}`)
}

/** SSE resume after disconnect — replays partial/complete assistant content */
export const resumeOrchestratorChat = (chatId, messageId) => {
  const token = localStorage.getItem('token')
  const params = { chatId, messageId }
  if (token) params.token = token
  const queryString = Object.keys(params)
    .map(k => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
    .join('&')
  return new EventSource(`${API_BASE_URL}/ai/orchestrator/chat/resume?${queryString}`)
}

export const chatWithManus = (message) =>
  connectSSE('/ai/manus/chat', { message })

export const uploadDocument = (file, status = '通用') => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('status', status)
  return request.post('/document/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

export const listDocuments = () => request.get('/document/list')

export const deleteDocument = (docId) => request.delete(`/document/${docId}`)

export const addTextDocument = (content, filename, status = '通用') => {
  const formData = new FormData()
  formData.append('content', content)
  formData.append('filename', filename)
  formData.append('status', status)
  return request.post('/document/add', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

/** 简历/Offer 感知预处理 → promptBlock（调试用，不绑会话） */
export const preprocessPerception = (file, hint = 'resume') => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('hint', hint || 'resume')
  return request.post('/perception/preprocess', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

/**
 * 预处理并写入会话 Shared State（推荐联调路径）。
 * 聊天只需发短句，避免 EventSource GET URL 过长。
 */
export const preprocessPerceptionAndBind = (file, chatId, hint = 'resume') => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('hint', hint || 'resume')
  formData.append('chatId', chatId)
  return request.post('/perception/preprocess-and-bind', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

/** 感知假设 vs 工具观测交叉验证 */
export const perceptionCrossCheck = (hypothesis, observed) =>
  request.post('/perception/cross-check', null, { params: { hypothesis, observed } })


export const getMyProfile = () => request.get('/profile/me')
export const clearMyProfile = () => request.delete('/profile/me')
export const getChatMessages = (chatId) => request.get(`/session/${chatId}/messages`)
export const renameSession = (chatId, title) => request.put(`/session/${chatId}/title`, { title })
export const archiveSession = (chatId) => request.put(`/session/${chatId}/archive`)
export const unarchiveSession = (chatId) => request.put(`/session/${chatId}/unarchive`)
export const listArchivedSessions = () => request.get('/session/archived')
export const restoreSession = (chatId) => request.put(`/session/${chatId}/restore`)
export const listTrashSessions = () => request.get('/session/trash')
export const searchSessions = (keyword) => request.get('/session/search', { params: { keyword } })
export const listArtifacts = (params) => request.get('/artifact/list', { params })
export const getArtifactDetail = (artifactId) => request.get(`/artifact/${artifactId}`)
export const listMyArtifacts = (chatId) =>
  request.get('/artifact/mine', { params: chatId ? { chatId } : {} })
export const getMyArtifactDetail = (artifactId) => request.get(`/artifact/mine/${artifactId}`)
export const listExpertPacks = () => request.get('/pack/list')
export const setExpertPackEnabled = (packId, enabled) =>
  request.post(`/pack/${packId}/enabled`, { enabled })
export const listSkills = () => request.get('/skill/list')
export const draftSkillFromTrace = (traceId) =>
  request.post('/skill/draft-from-trace', { traceId })
export const saveDraftSkill = (draft) => request.post('/skill/save', draft)
export const listMyTasks = () => request.get('/task/mine')
export const getSandboxPolicy = () => request.get('/task/sandbox-policy')
export const getTrace = (traceId) => request.get(`/trace/${traceId}`)
export const getTracesByChat = (chatId, pageNum = 1, pageSize = 20) =>
  request.get(`/trace/chat/${chatId}`, { params: { pageNum, pageSize } })
export const getTracesByUser = (userId, pageNum = 1, pageSize = 20) =>
  request.get(`/trace/user/${userId}`, { params: { pageNum, pageSize } })
export const addFavorite = (chatId, messageId, content, role) =>
  request.post('/favorite', { chatId, messageId, content, role })
export const removeFavorite = (favoriteId) => request.delete(`/favorite/${favoriteId}`)
export const listFavorites = () => request.get('/favorite/list')
export const exportAll = () => {
  const token = localStorage.getItem('token')
  window.open(`${API_BASE_URL}/export/all?token=${encodeURIComponent(token || '')}`, '_blank')
}
export const importData = (formData) =>
  request.post('/export/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
export const getUsageStats = () => request.get('/usage/stats')

export const submitFeedback = (chatId, messageId, rating, { comment, agentType, intent } = {}) =>
  request.post('/feedback', null, {
    params: { chatId, messageId, rating, comment, agentType, intent }
  })

export const getFeedbackStats = () => request.get('/feedback/stats')

export const getMyCompanion = () => request.get('/companion/me')
export const updateMyCompanion = (payload) => request.put('/companion/me', payload)

export const listDigitalEmployeeTemplates = () => request.get('/digital-employee/templates')
export const listMyDigitalEmployees = () => request.get('/digital-employee/mine')
export const createDigitalEmployee = (payload) => request.post('/digital-employee', payload)
export const updateDigitalEmployee = (id, payload) => request.put(`/digital-employee/${id}`, payload)
export const rollbackDigitalEmployee = (id, version) =>
  request.post(`/digital-employee/${id}/rollback`, null, { params: { version } })
export const setActiveDigitalEmployee = (id) => request.post(`/digital-employee/${id}/activate`)

export const approveHitl = (approvalId) =>
  request.post('/hitl/approve', null, { params: { approvalId } })

export const rejectHitl = (approvalId) =>
  request.post('/hitl/reject', null, { params: { approvalId } })

export { saveAuth, clearAuth, API_BASE_URL }
export default request

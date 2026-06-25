import axios from 'axios'

const API_BASE_URL = process.env.NODE_ENV === 'production'
  ? '/api'
  : 'http://localhost:8123/api'

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000
})

// 请求拦截：自动带上 Token
request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers['Authorization'] = `Bearer ${token}`
  return config
})

// 响应拦截：401 时自动重新登录并重试
let isRefreshing = false
let refreshQueue = []

request.interceptors.response.use(
  response => response,
  async error => {
    const originalRequest = error.config
    // 只处理 401，且不重试登录接口本身
    if (error.response?.status === 401 && !originalRequest._retry && !originalRequest.url.includes('/session/login')) {
      if (isRefreshing) {
        // 排队等待刷新完成
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
        // Clear invalid token and re-login, preserving userId for session ownership
        const oldUserId = localStorage.getItem('userId')
        localStorage.removeItem('token')
        const res = await login('游客', oldUserId)
        const newToken = res.data.data.token
        localStorage.setItem('token', newToken)
        localStorage.setItem('userId', res.data.data.userId)

        // 重试排队的请求
        refreshQueue.forEach(p => p.resolve(newToken))
        refreshQueue = []

        originalRequest.headers['Authorization'] = `Bearer ${newToken}`
        return request(originalRequest)
      } catch (e) {
        refreshQueue.forEach(p => p.reject(e))
        refreshQueue = []
        return Promise.reject(e)
      } finally {
        isRefreshing = false
      }
    }
    return Promise.reject(error)
  }
)

// 封装 SSE 连接
export const connectSSE = (url, params, headers = {}) => {
  const queryString = Object.keys(params)
    .map(k => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
    .join('&')
  const fullUrl = `${API_BASE_URL}${url}?${queryString}`
  return new EventSource(fullUrl)
}

// 游客登录（支持复用 userId 以保持会话归属）
export const login = (username = '游客', userId) =>
  request.post('/session/login', null, { params: { username, ...(userId ? { userId } : {}) } })

// 创建会话
export const createSession = (title = '新对话') =>
  request.post('/session/create', null, { params: { title } })

// 获取会话列表
export const listSessions = () => request.get('/session/list')

// 删除会话
export const deleteSession = (chatId) => request.delete(`/session/${chatId}`)

// 职场顾问 SSE 流式对话
export const chatWithAiChat = (message, chatId) =>
  connectSSE('/ai/ai_chat/chat/sse', { message, chatId })

// Orchestrator 智能路由对话（带 Token）
export const chatWithOrchestrator = (message, chatId) => {
  const token = localStorage.getItem('token')
  const params = { message, chatId }
  // EventSource 不支持自定义 header，Token 通过 URL 参数传递供后端鉴权
  if (token) params.token = token
  const queryString = Object.keys(params)
    .map(k => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
    .join('&')
  const fullUrl = `${API_BASE_URL}/ai/orchestrator/chat?${queryString}`
  return new EventSource(fullUrl)
}

// Manus 超级智能体
export const chatWithManus = (message) =>
  connectSSE('/ai/manus/chat', { message })

// 上传知识库文档
export const uploadDocument = (file, status = '通用') => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('status', status)
  return request.post('/document/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 查看当前用户画像（JWT 自动通过拦截器带上）
export const getMyProfile = () => request.get('/profile/me')

// 清空当前用户画像
export const clearMyProfile = () => request.delete('/profile/me')

// 管理员查询交付物列表（GET /artifact/list，params 含可选 userId/chatId/type；JWT 自动通过拦截器带上）
export const listArtifacts = (params = {}) => request.get('/artifact/list', { params })

// 管理员查看交付物详情（GET /artifact/{artifactId}，含完整 content）
export const getArtifactDetail = (artifactId) => request.get(`/artifact/${artifactId}`)

// ===== Trace API =====

// 查询单条轨迹详情（JWT via Authorization header, interceptor auto-injects）
export const getTrace = (traceId) => request.get(`/trace/${traceId}`)

// 查询某会话的所有轨迹（分页）
export const getTracesByChat = (chatId, pageNum = 1, pageSize = 20) =>
  request.get(`/trace/chat/${chatId}`, { params: { pageNum, pageSize } })

// 查询某用户的所有轨迹（分页）
export const getTracesByUser = (userId, pageNum = 1, pageSize = 20) =>
  request.get(`/trace/user/${userId}`, { params: { pageNum, pageSize } })

// ===== Session API =====

// 获取会话历史消息
export const getChatMessages = (chatId) => request.get(`/session/${chatId}/messages`)

// 重命名会话
export const renameSession = (chatId, title) =>
  request.put(`/session/${chatId}/title`, { title })

// 归档会话
export const archiveSession = (chatId) => request.put(`/session/${chatId}/archive`)

// 取消归档
export const unarchiveSession = (chatId) => request.put(`/session/${chatId}/unarchive`)

// 获取已归档会话列表
export const listArchivedSessions = () => request.get('/session/archived')

// 恢复已删除会话
export const restoreSession = (chatId) => request.put(`/session/${chatId}/restore`)

// 获取回收站列表
export const listTrashSessions = () => request.get('/session/trash')

// 搜索会话
export const searchSessions = (keyword) =>
  request.get('/session/search', { params: { keyword } })

// ===== Favorite API =====

// 添加收藏
export const addFavorite = (chatId, messageId, content, role) =>
  request.post('/favorite', { chatId, messageId, content, role })

// 取消收藏
export const removeFavorite = (favoriteId) =>
  request.delete(`/favorite/${favoriteId}`)

// 收藏列表
export const listFavorites = () => request.get('/favorite/list')

// ===== Export/Import API =====

// 导出所有数据为 ZIP
export const exportAll = () => {
  const token = localStorage.getItem('token')
  const base = process.env.NODE_ENV === 'production' ? '/api' : 'http://localhost:8123/api'
  window.open(`${base}/export/all?token=${token}`, '_blank')
}

// 导入数据
export const importData = (formData) =>
  request.post('/export/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })

// ===== Usage API =====

// 获取用量统计
export const getUsageStats = () => request.get('/usage/stats')

export default { chatWithAiChat, chatWithManus, chatWithOrchestrator, login, createSession, listSessions, deleteSession, getMyProfile, clearMyProfile, listArtifacts, getArtifactDetail, getTrace, getTracesByChat, getTracesByUser, getChatMessages, renameSession, archiveSession, unarchiveSession, listArchivedSessions, restoreSession, listTrashSessions }

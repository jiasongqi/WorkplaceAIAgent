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

// 封装 SSE 连接
export const connectSSE = (url, params, headers = {}) => {
  const queryString = Object.keys(params)
    .map(k => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
    .join('&')
  const fullUrl = `${API_BASE_URL}${url}?${queryString}`
  return new EventSource(fullUrl)
}

// 游客登录
export const login = (username = '游客') =>
  request.post('/session/login', null, { params: { username } })

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
  const queryString = Object.keys(params)
    .map(k => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
    .join('&')
  const fullUrl = `${API_BASE_URL}/ai/orchestrator/chat?${queryString}`
  // EventSource 不支持自定义 header，Token 通过 URL 参数传递（简化方案）
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

export default { chatWithAiChat, chatWithManus, chatWithOrchestrator, login, createSession, listSessions, deleteSession }

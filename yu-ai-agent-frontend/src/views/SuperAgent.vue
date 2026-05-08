<template>
  <div class="super-agent-container">
    <div class="header">
      <div class="back-button" @click="goBack">← 返回</div>
      <h1 class="title">🤖 AI 超级智能体</h1>
      <div class="placeholder"></div>
    </div>

    <div class="content-wrapper">
      <!-- 左侧：对话区 -->
      <div class="chat-area">
        <div class="chat-messages" ref="messagesContainer">
          <div v-for="(msg, index) in messages" :key="index" class="message-wrapper">
            <div v-if="!msg.isUser" class="message ai-message">
              <div class="avatar ai-avatar">🤖</div>
              <div class="message-bubble ai-bubble">
                <div class="message-content" v-html="renderMarkdown(msg.content)"></div>
                <span v-if="isStreaming && index === messages.length - 1" class="typing-cursor">▋</span>
                <div class="message-time">{{ formatTime(msg.time) }}</div>
              </div>
            </div>
            <div v-else class="message user-message">
              <div class="message-bubble user-bubble">
                <div class="message-content">{{ msg.content }}</div>
                <div class="message-time">{{ formatTime(msg.time) }}</div>
              </div>
              <div class="avatar user-avatar">我</div>
            </div>
          </div>

          <div v-if="isThinking" class="thinking-bubble">
            <div class="avatar ai-avatar">🤖</div>
            <div class="thinking-dots"><span></span><span></span><span></span></div>
          </div>
        </div>

        <div class="input-area">
          <textarea
            v-model="inputMessage"
            @keydown.enter.exact.prevent="sendMessage"
            @keydown.shift.enter="inputMessage += '\n'"
            placeholder="输入任务描述... (Enter 发送，Shift+Enter 换行)"
            :disabled="isStreaming"
            rows="2"
          ></textarea>
          <button @click="sendMessage" :disabled="isStreaming || !inputMessage.trim()" class="send-btn">
            {{ isStreaming ? '执行中...' : '执行' }}
          </button>
        </div>
      </div>

      <!-- 右侧：执行进度面板 -->
      <div class="progress-panel" v-if="steps.length > 0 || isStreaming">
        <div class="panel-header">
          <span class="panel-title">⚡ 执行进度</span>
          <span class="step-count">{{ steps.length }} 步</span>
        </div>
        <div class="steps-list" ref="stepsContainer">
          <div
            v-for="(step, index) in steps"
            :key="index"
            class="step-item"
            :class="step.status"
          >
            <div class="step-header">
              <div class="step-number">{{ index + 1 }}</div>
              <div class="step-summary">{{ step.summary }}</div>
              <div class="step-status-icon">
                <span v-if="step.status === 'done'">✓</span>
                <span v-else-if="step.status === 'running'" class="spin">⟳</span>
                <span v-else-if="step.status === 'error'">✗</span>
              </div>
            </div>
            <div v-if="step.tool" class="step-tool">
              <span class="tool-label">工具</span>
              <span class="tool-name">{{ step.tool }}</span>
            </div>
            <div v-if="step.detail" class="step-detail">{{ step.detail }}</div>
          </div>

          <div v-if="isStreaming" class="step-item running">
            <div class="step-header">
              <div class="step-number spin-num">⟳</div>
              <div class="step-summary">思考中...</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import { marked } from 'marked'
import { chatWithManus } from '../api'

useHead({ title: 'AI超级智能体 - 职场生存智囊' })

const router = useRouter()
const messagesContainer = ref(null)
const stepsContainer = ref(null)
const inputMessage = ref('')
const messages = ref([])
const steps = ref([])
const isStreaming = ref(false)
const isThinking = ref(false)

let eventSource = null

// 初始欢迎消息
messages.value.push({
  content: '你好！我是 AI 超级智能体，可以帮你完成复杂任务：联网搜索、生成 PDF、执行代码、下载文件等。请描述你的需求。',
  isUser: false,
  time: Date.now()
})

const addMessage = (content, isUser) => {
  messages.value.push({ content, isUser, time: Date.now() })
  scrollToBottom()
}

const sendMessage = () => {
  if (!inputMessage.value.trim() || isStreaming.value) return
  const msg = inputMessage.value.trim()
  inputMessage.value = ''
  addMessage(msg, true)
  isThinking.value = true
  isStreaming.value = true
  steps.value = []

  if (eventSource) eventSource.close()

  // 添加空 AI 消息占位
  messages.value.push({ content: '', isUser: false, time: Date.now() })
  const aiMsgIndex = messages.value.length - 1

  eventSource = chatWithManus(msg)

  eventSource.onmessage = (e) => {
    isThinking.value = false
    const data = e.data
    if (!data || data === '[DONE]') {
      isStreaming.value = false
      eventSource.close()
      return
    }
    // 解析步骤信息（格式：Step N: ...）
    const stepMatch = data.match(/^Step (\d+): (.+)/)
    if (stepMatch) {
      const stepNum = parseInt(stepMatch[1]) - 1
      const summary = stepMatch[2].slice(0, 60)
      if (steps.value[stepNum]) {
        steps.value[stepNum].status = 'done'
        steps.value[stepNum].detail = stepMatch[2]
      } else {
        steps.value.push({ summary, detail: stepMatch[2], status: 'done', tool: '' })
      }
      scrollSteps()
    }
    messages.value[aiMsgIndex].content += data + '\n'
    scrollToBottom()
  }

  eventSource.onerror = () => {
    isThinking.value = false
    isStreaming.value = false
    eventSource.close()
    if (!messages.value[aiMsgIndex].content) {
      messages.value[aiMsgIndex].content = '连接出现问题，请重试。'
    }
  }
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

const scrollSteps = async () => {
  await nextTick()
  if (stepsContainer.value) {
    stepsContainer.value.scrollTop = stepsContainer.value.scrollHeight
  }
}

const goBack = () => router.push('/')

const formatTime = (ts) => new Date(ts).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })

const renderMarkdown = (text) => {
  if (!text) return ''
  return marked.parse(text)
}

onBeforeUnmount(() => { if (eventSource) eventSource.close() })
</script>

<style scoped>
.super-agent-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f0f2f5;
  overflow: hidden;
}

.header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 14px 24px;
  background: #3f51b5;
  color: white;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}

.back-button { cursor: pointer; font-size: 15px; opacity: 0.85; transition: opacity 0.2s; }
.back-button:hover { opacity: 1; }
.title { font-size: 18px; font-weight: bold; margin: 0; text-align: center; }
.placeholder { justify-self: end; }

.content-wrapper {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* 对话区 */
.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message { display: flex; align-items: flex-start; gap: 10px; max-width: 80%; }
.ai-message { align-self: flex-start; }
.user-message { align-self: flex-end; flex-direction: row-reverse; }

.avatar {
  width: 36px; height: 36px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; flex-shrink: 0;
}
.user-avatar { background: #3f51b5; color: white; font-size: 13px; font-weight: bold; }

.message-bubble { padding: 12px 16px; border-radius: 16px; max-width: 100%; }
.ai-bubble { background: white; color: #1f2937; border-bottom-left-radius: 4px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); }
.user-bubble { background: #3f51b5; color: white; border-bottom-right-radius: 4px; }

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
.ai-bubble :deep(a) { color: #3f51b5; text-decoration: underline; }
.message-time { font-size: 11px; opacity: 0.5; margin-top: 6px; text-align: right; }

.typing-cursor { display: inline-block; animation: blink 0.7s infinite; margin-left: 2px; }
@keyframes blink { 0%, 100% { opacity: 0; } 50% { opacity: 1; } }

.thinking-bubble { display: flex; align-items: center; gap: 10px; align-self: flex-start; }
.thinking-dots {
  background: white; border-radius: 16px; border-bottom-left-radius: 4px;
  padding: 14px 18px; display: flex; gap: 5px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
}
.thinking-dots span {
  width: 8px; height: 8px; background: #9ca3af; border-radius: 50%;
  animation: bounce 1.2s infinite;
}
.thinking-dots span:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce { 0%, 60%, 100% { transform: translateY(0); } 30% { transform: translateY(-8px); } }

.input-area {
  display: flex; gap: 12px; padding: 16px 20px;
  background: white; border-top: 1px solid #e5e7eb; align-items: flex-end;
}
.input-area textarea {
  flex: 1; border: 1px solid #d1d5db; border-radius: 12px;
  padding: 10px 14px; font-size: 15px; resize: none; outline: none;
  font-family: inherit; transition: border-color 0.2s; line-height: 1.5;
}
.input-area textarea:focus { border-color: #3f51b5; }
.input-area textarea:disabled { background: #f9fafb; }

.send-btn {
  background: #3f51b5; color: white; border: none; border-radius: 12px;
  padding: 10px 22px; font-size: 15px; cursor: pointer; transition: all 0.2s;
  white-space: nowrap; height: 44px;
}
.send-btn:hover:not(:disabled) { background: #303f9f; }
.send-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* 进度面板 */
.progress-panel {
  width: 300px;
  min-width: 300px;
  background: #1a1f2e;
  display: flex;
  flex-direction: column;
  border-left: 1px solid rgba(255,255,255,0.08);
}

.panel-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 16px; border-bottom: 1px solid rgba(255,255,255,0.08);
}
.panel-title { color: white; font-size: 14px; font-weight: 600; }
.step-count { color: rgba(255,255,255,0.4); font-size: 12px; }

.steps-list { flex: 1; overflow-y: auto; padding: 12px; display: flex; flex-direction: column; gap: 8px; }

.step-item {
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 8px; padding: 10px 12px;
}
.step-item.done { border-color: rgba(16,185,129,0.3); }
.step-item.running { border-color: rgba(99,102,241,0.4); animation: pulse 1.5s infinite; }
.step-item.error { border-color: rgba(239,68,68,0.3); }

@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.7; } }

.step-header { display: flex; align-items: center; gap: 8px; }
.step-number {
  width: 22px; height: 22px; border-radius: 50%;
  background: rgba(99,102,241,0.3); color: #a5b4fc;
  font-size: 11px; font-weight: bold;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.step-summary { flex: 1; color: rgba(255,255,255,0.8); font-size: 13px; }
.step-status-icon { color: #34d399; font-size: 14px; }
.step-item.error .step-status-icon { color: #f87171; }

.step-tool { display: flex; gap: 6px; align-items: center; margin-top: 6px; }
.tool-label { font-size: 11px; color: rgba(255,255,255,0.3); }
.tool-name { font-size: 11px; color: #60a5fa; background: rgba(96,165,250,0.1); padding: 1px 6px; border-radius: 4px; }

.step-detail { font-size: 12px; color: rgba(255,255,255,0.4); margin-top: 4px; line-height: 1.4; }

.spin { display: inline-block; animation: spin 1s linear infinite; }
.spin-num { animation: spin 1s linear infinite; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

@media (max-width: 768px) {
  .progress-panel { display: none; }
  .chat-messages { padding: 12px; }
  .message { max-width: 92%; }
}
</style>

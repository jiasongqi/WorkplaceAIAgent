<template>
  <div class="super-agent-container">
    <div class="header">
      <div class="back-button" @click="goBack">← 返回</div>
      <h1 class="title"><WpIcon name="agent" :size="20" class="title-icon" /> 超级智能体</h1>
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
          <div v-if="attachedFile" class="attach-row">
            <span>📎 {{ attachedFile.name }}</span>
            <select v-model="attachHint" :disabled="isStreaming">
              <option value="resume">简历</option>
              <option value="offer">Offer</option>
            </select>
            <button type="button" @click="attachedFile = null" :disabled="isStreaming">×</button>
          </div>
          <div class="input-row">
            <input ref="fileInput" type="file" accept=".pdf,.docx,.txt,.md,.csv,.png,.jpg,.jpeg,.webp,.gif" hidden @change="onFileSelected" />
            <button type="button" class="attach-btn" @click="fileInput?.click()" :disabled="isStreaming" title="上传材料（感知预处理）">📎</button>
            <textarea
              v-model="inputMessage"
              @keydown.enter.exact.prevent="sendMessage"
              @keydown.shift.enter="inputMessage += '\n'"
              placeholder="输入任务描述... (可先上传简历/Offer)"
              :disabled="isStreaming || perceptionBusy"
              rows="2"
            ></textarea>
            <button @click="sendMessage" :disabled="isStreaming || perceptionBusy || (!inputMessage.trim() && !attachedFile)" class="send-btn">
              {{ perceptionBusy ? '感知中...' : (isStreaming ? '执行中...' : '执行') }}
            </button>
          </div>
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
import DOMPurify from 'dompurify'
import { chatWithManus, preprocessPerception } from '../api'
import WpIcon from '../components/WpIcon.vue'

useHead({ title: 'AI超级智能体 - 职场生存智囊' })

const router = useRouter()
const messagesContainer = ref(null)
const stepsContainer = ref(null)
const inputMessage = ref('')
const messages = ref([])
const steps = ref([])
const isStreaming = ref(false)
const isThinking = ref(false)
const attachedFile = ref(null)
const fileInput = ref(null)
const attachHint = ref('resume')
const perceptionBusy = ref(false)

let eventSource = null

// 初始欢迎消息
messages.value.push({
  content: '你好！我是 AI 超级智能体，可以帮你完成复杂任务：联网搜索、生成 PDF、执行代码、下载文件等。也可先上传简历/Offer 做感知预处理。',
  isUser: false,
  time: Date.now()
})

const addMessage = (content, isUser) => {
  messages.value.push({ content, isUser, time: Date.now() })
  scrollToBottom()
}

const onFileSelected = (e) => {
  const file = e.target.files?.[0]
  if (!file) return
  if (file.size > 10 * 1024 * 1024) {
    alert('文件不能超过 10MB')
    e.target.value = ''
    return
  }
  attachedFile.value = file
  const name = (file.name || '').toLowerCase()
  if (/offer|薪|salary/.test(name)) attachHint.value = 'offer'
  else if (/resume|简历|cv/.test(name)) attachHint.value = 'resume'
  e.target.value = ''
}

const sendMessage = async () => {
  if (isStreaming.value || perceptionBusy.value) return
  const msg = inputMessage.value.trim()
  const file = attachedFile.value
  if (!msg && !file) return

  inputMessage.value = ''
  attachedFile.value = null

  let finalMsg = msg
  const displayMsg = file
    ? (msg ? `📎 ${file.name}\n${msg}` : `📎 ${file.name}\n请根据材料完成分析`)
    : msg

  if (file) {
    perceptionBusy.value = true
    try {
      const res = await preprocessPerception(file, attachHint.value)
      const data = res.data?.data
      if (!data?.promptBlock) throw new Error(res.data?.message || '感知结果为空')
      // Manus 走 GET SSE：截断 promptBlock，避免 URL 过长
      let block = data.promptBlock
      if (block.length > 2500) {
        block = block.slice(0, 2500) + '\n…（感知文本已截断，建议用职场顾问页上传以绑定完整材料）'
      }
      const userAsk = msg || '请根据感知预处理结果完成分析并给出可执行建议。'
      finalMsg = `${block}\n\n【用户补充】\n${userAsk}`
    } catch (e) {
      perceptionBusy.value = false
      addMessage(`⚠️ 感知预处理失败: ${e.response?.data?.message || e.message || '请重试'}`, false)
      if (!msg) return
      finalMsg = msg
    } finally {
      perceptionBusy.value = false
    }
  }

  addMessage(displayMsg, true)
  isThinking.value = true
  isStreaming.value = true
  steps.value = []

  if (eventSource) eventSource.close()

  // 添加空 AI 消息占位
  messages.value.push({ content: '', isUser: false, time: Date.now() })
  const aiMsgIndex = messages.value.length - 1

  eventSource = chatWithManus(finalMsg || msg)
  let streamFinished = false

  const finishStream = () => {
    if (streamFinished) return
    streamFinished = true
    isThinking.value = false
    isStreaming.value = false
    if (eventSource) eventSource.close()
  }

  eventSource.onmessage = (e) => {
    isThinking.value = false
    const data = e.data
    if (!data || data === '[DONE]') {
      finishStream()
      return
    }
    // 解析步骤信息（格式：Step N: ...）
    const stepMatch = data.match(/^Step (\d+):\s*([\s\S]*)/)
    if (stepMatch) {
      const stepNum = parseInt(stepMatch[1]) - 1
      const detail = stepMatch[2].trim()
      const summary = detail.slice(0, 60)
      if (steps.value[stepNum]) {
        steps.value[stepNum].status = 'done'
        steps.value[stepNum].detail = detail
      } else {
        steps.value.push({ summary, detail, status: 'done', tool: '' })
      }
      scrollSteps()
      // 同步写入聊天区，避免只更新步骤面板导致消息空白
      if (detail && detail !== '思考完成 - 无需行动') {
        if (messages.value[aiMsgIndex].content) {
          messages.value[aiMsgIndex].content += '\n\n' + detail
        } else {
          messages.value[aiMsgIndex].content = detail
        }
        scrollToBottom()
      }
      return
    }
    // 解析工具调用信息
    const toolMatch = data.match(/^\[Tool: (.+?)\](.*)/)
    if (toolMatch) {
      const lastStep = steps.value[steps.value.length - 1]
      if (lastStep) lastStep.tool = toolMatch[1]
      // Don't show raw tool markers in chat
      if (toolMatch[2]?.trim()) {
        messages.value[aiMsgIndex].content += toolMatch[2].trim() + '\n'
        scrollToBottom()
      }
      return
    }
    // 过滤掉纯分隔线和空行噪音
    if (data.match(/^[-=]{3,}$/) || data.trim() === '') {
      return
    }
    messages.value[aiMsgIndex].content += data + '\n'
    scrollToBottom()
  }

  eventSource.onerror = () => {
    // SSE 正常结束后浏览器也会触发 onerror；有内容或已收到 [DONE] 时不当成失败
    const hasContent = !!messages.value[aiMsgIndex]?.content
    const hasSteps = steps.value.length > 0
    if (streamFinished || hasContent || hasSteps) {
      if (!hasContent && hasSteps) {
        const last = steps.value[steps.value.length - 1]
        if (last?.detail) {
          messages.value[aiMsgIndex].content = last.detail
        }
      }
      finishStream()
      return
    }
    finishStream()
    messages.value[aiMsgIndex].content =
      '连接出现问题，请重试。若持续失败，请检查后端是否启动，以及 DashScope API 额度是否充足。'
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
  // Clean up common messy patterns before rendering
  let cleaned = text
    .replace(/\n{3,}/g, '\n\n')           // collapse 3+ newlines to 2
    .replace(/^[\s\n]+/, '')               // trim leading whitespace
    .replace(/[\s\n]+$/, '')               // trim trailing whitespace
  return DOMPurify.sanitize(marked.parse(cleaned))
}

onBeforeUnmount(() => { if (eventSource) eventSource.close() })
</script>

<style scoped>
.super-agent-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 14px 24px;
  border-bottom: 1px solid var(--glass-border);
}

.back-button { cursor: pointer; font-size: 15px; color: var(--t3); transition: color 0.2s; }
.back-button:hover { color: var(--t1); }
.title { font-size: 16px; font-weight: 600; margin: 0; text-align: center; color: var(--t1); display: inline-flex; align-items: center; justify-content: center; gap: 8px; }
.title-icon { color: var(--gold-text); }
.placeholder { justify-self: end; }

.content-wrapper {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 22px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message { display: flex; align-items: flex-start; gap: 12px; max-width: 80%; }
.ai-message { align-self: flex-start; }
.user-message { align-self: flex-end; flex-direction: row-reverse; }

.avatar {
  width: 32px; height: 32px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 16px; flex-shrink: 0;
  background: var(--layer2);
}
.user-avatar { background: var(--user-orb); color: var(--t1); font-size: 12px; font-weight: 600; }

.message-bubble { padding: 16px 20px; border-radius: var(--r-lg); max-width: 100%; }
.ai-bubble { background: var(--layer1); border: 1px solid var(--glass-border); border-top-left-radius: 4px; color: var(--t1); }
.user-bubble { background: linear-gradient(135deg, var(--gold-soft), var(--gold-dim)); border: 1px solid var(--gold-border-soft); color: var(--t1); }

.message-content { font-size: 14px; line-height: 1.75; word-break: break-word; }

.ai-bubble :deep(h1),
.ai-bubble :deep(h2),
.ai-bubble :deep(h3) { font-weight: 600; margin: 10px 0 6px; color: var(--t1); }
.ai-bubble :deep(p) { margin: 6px 0; }
.ai-bubble :deep(ul), .ai-bubble :deep(ol) { padding-left: 20px; margin: 6px 0; }
.ai-bubble :deep(li) { margin: 3px 0; }
.ai-bubble :deep(strong) { font-weight: 600; color: var(--t1); }
.ai-bubble :deep(code) {
  background: var(--layer2); border-radius: 4px;
  padding: 2px 6px; font-size: 12px; font-family: var(--mono); color: var(--t2);
}
.ai-bubble :deep(pre) {
  background: var(--layer2); border-radius: var(--r-sm);
  padding: 12px 14px; overflow-x: auto; margin: 8px 0;
}
.ai-bubble :deep(pre code) { background: none; color: var(--t2); padding: 0; }
.ai-bubble :deep(blockquote) { border-left: 3px solid var(--glass-border); padding-left: 12px; color: var(--t3); margin: 6px 0; }
.ai-bubble :deep(a) { color: var(--gold-text); text-decoration: underline; }
.message-time { font-size: 11px; color: var(--t4); margin-top: 6px; text-align: right; }

.typing-cursor { display: inline-block; animation: blink 0.7s infinite; margin-left: 2px; }
@keyframes blink { 0%, 100% { opacity: 0; } 50% { opacity: 1; } }

.thinking-bubble { display: flex; align-items: center; gap: 12px; align-self: flex-start; }
.thinking-dots {
  background: var(--layer1); border: 1px solid var(--glass-border); border-radius: var(--r-lg); border-top-left-radius: 4px;
  padding: 14px 18px; display: flex; gap: 5px;
}
.thinking-dots span {
  width: 6px; height: 6px; background: var(--t4); border-radius: 50%;
  animation: jump 1.4s ease-in-out infinite;
}
.thinking-dots span:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots span:nth-child(3) { animation-delay: 0.4s; }

.input-area {
  display: flex; flex-direction: column; gap: 8px; padding: 16px 22px 24px;
  border-top: 1px solid var(--glass-border);
}
.attach-row {
  display: flex; align-items: center; gap: 8px;
  font-size: 12px; color: var(--t2);
  background: var(--layer2); border-radius: var(--r-sm); padding: 6px 10px;
}
.attach-row select {
  border: 1px solid var(--glass-border); background: var(--layer1); color: var(--t2);
  border-radius: 6px; font-size: 12px; padding: 2px 6px;
}
.attach-row button { background: none; border: none; color: var(--t4); cursor: pointer; font-size: 16px; }
.input-row { display: flex; align-items: flex-end; gap: 10px; }
.attach-btn {
  width: 40px; height: 44px; border-radius: var(--r-md);
  border: 1px solid var(--glass-border); background: var(--layer1); color: var(--t2);
  cursor: pointer; flex-shrink: 0;
}
.attach-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.input-area textarea {
  flex: 1; border: 1px solid var(--glass-border); border-radius: var(--r-lg);
  padding: 12px 18px; font-size: 14px; resize: none; outline: none;
  font-family: var(--font); transition: border-color 0.3s; line-height: 1.6;
  background: var(--layer1); color: var(--t1);
}
.input-area textarea::placeholder { color: var(--t4); }
.input-area textarea:focus { border-color: var(--gold-border); box-shadow: 0 0 0 3px var(--gold-dim); }
.input-area textarea:disabled { opacity: 0.5; }

.send-btn {
  background: var(--gold); color: var(--abyss); border: none; border-radius: var(--r-md);
  padding: 12px 22px; font-size: 14px; font-weight: 600; cursor: pointer; transition: all 0.25s var(--spring);
  white-space: nowrap; height: 44px;
  box-shadow: 0 4px 14px var(--gold-glow);
}
.send-btn:hover:not(:disabled) { transform: scale(1.03); box-shadow: 0 6px 22px var(--gold-glow-strong); }
.send-btn:active:not(:disabled) { transform: scale(0.97); }
.send-btn:disabled { opacity: 0.4; cursor: not-allowed; transform: none; box-shadow: none; }

/* Progress panel */
.progress-panel {
  width: 300px; min-width: 300px;
  background: var(--deep);
  display: flex; flex-direction: column;
  border-left: 1px solid var(--glass-border);
}

.panel-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 16px; border-bottom: 1px solid var(--glass-border);
}
.panel-title { color: var(--t1); font-size: 14px; font-weight: 600; }
.step-count { color: var(--t4); font-size: 12px; }

.steps-list { flex: 1; overflow-y: auto; padding: 12px; display: flex; flex-direction: column; gap: 8px; }

.step-item {
  background: var(--layer1); border: 1px solid var(--glass-border);
  border-radius: var(--r-sm); padding: 10px 12px;
}
.step-item.done { border-color: rgba(52,211,153,0.3); }
.step-item.running { border-color: var(--gold-border); animation: pulse 1.5s infinite; }
.step-item.error { border-color: rgba(248,113,113,0.3); }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.7; } }

.step-header { display: flex; align-items: center; gap: 8px; }
.step-number {
  width: 22px; height: 22px; border-radius: 50%;
  background: var(--gold-soft); color: var(--gold-text);
  font-size: 11px; font-weight: bold;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.step-summary { flex: 1; color: var(--t2); font-size: 13px; }
.step-status-icon { color: var(--ok); font-size: 14px; }
.step-item.error .step-status-icon { color: var(--danger); }

.step-tool { display: flex; gap: 6px; align-items: center; margin-top: 6px; }
.tool-label { font-size: 11px; color: var(--t4); }
.tool-name { font-size: 11px; color: var(--gold-text); background: var(--gold-soft); padding: 1px 6px; border-radius: 4px; }

.step-detail { font-size: 12px; color: var(--t4); margin-top: 4px; line-height: 1.4; }

.spin { display: inline-block; animation: spin 1s linear infinite; }
.spin-num { animation: spin 1s linear infinite; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

@media (max-width: 768px) {
  .progress-panel { display: none; }
  .chat-messages { padding: 12px; }
  .message { max-width: 92%; }
}
</style>

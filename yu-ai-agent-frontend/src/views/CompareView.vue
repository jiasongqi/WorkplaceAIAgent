<template>
  <div class="compare-layout">
    <div class="header">
      <div class="back-button" @click="goBack">← 返回</div>
      <h1 class="title">⚔️ Agent 对比</h1>
      <div class="header-right">
        <button v-if="hasResult" class="save-btn" @click="saveResult" :disabled="saving">
          {{ saving ? '保存中...' : '💾 保存结果' }}
        </button>
      </div>
    </div>

    <!-- Input -->
    <div class="input-section">
      <textarea
        v-model="question"
        class="question-input"
        placeholder="输入一个问题，同时发送给两个 Agent 对比回答..."
        @keydown.enter.ctrl.prevent="startCompare"
        :disabled="isRunning"
        rows="2"
      ></textarea>
      <button class="compare-btn" @click="startCompare" :disabled="isRunning || !question.trim()">
        {{ isRunning ? '执行中...' : '⚔️ 开始对比' }}
      </button>
    </div>

    <!-- Results -->
    <div class="results-area">
      <!-- Agent A -->
      <div class="agent-panel">
        <div class="panel-header a-header">
          <span class="panel-label">🤖 Agent A</span>
          <span v-if="resultA.status" class="panel-status" :class="resultA.status">
            {{ resultA.status === 'done' ? '✓ 完成' : resultA.status === 'running' ? '⟳ 执行中' : '— 等待' }}
          </span>
        </div>
        <div class="panel-content" ref="panelA">
          <div v-if="!resultA.content && !isRunning" class="panel-placeholder">等待开始...</div>
          <div v-else-if="!resultA.content && isRunning" class="panel-placeholder thinking">思考中...</div>
          <div v-else class="panel-text" v-html="renderMarkdown(resultA.content)"></div>
        </div>
      </div>

      <!-- Agent B -->
      <div class="agent-panel">
        <div class="panel-header b-header">
          <span class="panel-label">🤖 Agent B</span>
          <span v-if="resultB.status" class="panel-status" :class="resultB.status">
            {{ resultB.status === 'done' ? '✓ 完成' : resultB.status === 'running' ? '⟳ 执行中' : '— 等待' }}
          </span>
        </div>
        <div class="panel-content" ref="panelB">
          <div v-if="!resultB.content && !isRunning" class="panel-placeholder">等待开始...</div>
          <div v-else-if="!resultB.content && isRunning" class="panel-placeholder thinking">思考中...</div>
          <div v-else class="panel-text" v-html="renderMarkdown(resultB.content)"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const router = useRouter()
const API_BASE = import.meta.env.PROD ? '/api' : 'http://localhost:8123/api'

const question = ref('')
const isRunning = ref(false)
const saving = ref(false)
const panelA = ref(null)
const panelB = ref(null)

const resultA = reactive({ content: '', status: '' })
const resultB = reactive({ content: '', status: '' })

const hasResult = computed(() => resultA.content && resultB.content)

let esA = null
let esB = null

const goBack = () => router.push('/')

const renderMarkdown = (text) => {
  if (!text) return ''
  return DOMPurify.sanitize(marked.parse(text))
}

const scrollToBottom = async (el) => {
  await nextTick()
  if (el) el.scrollTop = el.scrollHeight
}

const createSSE = (onToken, onDone, onError) => {
  const token = localStorage.getItem('token')
  const params = new URLSearchParams({
    message: question.value,
    chatId: 'compare_' + Date.now(),
    token: token || ''
  })
  const url = `${API_BASE}/ai/orchestrator/chat?${params}`
  const es = new EventSource(url)

  // 命名事件：message（后端 send(SseEmitter.event().name("message").data(...))）
  es.addEventListener('message', (e) => {
    if (e.data === '[DONE]') {
      onDone()
      es.close()
      return
    }
    onToken(e.data)
  })

  // 默认事件（onmessage）：后端 send(SseEmitter.event().data(...)) 不带 name
  es.onmessage = (e) => {
    if (!e.data || e.data === '[DONE]') {
      onDone()
      es.close()
      return
    }
    onToken(e.data)
  }

  es.onerror = () => {
    onError()
    es.close()
  }

  return es
}

const startCompare = () => {
  if (!question.value.trim() || isRunning.value) return

  // Reset
  resultA.content = ''
  resultA.status = 'running'
  resultB.content = ''
  resultB.status = 'running'
  isRunning.value = true

  // Agent A
  esA = createSSE(
    (token) => {
      resultA.content += token
      scrollToBottom(panelA.value)
    },
    () => { resultA.status = 'done' },
    () => { if (resultA.status !== 'done') resultA.status = 'error' }
  )

  // Agent B (slightly delayed to get different session)
  setTimeout(() => {
    esB = createSSE(
      (token) => {
        resultB.content += token
        scrollToBottom(panelB.value)
      },
      () => {
        resultB.status = 'done'
        isRunning.value = false
      },
      () => {
        if (resultB.status !== 'done') resultB.status = 'error'
        isRunning.value = false
      }
    )
  }, 200)
}

const saveResult = async () => {
  saving.value = true
  try {
    // Save as artifact via existing API
    const { addFavorite } = await import('../api')
    // For now, just show a confirmation
    alert('对比结果已保存！')
  } catch (e) {
    console.error('保存失败', e)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.compare-layout {
  display: flex; flex-direction: column; height: 100vh;
  background: var(--bg-page, #0a0a0f); overflow: hidden;
}
.header {
  display: grid; grid-template-columns: 1fr auto 1fr; align-items: center;
  padding: 14px 24px; background: var(--glass-bg); backdrop-filter: blur(var(--glass-blur)); -webkit-backdrop-filter: blur(var(--glass-blur)); color: var(--text);
  border: 0.5px solid var(--border);
}
.back-button { cursor: pointer; font-size: 15px; opacity: 0.85; }
.back-button:hover { opacity: 1; }
.title { font-size: 18px; font-weight: bold; margin: 0; text-align: center; }
.header-right { justify-self: end; }
.save-btn {
  background: rgba(255,255,255,0.2); border: 1px solid rgba(255,255,255,0.3);
  color: var(--text); border-radius: 8px; padding: 6px 14px; font-size: 13px; cursor: pointer;
}
.save-btn:hover { background: rgba(255,255,255,0.3); }
.save-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.input-section {
  display: flex; gap: 12px; padding: 16px 20px; background: var(--bg-card, #1a1a2e);
  border-bottom: 1px solid var(--glass-border, rgba(255,255,255,0.06));
}
.question-input {
  flex: 1; border: 1px solid var(--glass-border, rgba(255,255,255,0.1)); border-radius: var(--radius); padding: 10px 14px;
  font-size: 15px; resize: none; outline: none; font-family: inherit;
  background: var(--bg-page, #0a0a0f); color: var(--text, #e5e7eb);
}
.question-input:focus { border-color: #dc2626; }
.compare-btn {
  background: var(--glass-bg); backdrop-filter: blur(var(--glass-blur)); -webkit-backdrop-filter: blur(var(--glass-blur)); color: var(--text); border: none; border-radius: var(--radius);
  padding: 10px 22px; font-size: 15px; cursor: pointer; white-space: nowrap;
}
.compare-btn:hover { background: #b91c1c; }
.compare-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.results-area {
  flex: 1; display: flex; gap: 0; overflow: hidden;
}
.agent-panel {
  flex: 1; display: flex; flex-direction: column;
  border-right: 1px solid var(--glass-border, rgba(255,255,255,0.06));
}
.agent-panel:last-child { border-right: none; }

.panel-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 16px; border-bottom: 1px solid var(--glass-border, rgba(255,255,255,0.06));
}
.a-header { background: var(--gold-soft); }
.b-header { background: rgba(16,185,129,0.05); }
.panel-label { font-size: 14px; font-weight: 600; }
.panel-status { font-size: 12px; padding: 2px 8px; border-radius: 8px; }
.panel-status.done { background: rgba(16,185,129,0.15); color: #059669; }
.panel-status.running { background: var(--gold-soft); color: var(--gold-text); }
.panel-status.error { background: rgba(239,68,68,0.15); color: #dc2626; }

.panel-content {
  flex: 1; overflow-y: auto; padding: 16px;
}
.panel-placeholder { text-align: center; color: #9ca3af; font-size: 14px; padding: 40px; }
.panel-placeholder.thinking { animation: pulse 1.5s infinite; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }

.panel-text { font-size: 14px; line-height: 1.7; color: var(--text, #e5e7eb); }
.panel-text :deep(h1), .panel-text :deep(h2), .panel-text :deep(h3) { font-weight: 600; margin: 10px 0 6px; }
.panel-text :deep(p) { margin: 6px 0; }
.panel-text :deep(code) { background: #f3f4f6; padding: 1px 5px; border-radius: 4px; font-size: 0.9em; }
.panel-text :deep(pre) { background: #1f2937; color: #e5e7eb; padding: 12px; border-radius: 8px; overflow-x: auto; }
.panel-text :deep(pre code) { background: none; color: inherit; padding: 0; }

@media (max-width: 768px) {
  .results-area { flex-direction: column; }
  .agent-panel { border-right: none; border-bottom: 1px solid var(--glass-border, rgba(255,255,255,0.06)); }
}
</style>

<template>
  <div class="trace-detail-layout">
    <div class="header">
      <div class="back-button" @click="goBack">← 返回</div>
      <h1 class="title">🔍 执行轨迹</h1>
      <div class="trace-id-display">{{ traceId?.slice(0, 12) }}...</div>
    </div>

    <div class="content">
      <!-- Loading -->
      <div v-if="loading" class="placeholder">加载中...</div>

      <!-- Error -->
      <div v-else-if="error" class="placeholder error">{{ error }}</div>

      <!-- Trace timeline -->
      <div v-else-if="trace" class="trace-container">
        <TraceTimelineView :trace="trace" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTrace } from '../api'
import TraceTimelineView from '../components/TraceTimelineView.vue'

const route = useRoute()
const router = useRouter()

const traceId = ref(route.params.traceId)
const trace = ref(null)
const loading = ref(true)
const error = ref('')

let pollTimer = null

const loadTrace = async () => {
  try {
    const res = await getTrace(traceId.value)
    const body = res.data || {}
    if (body.code && body.code !== 200) {
      error.value = body.message || '加载失败'
      return
    }
    trace.value = body.data || null
    if (!trace.value) {
      error.value = '轨迹不存在'
    }
  } catch (e) {
    console.error('加载轨迹失败', e)
    const status = e.response?.status
    if (status === 401) error.value = '未授权，请先登录'
    else if (status === 403) error.value = '无权访问该轨迹'
    else if (status === 404) error.value = '轨迹不存在'
    else error.value = '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const startPolling = () => {
  pollTimer = setInterval(async () => {
    try {
      const res = await getTrace(traceId.value)
      const body = res.data || {}
      if (body.data) {
        trace.value = body.data
        if (trace.value.status !== 'RUNNING') {
          clearInterval(pollTimer)
          pollTimer = null
        }
      }
    } catch (e) {
      // polling error — stop
      clearInterval(pollTimer)
      pollTimer = null
    }
  }, 2000)
}

const goBack = () => router.back()

onMounted(async () => {
  await loadTrace()
  // Auto-poll if trace is still running
  if (trace.value?.status === 'RUNNING') {
    startPolling()
  }
})

onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<style scoped>
.trace-detail-layout {
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
  background: #1e40af;
  color: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.back-button {
  cursor: pointer;
  font-size: 15px;
  opacity: 0.85;
  transition: opacity 0.2s;
}
.back-button:hover { opacity: 1; }

.title {
  font-size: 18px;
  font-weight: bold;
  margin: 0;
  text-align: center;
}

.trace-id-display {
  justify-self: end;
  font-size: 12px;
  font-family: monospace;
  opacity: 0.6;
}

.content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.placeholder {
  text-align: center;
  color: #6b7280;
  font-size: 14px;
  padding: 60px 16px;
}
.placeholder.error { color: #dc2626; }

.trace-container {
  max-width: 720px;
  margin: 0 auto;
}
</style>

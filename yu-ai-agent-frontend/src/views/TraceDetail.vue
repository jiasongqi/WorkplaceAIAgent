<template>
  <div class="trace-detail-layout">
    <!-- Header -->
    <div class="header glass">
      <div class="back-button" @click="goBack">← 返回</div>
      <h1 class="title">执行轨迹</h1>
      <div class="header-right">
        <span class="trace-status" :class="trace?.status?.toLowerCase()">
          {{ trace?.status === 'SUCCESS' ? '成功' : trace?.status === 'RUNNING' ? '运行中' : trace?.status === 'FAILED' ? '失败' : '—' }}
        </span>
        <span class="trace-id">{{ traceId?.slice(0, 12) }}...</span>
      </div>
    </div>

    <!-- Content -->
    <div class="content">
      <div v-if="loading" class="placeholder">加载中...</div>
      <div v-else-if="error" class="placeholder error">{{ error }}</div>

      <div v-else-if="trace" class="three-col">
        <!-- Left: Timeline -->
        <div class="col-timeline">
          <div class="col-header">时间线</div>
          <div class="timeline-list">
            <div
              v-for="(span, i) in trace.spans"
              :key="span.spanId || i"
              class="timeline-item"
              :class="[span.status?.toLowerCase(), { active: selectedSpan === i }]"
              @click="selectedSpan = i"
            >
              <div class="tl-dot"></div>
              <div class="tl-info">
                <span class="tl-label">{{ span.label || span.stepType }}</span>
                <span class="tl-duration">{{ span.durationMs ? span.durationMs + 'ms' : '—' }}</span>
              </div>
            </div>
            <div v-if="!trace.spans?.length" class="empty-hint">暂无步骤</div>
          </div>
        </div>

        <!-- Center: Node detail -->
        <div class="col-detail">
          <div class="col-header">{{ currentSpan?.label || currentSpan?.stepType || '选择一个步骤' }}</div>
          <div v-if="currentSpan" class="detail-body">
            <div class="detail-section">
              <div class="detail-label">类型</div>
              <div class="detail-value mono">{{ currentSpan.stepType }}</div>
            </div>
            <div class="detail-section">
              <div class="detail-label">状态</div>
              <div class="detail-value">
                <span class="status-tag" :class="currentSpan.status?.toLowerCase()">{{ currentSpan.status }}</span>
              </div>
            </div>
            <div v-if="currentSpan.durationMs != null" class="detail-section">
              <div class="detail-label">耗时</div>
              <div class="detail-value mono">{{ currentSpan.durationMs }}ms</div>
            </div>
            <div v-if="currentSpan.label" class="detail-section">
              <div class="detail-label">描述</div>
              <div class="detail-value">{{ currentSpan.label }}</div>
            </div>
            <div v-if="currentSpan.metadata" class="detail-section">
              <div class="detail-label">元数据</div>
              <pre class="detail-meta">{{ formatJson(currentSpan.metadata) }}</pre>
            </div>
          </div>
          <div v-else class="detail-empty">点击左侧步骤查看详情</div>
        </div>

        <!-- Right: Metrics -->
        <div class="col-metrics">
          <div class="col-header">运行指标</div>
          <div class="metrics-body">
            <div class="metric-row">
              <span class="metric-label">总耗时</span>
              <span class="metric-value mono">{{ totalDuration }}</span>
            </div>
            <div class="metric-row">
              <span class="metric-label">步骤数</span>
              <span class="metric-value mono">{{ trace.spans?.length || 0 }}</span>
            </div>
            <div class="metric-row">
              <span class="metric-label">状态</span>
              <span class="metric-value">{{ trace.status }}</span>
            </div>
            <div class="metric-row">
              <span class="metric-label">Trace ID</span>
              <span class="metric-value mono" style="font-size:10px;">{{ trace.traceId?.slice(0, 16) }}...</span>
            </div>
            <div class="metric-row">
              <span class="metric-label">用户</span>
              <span class="metric-value">{{ trace.userId }}</span>
            </div>
            <div class="metric-row">
              <span class="metric-label">会话</span>
              <span class="metric-value mono" style="font-size:10px;">{{ trace.chatId?.slice(0, 12) }}...</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTrace } from '../api'

const route = useRoute()
const router = useRouter()

const traceId = ref(route.params.traceId)
const trace = ref(null)
const loading = ref(true)
const error = ref('')
const selectedSpan = ref(0)

let pollTimer = null

const currentSpan = computed(() => {
  if (!trace.value?.spans?.length) return null
  return trace.value.spans[selectedSpan.value] || null
})

const totalDuration = computed(() => {
  if (!trace.value?.spans?.length) return '—'
  const total = trace.value.spans.reduce((sum, s) => sum + (s.durationMs || 0), 0)
  if (total < 1000) return total + 'ms'
  return (total / 1000).toFixed(1) + 's'
})

const formatJson = (obj) => {
  try { return JSON.stringify(obj, null, 2) } catch { return String(obj) }
}

const goBack = () => router.back()

const loadTrace = async () => {
  try {
    const res = await getTrace(traceId.value)
    const body = res.data || {}
    if (body.code && body.code !== 200) {
      error.value = body.message || '加载失败'
      return
    }
    trace.value = body.data || null
    if (!trace.value) error.value = '轨迹不存在'
  } catch (e) {
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
    } catch {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }, 2000)
}

onMounted(async () => {
  await loadTrace()
  if (trace.value?.status === 'RUNNING') startPolling()
})

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.trace-detail-layout {
  display: flex; flex-direction: column; height: 100vh;
  background: var(--bg); overflow: hidden;
}

/* Header */
.header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 20px;
  background: var(--glass-bg);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border-bottom: 0.5px solid var(--border);
  flex-shrink: 0;
}
.back-button { cursor: pointer; font-size: 13px; color: var(--text-muted); transition: color 0.15s; }
.back-button:hover { color: var(--text); }
.title { font-size: 15px; font-weight: 600; color: var(--text); margin: 0; }
.header-right { display: flex; align-items: center; gap: 8px; }
.trace-status {
  font-size: 11px; padding: 2px 8px; border-radius: var(--radius-sm); font-weight: 500;
}
.trace-status.success { background: var(--success-light); color: var(--success); }
.trace-status.running { background: var(--primary-light); color: var(--glass-bg); }
.trace-status.failed { background: var(--danger-light); color: var(--danger); }
.trace-id { font-size: 11px; color: var(--text-muted); font-family: var(--font-mono); }

/* Content */
.content { flex: 1; overflow: hidden; }
.placeholder { text-align: center; color: var(--text-muted); font-size: 14px; padding: 60px; }
.placeholder.error { color: var(--danger); }

/* Three columns */
.three-col {
  display: grid;
  grid-template-columns: 220px 1fr 200px;
  height: 100%;
  overflow: hidden;
}

/* Column shared */
.col-timeline, .col-detail, .col-metrics {
  display: flex; flex-direction: column;
  border-right: 0.5px solid var(--border);
  overflow: hidden;
}
.col-metrics { border-right: none; }
.col-header {
  font-size: 11px; font-weight: 500; color: var(--text-muted);
  text-transform: uppercase; letter-spacing: 0.06em;
  padding: 12px 16px 8px;
  border-bottom: 0.5px solid var(--border);
  flex-shrink: 0;
}

/* Timeline */
.timeline-list { flex: 1; overflow-y: auto; padding: 8px; }
.timeline-item {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; border-radius: var(--radius-sm);
  cursor: pointer; transition: background 0.12s;
  margin-bottom: 2px;
}
.timeline-item:hover { background: var(--surface-muted); }
.timeline-item.active { background: var(--primary-light); }
.tl-dot {
  width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0;
  background: #e5e7eb;
}
.timeline-item.success .tl-dot { background: var(--success); }
.timeline-item.running .tl-dot { background: var(--glass-bg); box-shadow: 0 0 0 3px var(--primary-muted); }
.timeline-item.failed .tl-dot { background: var(--danger); }
.tl-info { display: flex; flex-direction: column; min-width: 0; }
.tl-label { font-size: 12px; color: var(--text-secondary); truncate: true; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tl-duration { font-size: 10px; color: var(--text-muted); font-family: var(--font-mono); }
.empty-hint { text-align: center; color: var(--text-muted); font-size: 12px; padding: 20px; }

/* Detail */
.detail-body { flex: 1; overflow-y: auto; padding: 16px; }
.detail-section { margin-bottom: 14px; }
.detail-label { font-size: 10px; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.06em; margin-bottom: 3px; }
.detail-value { font-size: 13px; color: var(--text-secondary); }
.detail-meta {
  background: var(--surface-muted); border: 0.5px solid var(--border);
  border-radius: var(--radius-sm); padding: 10px; font-size: 11px;
  font-family: var(--font-mono); color: var(--text-secondary);
  overflow-x: auto; white-space: pre-wrap; margin: 0;
}
.detail-empty {
  flex: 1; display: flex; align-items: center; justify-content: center;
  color: var(--text-muted); font-size: 13px;
}
.status-tag {
  font-size: 11px; padding: 2px 7px; border-radius: 4px; font-weight: 500;
}
.status-tag.success { background: var(--success-light); color: var(--success); }
.status-tag.running { background: var(--primary-light); color: var(--glass-bg); }
.status-tag.failed { background: var(--danger-light); color: var(--danger); }

/* Metrics */
.metrics-body { flex: 1; overflow-y: auto; padding: 12px 16px; }
.metric-row {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 0;
  border-bottom: 0.5px solid var(--border);
}
.metric-row:last-child { border-bottom: none; }
.metric-label { font-size: 12px; color: var(--text-muted); }
.metric-value { font-size: 12px; color: var(--text); font-weight: 500; }

@media (max-width: 768px) {
  .three-col { grid-template-columns: 1fr; }
  .col-timeline, .col-metrics { display: none; }
}
</style>

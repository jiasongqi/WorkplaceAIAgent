<template>
  <div class="trace-timeline">
    <!-- Header: trace status + duration -->
    <div class="trace-header">
      <span class="trace-status" :class="statusClass">
        <span v-if="trace.status === 'RUNNING'" class="spin">⟳</span>
        <span v-else-if="trace.status === 'SUCCESS'">✓</span>
        <span v-else-if="trace.status === 'FAILED'">✗</span>
        <span v-else-if="trace.status === 'CANCELLED'">⊘</span>
        {{ statusLabel }}
      </span>
      <span class="trace-duration">{{ durationText }}</span>
    </div>

    <!-- Steps timeline -->
    <div class="steps-container">
      <div
        v-for="span in sortedSpans"
        :key="span.sequence"
        class="step"
        :class="stepClass(span)"
      >
        <div class="step-connector">
          <div class="step-dot">
            <span v-if="span.status === 'SUCCESS'">✓</span>
            <span v-else-if="span.status === 'RUNNING'" class="spin">⟳</span>
            <span v-else-if="span.status === 'FAILED'">✗</span>
            <span v-else-if="span.status === 'SKIPPED'">⊘</span>
          </div>
          <div v-if="span.sequence < sortedSpans.length - 1" class="step-line"></div>
        </div>

        <div class="step-body">
          <div class="step-header">
            <span class="step-type">{{ span.stepTypeDisplayName || span.stepType }}</span>
            <span class="step-label">{{ span.label }}</span>
            <span class="step-time">{{ spanDuration(span) }}</span>
          </div>

          <!-- RUNNING placeholder -->
          <div v-if="span.status === 'RUNNING'" class="step-running">
            <span class="running-dot"></span> 进行中...
          </div>

          <!-- Error message -->
          <div v-if="span.status === 'FAILED' && span.errorMessage" class="step-error">
            {{ span.errorMessage }}
          </div>

          <!-- Metadata (collapsible) -->
          <div v-if="span.metadata && Object.keys(span.metadata).length > 0" class="step-metadata">
            <button class="meta-toggle" @click="toggleMeta(span.sequence)">
              {{ expandedMeta.has(span.sequence) ? '▼' : '▶' }} metadata
            </button>
            <div v-if="expandedMeta.has(span.sequence)" class="meta-content">
              <div v-for="(val, key) in span.metadata" :key="key" class="meta-row">
                <span class="meta-key">{{ key }}</span>
                <span class="meta-val">{{ val }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Empty state -->
      <div v-if="sortedSpans.length === 0" class="empty-spans">
        暂无执行步骤
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive } from 'vue'

const props = defineProps({
  trace: {
    type: Object,
    required: true
  }
})

const isRealtime = computed(() => props.trace?.status === 'RUNNING')

const sortedSpans = computed(() => {
  if (!props.trace?.spans) return []
  return [...props.trace.spans].sort((a, b) => a.sequence - b.sequence)
})

const statusClass = computed(() => {
  const s = props.trace?.status
  if (s === 'RUNNING') return 'running'
  if (s === 'SUCCESS') return 'success'
  if (s === 'FAILED') return 'failed'
  if (s === 'CANCELLED') return 'cancelled'
  return ''
})

const statusLabel = computed(() => {
  const s = props.trace?.status
  if (s === 'RUNNING') return '执行中'
  if (s === 'SUCCESS') return '成功'
  if (s === 'FAILED') return '失败'
  if (s === 'CANCELLED') return '已取消'
  return s || '未知'
})

const durationText = computed(() => {
  const t = props.trace
  if (!t?.startTime) return ''
  const start = new Date(t.startTime).getTime()
  const end = t.endTime ? new Date(t.endTime).getTime() : Date.now()
  const ms = end - start
  if (ms < 1000) return ms + 'ms'
  return (ms / 1000).toFixed(1) + 's'
})

const stepClass = (span) => {
  if (span.status === 'RUNNING') return 'running'
  if (span.status === 'FAILED') return 'failed'
  if (span.status === 'SKIPPED') return 'skipped'
  return 'success'
}

const spanDuration = (span) => {
  if (!span.startTime) return ''
  const start = new Date(span.startTime).getTime()
  const end = span.endTime ? new Date(span.endTime).getTime() : Date.now()
  const ms = end - start
  if (ms < 1000) return ms + 'ms'
  return (ms / 1000).toFixed(1) + 's'
}

const expandedMeta = reactive(new Set())
const toggleMeta = (seq) => {
  if (expandedMeta.has(seq)) {
    expandedMeta.delete(seq)
  } else {
    expandedMeta.add(seq)
  }
}
</script>

<style scoped>
.trace-timeline {
  background: var(--layer1);
  border: 1px solid var(--glass-border);
  border-radius: 12px;
  padding: 16px;
  color: var(--t1);
}

.trace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--glass-border);
}

.trace-status {
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 12px;
}
.trace-status.running { background: var(--gold-soft); color: var(--gold-text); }
.trace-status.success { background: var(--ok-bg); color: var(--ok); }
.trace-status.failed { background: var(--danger-bg); color: var(--danger); }
.trace-status.cancelled { background: var(--layer2); color: var(--t3); }

.trace-duration {
  font-size: 13px;
  color: var(--t3);
  font-family: var(--mono, monospace);
}

.steps-container {
  display: flex;
  flex-direction: column;
}

.step {
  display: flex;
  gap: 12px;
  min-height: 48px;
}

.step-connector {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 24px;
  flex-shrink: 0;
}

.step-dot {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: bold;
  flex-shrink: 0;
  background: var(--gold-soft);
  color: var(--gold-text);
}
.step.success .step-dot { background: var(--ok-bg); color: var(--ok); }
.step.running .step-dot { background: var(--gold-soft); color: var(--gold-text); }
.step.failed .step-dot { background: var(--danger-bg); color: var(--danger); }
.step.skipped .step-dot { background: var(--layer2); color: var(--t3); }

.step-line {
  width: 2px;
  flex: 1;
  background: var(--glass-border);
  margin: 4px 0;
}

.step-body {
  flex: 1;
  padding-bottom: 16px;
}

.step-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.step-type {
  font-size: 12px;
  background: var(--gold-soft);
  color: var(--gold-text);
  padding: 2px 8px;
  border-radius: 6px;
  font-weight: 500;
}

.step-label {
  font-size: 13px;
  color: var(--t2);
}

.step-time {
  font-size: 11px;
  color: var(--t4);
  font-family: var(--mono, monospace);
  margin-left: auto;
}

.step-running {
  font-size: 12px;
  color: var(--gold-text);
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.running-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--gold);
  animation: pulse 1.2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.step-error {
  font-size: 12px;
  color: var(--danger);
  margin-top: 4px;
  padding: 6px 10px;
  background: var(--danger-bg);
  border-radius: 6px;
  border-left: 3px solid var(--danger);
}

.step-metadata {
  margin-top: 6px;
}

.meta-toggle {
  font-size: 11px;
  color: var(--t3);
  background: none;
  border: none;
  cursor: pointer;
  padding: 2px 0;
}
.meta-toggle:hover { color: var(--t1); }

.meta-content {
  margin-top: 4px;
  padding: 6px 10px;
  background: var(--layer2);
  border-radius: 6px;
  border: 1px solid var(--glass-border);
}

.meta-row {
  display: flex;
  gap: 8px;
  font-size: 11px;
  padding: 2px 0;
}
.meta-key { color: var(--t3); min-width: 80px; }
.meta-val { color: var(--t2); font-family: monospace; word-break: break-all; }

.empty-spans {
  text-align: center;
  color: var(--t4);
  font-size: 13px;
  padding: 24px 0;
}

.spin {
  display: inline-block;
  animation: spin 1s linear infinite;
}
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
</style>

<template>
  <div class="usage-layout">
    <div class="header">
      <div class="back-button" @click="goBack">← 返回</div>
      <h1 class="title">📊 用量统计</h1>
      <div class="total-events">{{ stats.totalEvents || 0 }} 次操作</div>
    </div>

    <div class="content">
      <div v-if="loading" class="placeholder">加载中...</div>
      <div v-else-if="!stats.totalEvents" class="placeholder">暂无使用记录。开始对话后会自动记录。</div>

      <div v-else class="dashboard">
        <!-- Summary cards -->
        <div class="summary-row">
          <div class="summary-card">
            <div class="card-value">{{ stats.totalEvents }}</div>
            <div class="card-label">总操作数</div>
          </div>
          <div class="summary-card">
            <div class="card-value">{{ formatDuration(stats.totalDurationMs) }}</div>
            <div class="card-label">总耗时</div>
          </div>
          <div class="summary-card">
            <div class="card-value">{{ Object.keys(stats.eventsByAgent || {}).length }}</div>
            <div class="card-label">使用 Agent 数</div>
          </div>
        </div>

        <!-- Daily trend -->
        <div class="section">
          <h2 class="section-title">📈 近 7 天趋势</h2>
          <div class="daily-chart">
            <div v-for="(count, date) in stats.dailyCounts" :key="date" class="daily-bar-wrapper">
              <div class="daily-bar" :style="{ height: barHeight(count) + 'px' }">
                <span v-if="count > 0" class="bar-count">{{ count }}</span>
              </div>
              <div class="daily-label">{{ formatDay(date) }}</div>
            </div>
          </div>
        </div>

        <!-- By type -->
        <div class="section">
          <h2 class="section-title">📋 按类型分布</h2>
          <div class="type-list">
            <div v-for="(count, name) in stats.eventsByType" :key="name" class="type-row">
              <span class="type-name">{{ name }}</span>
              <div class="type-bar-track">
                <div class="type-bar-fill" :style="{ width: typeBarWidth(count) + '%' }"></div>
              </div>
              <span class="type-count">{{ count }}</span>
            </div>
          </div>
        </div>

        <!-- By agent -->
        <div v-if="stats.eventsByAgent && Object.keys(stats.eventsByAgent).length > 0" class="section">
          <h2 class="section-title">🤖 按 Agent 分布</h2>
          <div class="type-list">
            <div v-for="(count, name) in stats.eventsByAgent" :key="name" class="type-row">
              <span class="type-name">{{ name }}</span>
              <div class="type-bar-track">
                <div class="type-bar-fill agent-bar" :style="{ width: agentBarWidth(count) + '%' }"></div>
              </div>
              <span class="type-count">{{ count }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getUsageStats } from '../api'

const router = useRouter()
const stats = ref({})
const loading = ref(true)

const goBack = () => router.push('/')

const loadStats = async () => {
  loading.value = true
  try {
    const res = await getUsageStats()
    stats.value = res.data?.data || {}
  } catch (e) {
    console.error('加载用量统计失败', e)
  } finally {
    loading.value = false
  }
}

const formatDuration = (ms) => {
  if (!ms) return '0s'
  if (ms < 1000) return ms + 'ms'
  if (ms < 60000) return (ms / 1000).toFixed(1) + 's'
  return (ms / 60000).toFixed(1) + 'min'
}

const formatDay = (dateStr) => {
  const d = new Date(dateStr)
  return (d.getMonth() + 1) + '/' + d.getDate()
}

const maxDaily = () => {
  const counts = Object.values(stats.value.dailyCounts || {})
  return Math.max(1, ...counts)
}

const barHeight = (count) => {
  return Math.max(4, (count / maxDaily()) * 120)
}

const maxType = () => {
  const counts = Object.values(stats.value.eventsByType || {})
  return Math.max(1, ...counts)
}

const typeBarWidth = (count) => {
  return (count / maxType()) * 100
}

const maxAgent = () => {
  const counts = Object.values(stats.value.eventsByAgent || {})
  return Math.max(1, ...counts)
}

const agentBarWidth = (count) => {
  return (count / maxAgent()) * 100
}

onMounted(loadStats)
</script>

<style scoped>
.usage-layout {
  display: flex; flex-direction: column; height: 100vh;
  background: #f0f2f5; overflow: hidden;
}
.header {
  display: grid; grid-template-columns: 1fr auto 1fr; align-items: center;
  padding: 14px 24px; background: var(--glass-bg); backdrop-filter: blur(var(--glass-blur)); -webkit-backdrop-filter: blur(var(--glass-blur)); color: var(--text);
  border: 0.5px solid var(--border);
}
.back-button { cursor: pointer; font-size: 15px; opacity: 0.85; }
.back-button:hover { opacity: 1; }
.title { font-size: 18px; font-weight: bold; margin: 0; text-align: center; }
.total-events { justify-self: end; font-size: 13px; opacity: 0.8; }

.content { flex: 1; overflow-y: auto; padding: 20px; max-width: 800px; margin: 0 auto; width: 100%; }
.placeholder { text-align: center; color: var(--text-muted); font-size: 14px; padding: 60px 16px; }

.dashboard { display: flex; flex-direction: column; gap: 20px; }

.summary-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.summary-card {
  background: var(--surface); border-radius: var(--radius); padding: 20px; text-align: center;
  border: 0.5px solid var(--border); box-shadow: none;
}
.card-value { font-size: 28px; font-weight: 700; color: #7c3aed; }
.card-label { font-size: 13px; color: var(--text-muted); margin-top: 4px; }

.section { background: var(--surface); border-radius: var(--radius); padding: 20px; border: 0.5px solid var(--border); box-shadow: none; }
.section-title { font-size: 15px; font-weight: 600; margin: 0 0 16px; }

.daily-chart { display: flex; align-items: flex-end; gap: 8px; height: 160px; padding-top: 20px; }
.daily-bar-wrapper { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px; }
.daily-bar {
  width: 100%; background: var(--primary); border-radius: 6px 6px 0 0;
  min-height: 4px; position: relative; transition: height 0.5s ease;
}
.bar-count {
  position: absolute; top: -18px; left: 50%; transform: translateX(-50%);
  font-size: 11px; color: #7c3aed; font-weight: 600;
}
.daily-label { font-size: 11px; color: #9ca3af; }

.type-list { display: flex; flex-direction: column; gap: 10px; }
.type-row { display: flex; align-items: center; gap: 10px; }
.type-name { font-size: 13px; color: #374151; min-width: 100px; }
.type-bar-track { flex: 1; height: 8px; background: #f3f4f6; border-radius: 4px; overflow: hidden; }
.type-bar-fill { height: 100%; background: var(--glass-bg); backdrop-filter: blur(var(--glass-blur)); -webkit-backdrop-filter: blur(var(--glass-blur)); border-radius: 4px; transition: width 0.5s ease; }
.agent-bar { background: #059669; }
.type-count { font-size: 13px; color: var(--text-muted); min-width: 30px; text-align: right; }
</style>

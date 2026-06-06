<template>
  <div class="workbench">
    <!-- Hero -->
    <section class="hero">
      <h1 class="hero-title">你的 AI 职业教练</h1>
      <p class="hero-sub">越用越懂你，每次对话都有产出</p>
    </section>

    <!-- Input -->
    <div class="input-box">
      <input
        v-model="taskInput"
        class="task-input"
        placeholder="描述你的职业困惑，例如：帮我优化简历..."
        @keydown.enter="startTask"
      />
      <button class="send-btn" @click="startTask" :disabled="!taskInput.trim()">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
          <path d="M3 8H13M10 5L13 8L10 11" stroke="white" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
    </div>

    <!-- Capabilities -->
    <div class="section-label">我能帮你做什么</div>
    <div class="cap-grid">
      <div
        v-for="cap in capabilities"
        :key="cap.name"
        class="cap-card"
        :style="{ '--cap-color': cap.color }"
        @click="$router.push(cap.route)"
      >
        <div class="cap-icon" :style="{ background: cap.bg }">{{ cap.icon }}</div>
        <div class="cap-name">{{ cap.name }}</div>
        <div class="cap-desc">{{ cap.desc }}</div>
      </div>
    </div>

    <!-- Recent deliverables -->
    <div class="section-label">最近成果</div>
    <div class="deliverables">
      <div v-if="recentDeliverables.length === 0" class="empty-state">暂无成果，开始对话后自动生成</div>
      <div
        v-for="del in recentDeliverables"
        :key="del.id"
        class="deliverable-item"
      >
        <div class="del-icon" :style="{ background: del.iconBg }">{{ del.iconEmoji }}</div>
        <div class="del-info">
          <div class="del-name">{{ del.name }}</div>
          <div class="del-meta">{{ del.time }} · {{ del.type }}</div>
        </div>
        <div class="del-status" :class="del.statusClass">{{ del.statusText }}</div>
      </div>
    </div>

    <!-- Stats -->
    <div class="section-label">我的数据</div>
    <div class="stats-row">
      <div class="stat-item">
        <div class="stat-num">{{ stats.sessions }}</div>
        <div class="stat-label">对话次数</div>
      </div>
      <div class="stat-item">
        <div class="stat-num">{{ stats.artifacts }}</div>
        <div class="stat-label">交付物</div>
      </div>
      <div class="stat-item">
        <div class="stat-num">{{ stats.documents }}</div>
        <div class="stat-label">知识文档</div>
      </div>
    </div>

    <!-- Profile -->
    <div class="section-label">你的画像</div>
    <div class="profile-card">
      <div v-if="!profile" class="profile-empty">多与 AI 对话后，系统会自动构建你的专属画像</div>
      <template v-else>
        <div class="profile-header">
          <div class="profile-avatar">{{ userInitial }}</div>
          <div>
            <div class="profile-name">{{ username }}</div>
            <div class="profile-meta">已对话 {{ stats.sessions }} 次</div>
          </div>
        </div>
        <div class="profile-tags">
          <span v-for="tag in profileTags" :key="tag" class="profile-tag">{{ tag }}</span>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getTracesByUser, listArtifacts, listFavorites } from '../api'

const router = useRouter()
const username = ref(localStorage.getItem('username') || '用户')
const userInitial = computed(() => (username.value || 'U').charAt(0).toUpperCase())
const taskInput = ref('')

const capabilities = [
  { name: '简历优化', desc: '分析简历问题，给出修改建议', icon: '📄', color: '#6366f1', bg: '#ede9fe', route: '/chat/career' },
  { name: '谈薪策略', desc: '薪资分析，谈判话术', icon: '💰', color: '#10b981', bg: '#d1fae5', route: '/chat/career' },
  { name: '离职规划', desc: '离职时机，交接方案', icon: '🚪', color: '#f59e0b', bg: '#fef3c7', route: '/chat/career' },
  { name: '行业调研', desc: '行业趋势，岗位分析', icon: '📊', color: '#3b82f6', bg: '#dbeafe', route: '/chat/super' },
  { name: '面试准备', desc: '模拟面试，高频问题', icon: '🎯', color: '#ec4899', bg: '#fce7f3', route: '/chat/career' },
  { name: '预约咨询', desc: '连接人类职业顾问', icon: '📅', color: '#8b5cf6', bg: '#ede9fe', route: '/chat/career' },
]

const recentDeliverables = ref([])
const stats = ref({ sessions: 0, artifacts: 0, documents: 0 })
const profile = ref(null)
const profileTags = ref([])

const startTask = () => {
  if (!taskInput.value.trim()) return
  router.push('/chat/career')
}

const formatTime = (val) => {
  if (!val) return ''
  const d = new Date(val)
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  const diffMs = now - d
  if (diffMs < 60000) return '刚刚'
  if (diffMs < 3600000) return Math.floor(diffMs / 60000) + ' 分钟前'
  if (diffMs < 86400000) return Math.floor(diffMs / 3600000) + ' 小时前'
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

onMounted(async () => {
  const userId = localStorage.getItem('userId')
  if (!userId) return

  try {
    const [traceRes, artRes, favRes] = await Promise.all([
      getTracesByUser(userId, 1, 5).catch(() => ({ data: { data: [] } })),
      listArtifacts({}).catch(() => ({ data: { data: [] } })),
      listFavorites().catch(() => ({ data: { data: [] } })),
    ])

    const traces = traceRes.data?.data || []
    stats.value.sessions = traces.length
    stats.value.artifacts = (artRes.data?.data || []).length
    stats.value.favorites = (favRes.data?.data || []).length

    // Build recent deliverables from traces
    recentDeliverables.value = traces.slice(0, 3).map((t, i) => ({
      id: t.traceId,
      name: t.spans?.find(s => s.stepType === 'SUB_AGENT_EXECUTION')?.label || '任务 ' + (i + 1),
      time: formatTime(t.startTime),
      type: '对话成果',
      iconEmoji: '📄',
      iconBg: '#ede9fe',
      statusClass: t.status === 'SUCCESS' ? 'done' : t.status === 'RUNNING' ? 'running' : 'error',
      statusText: t.status === 'SUCCESS' ? '已完成' : t.status === 'RUNNING' ? '生成中' : '失败',
    }))

    // Load profile
    try {
      const { getMyProfile } = await import('../api')
      const profileRes = await getMyProfile()
      const p = profileRes.data?.data
      if (p) {
        profile.value = p
        const tags = []
        if (p.tonePreference) tags.push('沟通风格：' + p.tonePreference)
        if (p.focusAreas?.length) tags.push('关注领域：' + p.focusAreas[0])
        if (p.coreNeeds?.length) tags.push('核心诉求：' + p.coreNeeds[0])
        profileTags.value = tags
      }
    } catch {}
  } catch (e) {
    console.error('Failed to load workbench data', e)
  }
})
</script>

<style scoped>
.workbench {
  padding: 40px 20px 80px;
  max-width: 720px;
  margin: 0 auto;
}

/* Hero */
.hero { text-align: center; margin-bottom: 40px; }
.hero-title {
  font-size: 32px; font-weight: 700; letter-spacing: -1px;
  margin-bottom: 8px;
  background: linear-gradient(135deg, var(--text), var(--primary));
  -webkit-background-clip: text; -webkit-text-fill-color: transparent;
}
.hero-sub { font-size: 15px; color: var(--text-muted); }

/* Input */
.input-box {
  background: var(--glass-bg);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 16px 20px;
  display: flex; align-items: center; gap: 12px;
  margin-bottom: 48px;
  transition: all 0.2s;
  box-shadow: 0 2px 12px rgba(0,0,0,0.04);
}
.input-box:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--primary-muted), 0 2px 12px rgba(0,0,0,0.04);
}
.task-input {
  flex: 1; border: none; outline: none;
  font-size: 15px; color: var(--text); background: transparent;
}
.task-input::placeholder { color: var(--text-muted); }
.send-btn {
  width: 36px; height: 36px; border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff; border: none; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: all 0.15s; flex-shrink: 0;
}
.send-btn:hover { transform: scale(1.05); box-shadow: 0 4px 12px rgba(99,102,241,0.3); }
.send-btn:disabled { opacity: 0.4; cursor: not-allowed; transform: none; }

/* Section label */
.section-label {
  font-size: 11px; font-weight: 600; color: var(--text-muted);
  text-transform: uppercase; letter-spacing: 0.1em;
  margin-bottom: 14px;
}

/* Capabilities */
.cap-grid {
  display: grid; grid-template-columns: repeat(3, 1fr);
  gap: 10px; margin-bottom: 48px;
}
.cap-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 16px; cursor: pointer;
  transition: all 0.2s; position: relative; overflow: hidden;
}
.cap-card::before {
  content: ''; position: absolute; top: 0; left: 0; right: 0;
  height: 3px; background: var(--cap-color, var(--primary));
  opacity: 0; transition: opacity 0.2s;
}
.cap-card:hover {
  border-color: var(--primary);
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.06);
}
.cap-card:hover::before { opacity: 1; }
.cap-icon {
  width: 36px; height: 36px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; margin-bottom: 10px;
}
.cap-name { font-size: 13px; font-weight: 600; margin-bottom: 3px; }
.cap-desc { font-size: 11px; color: var(--text-muted); line-height: 1.4; }

/* Deliverables */
.deliverables { margin-bottom: 48px; }
.empty-state {
  text-align: center; color: var(--text-muted); font-size: 13px;
  padding: 32px; background: var(--surface); border: 1px solid var(--border);
  border-radius: var(--radius-sm);
}
.deliverable-item {
  background: var(--surface); border: 1px solid var(--border);
  border-radius: var(--radius-sm); padding: 14px 16px;
  display: flex; align-items: center; gap: 12px;
  margin-bottom: 8px; cursor: pointer; transition: all 0.15s;
}
.deliverable-item:hover { border-color: var(--primary); transform: translateX(4px); }
.del-icon {
  width: 32px; height: 32px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; flex-shrink: 0;
}
.del-info { flex: 1; min-width: 0; }
.del-name { font-size: 13px; font-weight: 500; margin-bottom: 2px; }
.del-meta { font-size: 11px; color: var(--text-muted); }
.del-status {
  font-size: 10px; padding: 2px 8px; border-radius: 6px;
  font-weight: 500; flex-shrink: 0;
}
.del-status.done { background: var(--success-light); color: #059669; }
.del-status.running { background: var(--primary-light); color: var(--primary); }
.del-status.error { background: var(--danger-light); color: var(--danger); }

/* Stats */
.stats-row {
  display: grid; grid-template-columns: repeat(3, 1fr);
  gap: 10px; margin-bottom: 48px;
}
.stat-item {
  background: var(--surface); border: 1px solid var(--border);
  border-radius: var(--radius-sm); padding: 16px; text-align: center;
}
.stat-num { font-size: 24px; font-weight: 700; color: var(--primary); }
.stat-label { font-size: 11px; color: var(--text-muted); margin-top: 2px; }

/* Profile */
.profile-card {
  background: var(--surface); border: 1px solid var(--border);
  border-radius: var(--radius); padding: 20px;
}
.profile-empty { text-align: center; color: var(--text-muted); font-size: 13px; padding: 16px; }
.profile-header { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.profile-avatar {
  width: 40px; height: 40px; border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #ec4899);
  display: flex; align-items: center; justify-content: center;
  font-size: 16px; color: #fff; font-weight: 600;
}
.profile-name { font-size: 15px; font-weight: 600; }
.profile-meta { font-size: 12px; color: var(--text-muted); }
.profile-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.profile-tag {
  font-size: 11px; padding: 4px 10px; border-radius: 6px;
  background: var(--primary-light); color: var(--primary); font-weight: 500;
}

@media (max-width: 640px) {
  .cap-grid { grid-template-columns: repeat(2, 1fr); }
  .hero-title { font-size: 24px; }
  .workbench { padding: 24px 16px 60px; }
}
</style>

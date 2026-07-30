<template>
  <div class="home">
    <!-- Greeting -->
    <div class="greet-block">
      <span class="greet-wave">{{ theme === 'sage' ? '🌿' : '👋' }}</span>
      <h1 class="greet-title">{{ greeting }}</h1>
      <p class="greet-sub greet-sub--sage" v-if="theme === 'sage'">心里有光<br><span class="greet-tagline">不管你在烦恼什么，都可以先放一放。这里只有倾听，没有对错。</span></p>
      <p class="greet-sub" v-else>不管你在烦恼什么，都可以先放一放。<br>这里只有选择，没有对错。</p>
    </div>

    <!-- Hero Input -->
    <div class="hero-input">
      <div class="hero-input-wrap" :class="{ focused: inputFocused }">
        <textarea
          ref="heroTextarea"
          v-model="taskInput"
          :placeholder="currentPlaceholder"
          rows="1"
          @focus="inputFocused = true"
          @blur="inputFocused = false"
          @keydown.enter.exact.prevent="startTask"
          @input="autoResize"
        ></textarea>
        <div class="hero-input-foot">
          <span class="hero-hint">说你想说的，你说的话只有你和我知道</span>
          <button class="hero-submit" @click="startTask" :disabled="!taskInput.trim()">→</button>
        </div>
      </div>
    </div>

    <!-- Quick chips -->
    <div class="quick-chips">
      <span class="chip" v-for="chip in quickChips" :key="chip.text" @click="quickStart(chip.text)">
        <WpIcon class="chip-icon" :name="chip.icon" :size="14" />
        {{ chip.text }}
      </span>
    </div>

    <!-- Bento grid -->
    <div class="bento-grid">
      <div class="bento-card" v-for="card in bentoCards" :key="card.title" @click="quickStart(card.prompt)">
        <div class="icon-well">
          <WpIcon :name="card.icon" :size="22" />
        </div>
        <h3>{{ card.title }}</h3>
        <p>{{ card.desc }}</p>
        <span class="card-action">{{ card.action }} →</span>
      </div>
    </div>

    <!-- More capabilities -->
    <div class="more-caps" v-if="!capsExpanded">
      <button class="caps-toggle" @click="capsExpanded = true">还能帮你做更多 ↓</button>
    </div>
    <div class="caps-grid" v-if="capsExpanded">
      <span class="cap-tag" v-for="cap in moreCaps" :key="cap.text" @click="quickStart(cap.prompt)">
        <WpIcon class="cap-icon" :name="cap.icon" :size="14" />
        {{ cap.text }}
      </span>
    </div>

    <!-- Privacy trust -->
    <div class="privacy-strip">
      <WpIcon class="privacy-icon" name="lock" :size="14" />
      <span class="privacy-text">对话仅存储在本地，不会分享给任何第三方。你可以随时导出或删除。</span>
    </div>

    <!-- Recent conversations -->
    <div class="recent-row" v-if="recentSessions.length > 0">
      <span class="recent-label">继续聊</span>
      <div class="recent-scroll">
        <span
          class="recent-item"
          v-for="session in recentSessions"
          :key="session.chatId"
          @click="goToSession(session.chatId)"
        >{{ session.title }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { listSessions } from '../api'
import WpIcon from '../components/WpIcon.vue'
import { useTheme } from '../composables/useTheme'

const router = useRouter()
const { theme } = useTheme()
const taskInput = ref('')
const inputFocused = ref(false)
const heroTextarea = ref(null)
const recentSessions = ref([])
const capsExpanded = ref(false)
const placeholderIndex = ref(0)

const placeholders = [
  '说说你在烦恼什么，比如「最近老觉得不安，但又说不上来为什么」…',
  '比如「我该怎么跟领导谈涨薪」…',
  '比如「帮我准备下周的面试」…',
  '比如「同事总甩锅给我怎么办」…',
  '比如「想写一封离职信但不知道怎么措辞」…',
  '比如「收到两个offer不知道选哪个」…',
]

const currentPlaceholder = computed(() => placeholders[placeholderIndex.value])

let placeholderTimer = null
onMounted(async () => {
  // Rotate placeholder
  placeholderTimer = setInterval(() => {
    placeholderIndex.value = (placeholderIndex.value + 1) % placeholders.length
  }, 4000)
  
  try {
    const res = await listSessions()
    const sessions = res.data?.data || []
    recentSessions.value = sessions.slice(0, 5)
  } catch (e) {
    recentSessions.value = []
  }
})

onBeforeUnmount(() => { if (placeholderTimer) clearInterval(placeholderTimer) })

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const quickChips = [
  { icon: 'salary', text: '我不知道该不该提涨薪' },
  { icon: 'path', text: '想离职但怕后悔' },
  { icon: 'resume', text: '总觉得简历写不好' },
  { icon: 'manage', text: '同事关系让我很累' },
  { icon: 'interview', text: '面试前的慌张怎么克服' },
]

const bentoCards = [
  { icon: 'salary', title: '不知道怎么谈涨薪？', desc: '害怕谈崩、不知道要多少合适、老板说你表现不够好怎么回应', action: '聊聊这件事', prompt: '我想跟公司谈涨薪，但不知道怎么开口' },
  { icon: 'path', title: '该不该离职？', desc: '什么时候走最安全、竞业限制怎么绕、交接时怎么保护自己', action: '一起分析一下', prompt: '我在纠结要不要离职' },
  { icon: 'compass', title: '好像迷路了', desc: '不知道自己擅长什么、35岁以后怎么办、换行业怎么找突破口', action: '理一理方向', prompt: '我不确定自己的职业方向' },
]

const moreCaps = [
  { icon: 'interview', text: '面试模拟', prompt: '帮我模拟一次面试，我在准备后端开发岗位' },
  { icon: 'compare', text: 'Offer 对比', prompt: '我收到两个offer，帮我分析该选哪个' },
  { icon: 'resume', text: '简历诊断', prompt: '帮我看看简历有什么问题' },
  { icon: 'calendar', text: '预约咨询', prompt: '我想预约一次职场咨询' },
  { icon: 'target', text: '绩效复盘', prompt: '帮我复盘这个季度的绩效表现' },
  { icon: 'manage', text: '向上管理', prompt: '怎么跟领导汇报工作成果' },
]

const autoResize = () => {
  const ta = heroTextarea.value
  if (!ta) return
  ta.style.height = 'auto'
  ta.style.height = Math.min(ta.scrollHeight, 200) + 'px'
}

const startTask = () => {
  if (!taskInput.value.trim()) return
  router.push({ path: '/chat/career', query: { msg: taskInput.value.trim() } })
}

const quickStart = (text) => {
  router.push({ path: '/chat/career', query: { msg: text } })
}

const goToSession = (chatId) => {
  router.push({ path: '/chat/career', query: { chatId } })
}
</script>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  align-items: center;
  /* 内容过高时不可用 center，否则顶部会被裁切且无法上滚 */
  justify-content: flex-start;
  justify-content: safe center;
  padding: 48px 28px;
  gap: 36px;
  height: 100%;
  overflow-y: auto;
}

/* Greeting */
.greet-block {
  text-align: center;
  max-width: 460px;
  animation: rise 0.8s var(--ease) both;
}

.greet-wave {
  display: inline-block;
  font-size: 28px;
  animation: wiggle 2.8s ease-in-out infinite;
  transform-origin: 70% 70%;
}

.greet-title {
  font-size: 40px;
  font-weight: 650;
  letter-spacing: -0.8px;
  line-height: 1.2;
  background: var(--greet-grad);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin-top: 8px;
  animation: rise 0.8s 0.12s var(--ease) both;
}

.greet-sub {
  margin-top: 14px;
  font-size: 15px;
  color: var(--t3);
  font-weight: 400;
  line-height: 1.7;
  animation: rise 0.8s 0.2s var(--ease) both;
}

.greet-sub--sage {
  color: var(--gold-text);
  font-weight: 500;
  font-size: 16px;
}

.greet-tagline {
  display: inline-block;
  margin-top: 6px;
  font-size: 13px;
  color: var(--t3);
  font-weight: 400;
}

/* Hero Input */
.hero-input {
  width: 100%;
  max-width: 620px;
  animation: rise 0.8s 0.35s var(--ease) both;
}

.hero-input-wrap {
  background: var(--layer1);
  border: 1px solid var(--glass-border);
  border-radius: var(--r-lg);
  padding: 6px;
  transition: all 0.4s var(--ease);
  box-shadow: var(--shadow-card);
}

.hero-input-wrap.focused {
  border-color: var(--gold-border);
  box-shadow: 0 0 0 4px var(--gold-dim), 0 0 36px var(--gold-dim);
}

.hero-input-wrap textarea {
  width: 100%;
  border: none;
  background: transparent;
  color: var(--t1);
  font-family: var(--font);
  font-size: 16px;
  line-height: 1.7;
  padding: 20px 24px 12px;
  resize: none;
  outline: none;
  min-height: 60px;
  max-height: 200px;
}

.hero-input-wrap textarea::placeholder {
  color: var(--t4);
}

.hero-input-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 14px 12px;
}

.hero-hint {
  font-size: 11px;
  color: var(--t4);
}

.hero-submit {
  width: 42px;
  height: 42px;
  border-radius: var(--r-md);
  border: none;
  background: var(--gold-grad);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
  font-weight: 700;
  transition: all 0.25s var(--spring);
  box-shadow: 0 4px 18px var(--gold-glow);
}

.hero-submit:hover {
  transform: scale(1.06);
  box-shadow: 0 8px 28px var(--gold-glow-strong);
}

.hero-submit:active {
  transform: scale(0.95);
}

.hero-submit:disabled {
  opacity: 0.3;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
  background: var(--layer3);
  color: var(--t4);
}

/* Quick chips */
.quick-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  max-width: 620px;
  animation: rise 0.8s 0.45s var(--ease) both;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: var(--r-full);
  background: var(--glass);
  border: 1px solid var(--glass-border);
  color: var(--t2);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.25s var(--ease);
  backdrop-filter: blur(12px);
  user-select: none;
  white-space: nowrap;
}

.chip:hover {
  background: var(--glass-hover);
  border-color: var(--chip-hover-border);
  color: var(--t1);
  transform: translateY(-1px);
}

.chip-icon {
  color: var(--gold-text);
  opacity: 0.85;
}

/* Bento grid */
.bento-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 14px;
  max-width: 720px;
  width: 100%;
  animation: rise 0.8s 0.55s var(--ease) both;
}

.bento-card {
  position: relative;
  overflow: hidden;
  background: var(--layer1);
  border: 1px solid var(--glass-border);
  border-radius: var(--r-lg);
  padding: 22px;
  cursor: pointer;
  transition: all 0.4s var(--ease);
  box-shadow: var(--shadow-card);
}

.bento-card::after {
  content: '';
  position: absolute;
  inset: 0;
  opacity: 0;
  background: radial-gradient(ellipse at top left, var(--gold-soft), transparent 70%);
  transition: opacity 0.4s var(--ease);
}

.bento-card:hover::after { opacity: 1; }

.bento-card:hover {
  border-color: var(--card-hover-border);
  transform: translateY(-3px);
  box-shadow: var(--card-hover-shadow);
}

.icon-well {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: var(--gold-soft);
  border: 1px solid var(--gold-border-soft);
  margin-bottom: 14px;
  color: var(--gold-text);
}

.bento-card h3 {
  font-size: 14px;
  font-weight: 600;
  color: var(--t1);
  margin-bottom: 6px;
  letter-spacing: -0.2px;
}

.bento-card p {
  font-size: 12px;
  color: var(--t3);
  line-height: 1.55;
}

.card-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 16px;
  font-size: 11px;
  color: var(--gold-text);
  opacity: 0.7;
  transition: opacity 0.2s;
}

.bento-card:hover .card-action { opacity: 1; }

/* Recent row */
.recent-row {
  display: flex;
  align-items: center;
  gap: 14px;
  max-width: 720px;
  width: 100%;
  animation: rise 0.8s 0.65s var(--ease) both;
}

.recent-label {
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 1.2px;
  color: var(--t4);
  font-weight: 600;
  flex-shrink: 0;
}

.recent-scroll {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  flex: 1;
  padding-bottom: 2px;
}

.recent-scroll::-webkit-scrollbar { height: 0; }

.recent-item {
  flex-shrink: 0;
  padding: 8px 16px;
  border-radius: var(--r-full);
  background: var(--glass);
  border: 1px solid var(--glass-border);
  color: var(--t2);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s var(--ease);
  white-space: nowrap;
}

.recent-item:hover {
  background: var(--glass-hover);
  color: var(--t1);
}

@media (max-width: 768px) {
  .bento-grid { grid-template-columns: 1fr; }
  .greet-title { font-size: 28px; }
  .hero-input { max-width: 100%; }
  .home { padding: 24px 16px; gap: 28px; }
}

/* More capabilities */
.more-caps {
  animation: rise 0.8s 0.6s var(--ease) both;
}
.caps-toggle {
  background: none; border: none; color: var(--t4); font-size: 12px;
  cursor: pointer; padding: 6px 14px; border-radius: var(--r-full);
  transition: all 0.2s var(--ease);
}
.caps-toggle:hover { color: var(--t2); background: var(--glass); }
.caps-grid {
  display: flex; flex-wrap: wrap; gap: 8px; justify-content: center;
  max-width: 620px; animation: rise 0.4s var(--ease) both;
}
.cap-tag {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 16px; border-radius: var(--r-full);
  background: var(--glass); border: 1px solid var(--glass-border);
  color: var(--t3); font-size: 12px; cursor: pointer;
  transition: all 0.25s var(--ease); user-select: none;
}
.cap-tag:hover { background: var(--glass-hover); color: var(--t1); transform: translateY(-1px); }
.cap-icon { color: var(--gold-text); opacity: 0.8; }

/* Privacy strip */
.privacy-strip {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 20px; border-radius: var(--r-full);
  background: rgba(52, 211, 153, 0.05); border: 1px solid rgba(52, 211, 153, 0.12);
  animation: rise 0.8s 0.75s var(--ease) both;
}
.privacy-icon { color: var(--ok); flex-shrink: 0; }
.privacy-text { font-size: 11px; color: var(--t3); line-height: 1.4; }
</style>

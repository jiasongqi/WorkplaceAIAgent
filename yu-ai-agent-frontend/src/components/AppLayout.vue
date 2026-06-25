<template>
  <div class="app-shell">
    <!-- Ambient background -->
    <div class="ambient">
      <div class="glow-top"></div>
      <div class="glow-bot"></div>
    </div>
    <div class="noise"></div>

    <!-- App content -->
    <div class="app-content">
      <!-- Topbar -->
      <header class="topbar">
        <div class="logo-area" @click="$router.push('/')">
          <div class="logo-orb"></div>
          <span class="logo-text">WorkPilot</span>
        </div>
        <nav class="topbar-nav">
          <router-link
            v-for="item in navItems"
            :key="item.path"
            :to="item.path"
            class="nav-link"
            :class="{ active: isActive(item.path) }"
          >
            <span class="nav-icon">{{ item.icon }}</span>
            <span>{{ item.label }}</span>
          </router-link>
        </nav>
        <div class="topbar-r">
          <button class="tb-btn" @click="$router.push('/knowledge')" title="知识库"><BookOpen :size="16" /></button>
          <button class="tb-btn" @click="$router.push('/artifacts')" title="交付物"><ClipboardList :size="16" /></button>
          <button class="tb-btn" @click="$router.push('/favorites')" title="收藏"><Star :size="16" /></button>
          <button class="tb-btn" @click="$router.push('/usage')" title="用量"><BarChart3 :size="16" /></button>
          <button class="tb-btn" @click="$router.push('/admin')" title="管理"><Settings :size="16" /></button>
          <div class="user-orb">{{ userInitial }}</div>
        </div>
      </header>

      <!-- Main view -->
      <div class="main-view">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { BookOpen, ClipboardList, Star, BarChart3, Settings } from 'lucide-vue-next'

const route = useRoute()
const username = ref(localStorage.getItem('username') || '用户')
const userInitial = computed(() => (username.value || 'U').charAt(0).toUpperCase())

const navItems = [
  { path: '/', icon: '◈', label: '首页' },
  { path: '/chat/career', icon: '💬', label: '职场顾问' },
  { path: '/chat/super', icon: '🤖', label: '超级智能体' },
]

const isActive = (path) => {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}
</script>

<style scoped>
.app-shell {
  position: relative;
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

.app-content {
  position: relative;
  z-index: 2;
  height: 100vh;
  display: flex;
  flex-direction: column;
}

/* Topbar */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  flex-shrink: 0;
  background: rgba(10,12,22,0.7);
  backdrop-filter: blur(var(--glass-blur));
  border-bottom: 1px solid var(--glass-border);
  height: var(--topbar-h);
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  user-select: none;
}

.logo-orb {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, rgba(245,158,11,0.85), rgba(217,119,6,0.4));
  box-shadow: 0 0 14px var(--gold-glow), inset 0 1px 0 rgba(255,255,255,0.2);
  animation: orb-pulse 5s ease-in-out infinite;
}

.logo-text {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.3px;
  color: var(--t1);
}

/* Navigation */
.topbar-nav {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: var(--r-sm);
  color: var(--t3);
  font-size: 13px;
  font-weight: 500;
  transition: all 0.2s var(--ease);
  text-decoration: none;
}

.nav-link:hover {
  background: var(--glass-hover);
  color: var(--t2);
}

.nav-link.active {
  background: var(--gold-soft);
  color: var(--gold-text);
}

.nav-icon {
  font-size: 14px;
}

/* Right side */
.topbar-r {
  display: flex;
  align-items: center;
  gap: 4px;
}

.tb-btn {
  width: 34px;
  height: 34px;
  border-radius: var(--r-sm);
  border: none;
  background: transparent;
  color: var(--t3);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  transition: all 0.2s var(--ease);
}

.tb-btn:hover {
  background: var(--glass-hover);
  color: var(--t2);
}

.user-orb {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(245,158,11,0.6), rgba(217,119,6,0.3));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--t1);
  margin-left: 8px;
}

/* Main */
.main-view {
  flex: 1;
  overflow: hidden;
  position: relative;
}

@media (max-width: 768px) {
  .topbar-nav { display: none; }
  .topbar { padding: 12px 16px; }
  .tb-btn:nth-child(n+3) { display: none; }
}
</style>

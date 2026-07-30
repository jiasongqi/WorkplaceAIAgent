<template>
  <div class="app-shell">
    <!-- Ambient background -->
    <div class="ambient">
      <div class="glow-top"></div>
      <div class="glow-bot"></div>
      <div class="leaf leaf-1"></div>
      <div class="leaf leaf-2"></div>
    </div>
    <div class="noise"></div>

    <!-- App content -->
    <div class="app-content" :class="{ 'auth-only': isAuthPage }">
      <!-- Topbar (hidden on login) -->
      <header v-if="!isAuthPage" class="topbar">
        <button class="hamburger-btn" @click="mobileNavOpen = !mobileNavOpen" aria-label="Toggle menu">
          <WpIcon name="menu" :size="18" />
        </button>
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
            <WpIcon class="nav-icon" :name="item.icon" :size="16" :active="isActive(item.path)" />
            <span>{{ item.label }}</span>
          </router-link>
        </nav>
        <div class="topbar-r">
          <button
            class="tb-btn theme-toggle"
            :title="theme === 'sage' ? '切换到暗色主题' : '切换到青荷绿主题'"
            aria-label="切换主题"
            @click="toggleTheme"
          >
            <!-- moon when sage (switch to dark) / leaf when dark (switch to sage) -->
            <svg v-if="theme === 'sage'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
            </svg>
            <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 2C8 6 5 9.5 5 13a7 7 0 0 0 14 0c0-3.5-3-7-7-11z"/>
            </svg>
          </button>
          <button
            v-for="tool in toolItems"
            :key="tool.path"
            class="tb-btn"
            :title="tool.label"
            @click="$router.push(tool.path)"
          >
            <WpIcon :name="tool.icon" :size="18" />
          </button>
          <div class="user-orb">{{ userInitial }}</div>
        </div>
      </header>

      <!-- Mobile nav drawer -->
      <Transition name="drawer">
        <div v-if="mobileNavOpen && !isAuthPage" class="mobile-drawer-overlay" @click.self="mobileNavOpen = false">
          <nav class="mobile-drawer">
            <div class="drawer-header">
              <div class="logo-area">
                <div class="logo-orb"></div>
                <span class="logo-text">WorkPilot</span>
              </div>
              <button class="drawer-close" @click="mobileNavOpen = false">×</button>
            </div>
            <router-link
              v-for="item in navItems"
              :key="item.path"
              :to="item.path"
              class="drawer-link"
              :class="{ active: isActive(item.path) }"
              @click="mobileNavOpen = false"
            >
              <WpIcon class="nav-icon" :name="item.icon" :size="18" :active="isActive(item.path)" />
              <span>{{ item.label }}</span>
            </router-link>
            <div class="drawer-divider"></div>
            <router-link
              v-for="tool in toolItems"
              :key="tool.path"
              class="drawer-link"
              :to="tool.path"
              @click="mobileNavOpen = false"
            >
              <WpIcon class="nav-icon" :name="tool.icon" :size="18" />
              <span>{{ tool.label }}</span>
            </router-link>
            <div class="drawer-divider"></div>
            <button class="drawer-link theme-drawer-btn" @click="toggleTheme">
              <span class="nav-icon" aria-hidden="true">{{ theme === 'sage' ? '🌙' : '🌿' }}</span>
              <span>{{ theme === 'sage' ? '切换暗色主题' : '切换青荷绿主题' }}</span>
            </button>
          </nav>
        </div>
      </Transition>

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
import WpIcon from './WpIcon.vue'
import { useTheme } from '../composables/useTheme'

const route = useRoute()
const { theme, toggleTheme } = useTheme()
const mobileNavOpen = ref(false)
const username = ref(localStorage.getItem('username') || '用户')
const userInitial = computed(() => (username.value || 'U').charAt(0).toUpperCase())
const isAuthPage = computed(() => route.path === '/login' || route.name === 'Login')

const navItems = [
  { path: '/', icon: 'home', label: '首页' },
  { path: '/chat/career', icon: 'career', label: '职场顾问' },
  { path: '/chat/super', icon: 'agent', label: '超级智能体' },
]

const toolItems = [
  { path: '/knowledge', icon: 'knowledge', label: '知识库' },
  { path: '/artifacts', icon: 'artifact', label: '交付物' },
  { path: '/favorites', icon: 'star', label: '收藏' },
  { path: '/usage', icon: 'usage', label: '用量' },
  { path: '/admin', icon: 'admin', label: '管理' },
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
  background: var(--topbar-bg);
  backdrop-filter: blur(var(--glass-blur));
  border-bottom: 1px solid var(--glass-border);
  height: var(--topbar-h);
  transition: background 0.35s var(--ease), border-color 0.35s var(--ease);
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
  background: var(--logo-orb);
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
  flex-shrink: 0;
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
  background: var(--user-orb);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--t1);
  margin-left: 8px;
}

.theme-toggle {
  color: var(--gold-text);
}
.theme-toggle:hover {
  color: var(--gold);
  background: var(--gold-soft);
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
  .hamburger-btn { display: flex; }
}

/* Hamburger button */
.hamburger-btn {
  display: none;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--r-sm);
  border: none;
  background: transparent;
  color: var(--t2);
  cursor: pointer;
  margin-right: 8px;
  transition: background 0.2s var(--ease);
}
.hamburger-btn:hover {
  background: var(--glass-hover);
}

/* Mobile drawer overlay */
.mobile-drawer-overlay {
  position: fixed;
  inset: 0;
  z-index: 200;
  background: var(--overlay-bg);
  backdrop-filter: blur(4px);
}

.mobile-drawer {
  width: 260px;
  height: 100%;
  background: var(--layer1, #111827);
  border-right: 1px solid var(--glass-border);
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow-y: auto;
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  border-bottom: 1px solid var(--glass-border);
}

.drawer-close {
  width: 32px;
  height: 32px;
  border-radius: var(--r-sm);
  border: none;
  background: transparent;
  color: var(--t3);
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.drawer-close:hover {
  background: var(--glass-hover);
  color: var(--t1);
}

.drawer-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 18px;
  color: var(--t3);
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  transition: all 0.2s var(--ease);
}
.drawer-link:hover {
  background: var(--glass-hover);
  color: var(--t2);
}
.drawer-link.active {
  background: var(--gold-soft);
  color: var(--gold-text);
}

.theme-drawer-btn {
  width: 100%;
  text-align: left;
  cursor: pointer;
  background: none;
  border: none;
  font: inherit;
}

.drawer-divider {
  height: 1px;
  background: var(--glass-border);
  margin: 8px 18px;
}

/* Drawer transition */
.drawer-enter-active,
.drawer-leave-active {
  transition: opacity 0.25s ease;
}
.drawer-enter-active .mobile-drawer,
.drawer-leave-active .mobile-drawer {
  transition: transform 0.25s ease;
}
.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
}
.drawer-enter-from .mobile-drawer,
.drawer-leave-to .mobile-drawer {
  transform: translateX(-100%);
}
</style>

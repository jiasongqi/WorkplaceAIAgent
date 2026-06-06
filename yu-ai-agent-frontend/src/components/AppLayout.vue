<template>
  <div class="app-layout">
    <!-- Sidebar -->
    <aside class="sidebar">
      <div class="sidebar-logo">
        <div class="logo-icon">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
            <path d="M8 2L14 5.5V10.5L8 14L2 10.5V5.5L8 2Z" stroke="white" stroke-width="1.2" fill="none"/>
            <circle cx="8" cy="8" r="2" fill="white" opacity="0.8"/>
          </svg>
        </div>
        <span class="logo-name">WorkPilot</span>
        <span class="logo-badge">BETA</span>
      </div>

      <nav class="nav-list">
        <div class="nav-section-label">Main</div>
        <router-link
          v-for="item in mainNav"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <span>{{ item.label }}</span>
        </router-link>

        <div class="nav-divider"></div>
        <div class="nav-section-label">Data</div>

        <router-link
          v-for="item in dataNav"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <router-link to="/admin" class="nav-item admin-item">
          <span class="nav-icon">⚙</span>
          <span>管理后台</span>
        </router-link>
        <div class="user-row">
          <div class="user-avatar">{{ userInitial }}</div>
          <span class="user-name">{{ username }}</span>
        </div>
      </div>
    </aside>

    <!-- Main -->
    <div class="main-area dot-bg">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const username = ref(localStorage.getItem('username') || '用户')
const userInitial = computed(() => (username.value || 'U').charAt(0).toUpperCase())

const mainNav = [
  { path: '/', icon: '◈', label: '工作台' },
  { path: '/chat/career', icon: '◇', label: '职场顾问' },
  { path: '/chat/super', icon: '◆', label: '超级智能体' },
  { path: '/knowledge', icon: '◻', label: '知识库' },
]

const dataNav = [
  { path: '/artifacts', icon: '◼', label: '交付物' },
  { path: '/favorites', icon: '★', label: '收藏' },
  { path: '/usage', icon: '◎', label: '用量' },
]

const isActive = (path) => {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  background: var(--bg);
}

/* ===== Sidebar ===== */
.sidebar {
  width: var(--sidebar-w);
  background: var(--sidebar-bg);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 16px 12px;
  border-bottom: 0.5px solid var(--sidebar-divider);
}
.logo-icon {
  width: 26px; height: 26px; border-radius: 6px;
  background: linear-gradient(135deg, #6366f1, #a78bfa);
  box-shadow: 0 2px 8px rgba(99,102,241,0.3);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.logo-name { font-size: 13px; font-weight: 600; color: #fff; letter-spacing: 0.02em; }
.logo-badge {
  font-size: 9px; color: #6366f1;
  background: rgba(99,102,241,0.15); border: 0.5px solid rgba(99,102,241,0.3);
  padding: 1px 5px; border-radius: 4px; font-weight: 500;
}

.nav-list { flex: 1; padding: 8px 8px 0; display: flex; flex-direction: column; }
.nav-section-label {
  font-size: 10px; color: rgba(255,255,255,0.25);
  letter-spacing: 0.08em; text-transform: uppercase;
  padding: 10px 10px 4px; font-weight: 500;
}
.nav-divider { height: 0.5px; background: var(--sidebar-divider); margin: 6px 0; }
.nav-item {
  display: flex; align-items: center; gap: 8px;
  padding: 7px 10px; border-radius: 7px;
  color: var(--sidebar-text); font-size: 13px; font-weight: 500;
  transition: background 0.15s, color 0.15s;
  margin-bottom: 1px; text-decoration: none;
}
.nav-item:hover { background: var(--sidebar-hover); color: rgba(255,255,255,0.85); }
.nav-item:active { transform: scale(0.98); }
.nav-item.active {
  background: var(--sidebar-active-bg);
  color: var(--sidebar-active-text);
  border-left: 2px solid var(--sidebar-active-text);
  padding-left: 8px;
}
.nav-icon { width: 16px; text-align: center; font-size: 13px; opacity: 0.6; transition: opacity 0.15s; }
.nav-item:hover .nav-icon, .nav-item.active .nav-icon { opacity: 1; }

.sidebar-footer { padding: 8px; border-top: 0.5px solid var(--sidebar-divider); }
.admin-item { color: rgba(255,255,255,0.35) !important; font-size: 12px; margin-bottom: 6px; }
.admin-item:hover { color: rgba(255,255,255,0.6) !important; }
.user-row { display: flex; align-items: center; gap: 8px; padding: 6px 10px; }
.user-avatar {
  width: 26px; height: 26px; border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #ec4899);
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; color: #fff; font-weight: 600; flex-shrink: 0;
}
.user-name { font-size: 12px; color: rgba(255,255,255,0.6); }

/* ===== Main ===== */
.main-area {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  position: relative;
}

.fade-enter-active, .fade-leave-active { transition: opacity 0.15s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>

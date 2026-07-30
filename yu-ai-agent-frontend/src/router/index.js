import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录 - WorkPilot', public: true }
  },
  {
    path: '/',
    name: 'Workbench',
    component: () => import('../views/Home.vue'),
    meta: { title: '工作台 - WorkPilot' }
  },
  {
    path: '/chat/career',
    name: 'CareerAdvisor',
    component: () => import('../views/CareerAdvisor.vue'),
    meta: { title: '职场顾问 - WorkPilot', requiresAuth: true }
  },
  {
    path: '/chat/super',
    name: 'SuperAgent',
    component: () => import('../views/SuperAgent.vue'),
    meta: { title: '超级智能体 - WorkPilot' }
  },
  {
    path: '/knowledge',
    name: 'KnowledgeBase',
    component: () => import('../views/KnowledgeBase.vue'),
    meta: { title: '知识库 - WorkPilot', requiresAuth: true }
  },
  {
    path: '/artifacts',
    name: 'Artifacts',
    component: () => import('../views/ArtifactAdmin.vue'),
    meta: { title: '交付物 - WorkPilot', requiresAuth: true, roles: ['ADMIN'] }
  },
  {
    path: '/favorites',
    name: 'Favorites',
    component: () => import('../views/Favorites.vue'),
    meta: { title: '收藏 - WorkPilot' }
  },
  {
    path: '/usage',
    name: 'Usage',
    component: () => import('../views/UsageDashboard.vue'),
    meta: { title: '用量 - WorkPilot' }
  },
  {
    path: '/compare',
    name: 'AdminCompare',
    component: () => import('../views/CompareView.vue'),
    meta: { title: 'Agent 对比 - WorkPilot', requiresAuth: true, roles: ['ADMIN'] }
  },
  {
    path: '/trace/:traceId',
    name: 'TraceDetail',
    component: () => import('../views/TraceDetail.vue'),
    meta: { title: '执行轨迹 - WorkPilot' }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('../views/AdminDashboard.vue'),
    meta: { title: '管理后台 - WorkPilot', requiresAuth: true, roles: ['ADMIN'] }
  },
  // LoveMaster — hidden, kept for backward compatibility
  {
    path: '/love-master',
    name: 'LoveMaster',
    component: () => import('../views/LoveMaster.vue'),
    meta: { title: '沟通助手 - WorkPilot' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.meta.title) document.title = to.meta.title
  const token = localStorage.getItem('token')
  const role = localStorage.getItem('role') || 'GUEST'
  if (to.meta.public) return next()
  if (to.meta.requiresAuth && !token) {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }
  if (to.meta.roles && !to.meta.roles.includes(role)) {
    return next({ path: '/' })
  }
  next()
})

export default router

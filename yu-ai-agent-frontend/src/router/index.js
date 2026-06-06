import { createRouter, createWebHistory } from 'vue-router'

const routes = [
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
    meta: { title: '职场顾问 - WorkPilot' }
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
    meta: { title: '知识库 - WorkPilot' }
  },
  {
    path: '/artifacts',
    name: 'Artifacts',
    component: () => import('../views/ArtifactAdmin.vue'),
    meta: { title: '交付物 - WorkPilot' }
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
    path: '/admin/compare',
    name: 'AdminCompare',
    component: () => import('../views/CompareView.vue'),
    meta: { title: 'Agent 对比 - WorkPilot' }
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
    meta: { title: '管理后台 - WorkPilot' }
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
  next()
})

export default router

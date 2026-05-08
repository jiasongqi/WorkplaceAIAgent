import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: { title: '首页 - 职场生存智囊' }
  },
  {
    path: '/career-advisor',
    name: 'CareerAdvisor',
    component: () => import('../views/CareerAdvisor.vue'),
    meta: { title: '职场顾问 - 职场生存智囊' }
  },
  {
    path: '/super-agent',
    name: 'SuperAgent',
    component: () => import('../views/SuperAgent.vue'),
    meta: { title: 'AI超级智能体 - 职场生存智囊' }
  },
  {
    path: '/love-master',
    name: 'LoveMaster',
    component: () => import('../views/LoveMaster.vue'),
    meta: { title: 'AI恋爱大师 - 职场生存智囊' }
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
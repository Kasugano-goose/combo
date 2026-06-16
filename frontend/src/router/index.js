import { createRouter, createWebHistory } from 'vue-router'
import { usePlayerStore } from '../stores/player'

const routes = [
  { path: '/', component: () => import('../views/Register.vue'), meta: { guest: true } },
  { path: '/login', component: () => import('../views/Login.vue'), meta: { guest: true } },
  { path: '/home', component: () => import('../views/Home.vue'), meta: { auth: true } },
  { path: '/friends', component: () => import('../views/Friends.vue'), meta: { auth: true } },
  { path: '/chat', component: () => import('../views/Chat.vue'), meta: { auth: true } },
  { path: '/match', component: () => import('../views/Match.vue'), meta: { auth: true } },
  { path: '/scene', component: () => import('../views/Scene.vue'), meta: { auth: true } },
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  const store = usePlayerStore()

  // 首次访问时尝试恢复登录状态
  if (!store.initialized) {
    await store.fetchPlayer()
  }

  if (to.meta.auth && !store.isLoggedIn) {
    next('/login')
  } else if (to.meta.guest && store.isLoggedIn) {
    next('/home')
  } else {
    next()
  }
})

export default router

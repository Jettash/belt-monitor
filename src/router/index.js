import { createRouter, createWebHistory } from 'vue-router'

function isAuthenticated() {
  return sessionStorage.getItem('auth') === 'true'
}

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guestOnly: true }
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('@/views/DashboardView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/report',
    name: 'report',
    component: () => import('@/views/ReportView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/',
    redirect: () => (isAuthenticated() ? '/dashboard' : '/login')
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: () => (isAuthenticated() ? '/dashboard' : '/login')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const authed = isAuthenticated()

  if (to.meta.requiresAuth && !authed) {
    return { path: '/login' }
  }

  if (to.meta.guestOnly && authed) {
    return { path: '/dashboard' }
  }

  return true
})

export default router

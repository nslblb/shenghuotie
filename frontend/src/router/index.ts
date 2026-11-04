import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
    },
    {
      path: '/reset-password',
      name: 'reset-password',
      component: () => import('@/views/ResetPasswordView.vue'),
    },
    {
      path: '/post/:id',
      name: 'post-detail',
      component: () => import('@/views/PostDetailView.vue'),
    },
    {
      path: '/create',
      name: 'create-post',
      component: () => import('@/views/CreatePostView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/post/:id/edit',
      name: 'edit-post',
      component: () => import('@/views/CreatePostView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/drafts',
      name: 'drafts',
      component: () => import('@/views/DraftsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/profile/:id',
      name: 'profile',
      component: () => import('@/views/ProfileView.vue'),
    },
    {
      path: '/notifications',
      name: 'notifications',
      component: () => import('@/views/NotificationsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/messages',
      name: 'messages',
      component: () => import('@/views/MessagesView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/messages/:partnerId',
      name: 'chat',
      component: () => import('@/views/ChatView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/likes',
      name: 'likes',
      component: () => import('@/views/LikesView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/follows',
      name: 'follows',
      component: () => import('@/views/FollowsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/favorites',
      name: 'favorites',
      component: () => import('@/views/FavoritesView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/my-posts',
      name: 'my-posts',
      component: () => import('@/views/MyPostsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin/login',
      name: 'admin-login',
      component: () => import('@/views/admin/AdminLoginView.vue'),
    },
    {
      path: '/admin',
      component: () => import('@/views/admin/AdminLayout.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
      children: [
        {
          path: '',
          redirect: '/admin/dashboard',
        },
        {
          path: 'dashboard',
          name: 'admin-dashboard',
          component: () => import('@/views/admin/DashboardView.vue'),
        },
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('@/views/admin/UsersView.vue'),
        },
        {
          path: 'posts',
          name: 'admin-posts',
          component: () => import('@/views/admin/PostsView.vue'),
        },
        {
          path: 'comments',
          name: 'admin-comments',
          component: () => import('@/views/admin/CommentsView.vue'),
        },
        {
          path: 'reports',
          name: 'admin-reports',
          component: () => import('@/views/admin/ReportsView.vue'),
        },
        {
          path: 'appeals',
          name: 'admin-appeals',
          component: () => import('@/views/admin/AppealsView.vue'),
        },
        {
          path: 'topics',
          name: 'admin-topics',
          component: () => import('@/views/admin/TopicsView.vue'),
        },
        {
          path: 'sensitive-words',
          name: 'admin-sensitive-words',
          component: () => import('@/views/admin/SensitiveWordsView.vue'),
        },
        {
          path: 'knowledge',
          name: 'admin-knowledge',
          component: () => import('@/views/admin/KnowledgeView.vue'),
        },
        {
          path: 'logs',
          name: 'admin-logs',
          component: () => import('@/views/admin/LogsView.vue'),
        },
        {
          path: 'settings',
          name: 'admin-settings',
          component: () => import('@/views/admin/SettingsView.vue'),
        },
      ],
    },
  ],
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    return { top: 0 }
  },
})

// 导航守卫
router.beforeEach((to, _from) => {
  if (to.meta.requiresAuth && !getToken()) {
    // 后台管理路由未登录时跳转到管理员登录页
    if (to.meta.requiresAdmin) {
      return { name: 'admin-login' }
    } else {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }
})

export default router

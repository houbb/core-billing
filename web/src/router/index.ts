import { createRouter, createWebHashHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/account',
    component: () => import('@/layouts/UserLayout.vue'),
    children: [
      { path: '', component: () => import('@/pages/account/MyAccount.vue') },
    ],
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    children: [
      { path: 'accounts', component: () => import('@/pages/admin/AccountList.vue') },
      { path: 'accounts/:id', component: () => import('@/pages/admin/AccountDetail.vue') },
      { path: 'transactions', component: () => import('@/pages/admin/TransactionList.vue') },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/account' },
]

export default createRouter({
  history: createWebHashHistory(),
  routes,
})
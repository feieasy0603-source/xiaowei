import { createRouter, createWebHistory } from 'vue-router'
import { getAdminToken } from '@/api/http'

const router = createRouter({
  history: createWebHistory('/admin/'),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/Login.vue'), meta: { title: '登录' } },
    {
      path: '/',
      component: () => import('@/views/Layout.vue'),
      meta: { auth: true },
      children: [
        { path: '', name: 'dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: 'AI 运营看板' } },
        { path: 'jobs', name: 'jobs', component: () => import('@/views/Jobs.vue'), meta: { title: 'AI 生成任务' } },
        { path: 'papers', name: 'papers', component: () => import('@/views/Papers.vue'), meta: { title: '论文草稿' } },
        { path: 'literature', name: 'literature', component: () => import('@/views/Literature.vue'), meta: { title: '文献库' } },
        { path: 'outlines', name: 'outlines', component: () => import('@/views/Outlines.vue'), meta: { title: '提纲模板' } },
        { path: 'products', name: 'products', component: () => import('@/views/Products.vue'), meta: { title: 'AI 产品' } },
        { path: 'ai-settings', name: 'aiSettings', component: () => import('@/views/AiSettings.vue'), meta: { title: 'AI 模型池' } },
        { path: 'deploy', name: 'deploy', component: () => import('@/views/DeployWizard.vue'), meta: { title: '生产部署向导' } },
        { path: 'users', name: 'users', component: () => import('@/views/Users.vue'), meta: { title: '用户管理' } },
        { path: 'vip-quotas', name: 'vipQuotas', component: () => import('@/views/VipQuotas.vue'), meta: { title: 'VIP 配额' } },
        { path: 'orders', name: 'orders', component: () => import('@/views/Orders.vue'), meta: { title: '订单管理' } },
        { path: 'wallet-recharges', name: 'walletRecharges', component: () => import('@/views/WalletRecharges.vue'), meta: { title: '充值订单' } },
        { path: 'payment-records', name: 'paymentRecords', component: () => import('@/views/PaymentRecords.vue'), meta: { title: '支付流水' } },
        { path: 'channels', name: 'channels', component: () => import('@/views/Channels.vue'), meta: { title: '渠道管理' } },
        { path: 'gift-codes', name: 'giftCodes', component: () => import('@/views/GiftCodes.vue'), meta: { title: '礼包码' } },
        { path: 'referral-settings', name: 'referralSettings', component: () => import('@/views/ReferralSettings.vue'), meta: { title: '分享奖励' } },
        { path: 'support-settings', name: 'supportSettings', component: () => import('@/views/SupportSettings.vue'), meta: { title: '在线客服' } },
        { path: 'branding-settings', name: 'brandingSettings', component: () => import('@/views/BrandingSettings.vue'), meta: { title: '站点品牌' } },
        { path: 'schools', name: 'schools', component: () => import('@/views/Schools.vue'), meta: { title: '学校模板' } },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const needsAuth = to.matched.some((record) => record.meta.auth)
  if (needsAuth && !getAdminToken()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
})

export default router

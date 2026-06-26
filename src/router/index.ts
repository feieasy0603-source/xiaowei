import { createRouter, createWebHashHistory } from 'vue-router'
import { nanoid } from 'nanoid'
import AppShellLayout from '@/layouts/AppShellLayout.vue'
import { getToken, useApiEnabled } from '@/api/http'
import { useAppStore } from '@/stores/app'
import { usePaperStore } from '@/stores/paper'
import { validateAndSetDCode } from '@/composables/useChannelCode'
import { captureInviteFromQuery } from '@/composables/useReferralCode'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      redirect: '/iw/intelligentWriting/0',
    },
    {
      path: '/iw',
      component: AppShellLayout,
      children: [
        {
          path: 'intelligentWriting/:step',
          name: 'intelligentWriting',
          component: () => import('@/views/iw/IwMainContent.vue'),
        },
        {
          path: 'orders',
          name: 'orders',
          component: () => import('@/views/orders/OrderList.vue'),
          meta: { title: '查询结果' },
        },
        {
          path: 'jobs/:id',
          name: 'jobDetail',
          component: () => import('@/views/jobs/JobDetail.vue'),
          meta: { requiresAuth: true, title: '任务详情' },
        },
        {
          path: 'account',
          name: 'account',
          component: () => import('@/views/account/UserAccount.vue'),
          meta: { requiresAuth: true, title: '用户中心' },
        },
        {
          path: 'settings',
          name: 'settings',
          redirect: { name: 'account', query: { tab: 'prefs' } },
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  if (useApiEnabled() && to.meta.requiresAuth && !getToken()) {
    const appStore = useAppStore()
    appStore.openLogin()
    return { name: 'intelligentWriting', params: { step: '0' }, query: { lunwen: to.query.lunwen } }
  }

  if (
    useApiEnabled() &&
    to.name === 'intelligentWriting' &&
    to.query.wizard === '1' &&
    Number(to.params.step) >= 3 &&
    !getToken()
  ) {
    useAppStore().openLogin()
    return {
      name: 'intelligentWriting',
      params: { step: '2' },
      query: { ...to.query },
    }
  }

  if (to.params.step === '1' && to.query.pro !== '1' && to.query.wizard !== '1') {
    return {
      name: 'intelligentWriting',
      params: { step: '0' },
      query: { ...to.query, pro: '1' },
    }
  }
  if (to.params.step === '2' && to.query.wizard !== '1') {
    return {
      name: 'intelligentWriting',
      params: { step: '3' },
      query: { ...to.query },
    }
  }

  if (to.name !== 'intelligentWriting') return

  const appStore = useAppStore()
  const paperStore = usePaperStore()

  const dc = to.query.dCode
  if (typeof dc === 'string' && dc.trim()) {
    void validateAndSetDCode(dc)
  }
  captureInviteFromQuery(to.query as Record<string, unknown>)

  if (to.query.pro === '1') appStore.openProEdition()
  else appStore.closeProEdition()

  if (to.query.wizard === '1') appStore.openWizard()
  else appStore.closeWizard()

  let lunwenId = typeof to.query.lunwen === 'string' ? to.query.lunwen : ''
  if (!lunwenId) {
    lunwenId = nanoid(8)
    return {
      name: 'intelligentWriting',
      params: to.params,
      query: { ...to.query, lunwen: lunwenId },
    }
  }

  paperStore.initPaper(lunwenId)

  const stepNum = Number(to.params.step)
  if (to.query.wizard === '1' && Number.isFinite(stepNum)) {
    const max = paperStore.draft?.maxVisitedStep ?? 0
    if (stepNum > max) {
      return {
        name: 'intelligentWriting',
        params: { step: String(max) },
        query: to.query,
      }
    }
    paperStore.markVisitedStep(stepNum)
    if (useApiEnabled() && getToken()) {
      void paperStore.persistDraft()
    }
  } else if (Number.isFinite(stepNum)) {
    paperStore.markVisitedStep(stepNum)
  }
})

export default router

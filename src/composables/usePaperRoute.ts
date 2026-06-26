import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { nanoid } from 'nanoid'
import { useAppStore } from '@/stores/app'
import { usePaperStore } from '@/stores/paper'

/** 路由同步逻辑见 router/index.ts，此处仅提供导航方法，避免多组件重复注册 watch 导致死循环 */
export function usePaperRoute() {
  const route = useRoute()
  const router = useRouter()
  const appStore = useAppStore()
  const paperStore = usePaperStore()

  const step = computed(() => {
    const s = Number(route.params.step)
    return Number.isFinite(s) ? Math.min(3, Math.max(0, s)) : 0
  })

  const wizardMode = computed(() => route.query.wizard === '1')
  const lunwen = computed(() => (route.query.lunwen as string) || '')

  function buildQuery(extra: Record<string, string | undefined> = {}) {
    const q: Record<string, string> = {}
    const id = paperStore.currentId ?? lunwen.value
    if (id) q.lunwen = id
    const dc = appStore.dCode || (route.query.dCode as string)
    if (dc) q.dCode = dc
    if (appStore.proEditionOpen && extra.pro === undefined) q.pro = '1'
    if (wizardMode.value && extra.wizard === undefined) q.wizard = '1'
    Object.assign(q, extra)
    for (const key of Object.keys(extra)) {
      const v = extra[key]
      if (v === undefined || v === '') delete q[key]
    }
    return q
  }

  function ensureLunwen() {
    let id = lunwen.value
    if (!id) {
      id = nanoid(8)
      paperStore.initPaper(id)
      router.replace({
        name: 'intelligentWriting',
        params: { step: String(step.value) },
        query: buildQuery({ lunwen: id }),
      })
      return id
    }
    paperStore.initPaper(id)
    return id
  }

  function openProEdition() {
    ensureLunwen()
    appStore.closeWizard()
    appStore.openProEdition()
    router.replace({
      name: 'intelligentWriting',
      params: { step: '0' },
      query: buildQuery({ pro: '1', wizard: undefined }),
    })
  }

  function openWizard() {
    ensureLunwen()
    appStore.closeProEdition()
    appStore.openWizard()
    router.replace({
      name: 'intelligentWriting',
      params: { step: '0' },
      query: buildQuery({ wizard: '1', pro: undefined }),
    })
  }

  function closeProEdition() {
    appStore.closeProEdition()
    const q = buildQuery({ pro: undefined })
    delete q.pro
    router.replace({
      name: 'intelligentWriting',
      params: { step: '0' },
      query: q,
    })
  }

  function goToStep(
    target: number,
    replace = false,
    options?: { autogen?: boolean },
  ) {
    const s = Math.min(3, Math.max(0, target))
    paperStore.markVisitedStep(s)
    const extra: Record<string, string | undefined> = {}
    if (options?.autogen) extra.autogen = '1'
    else if (s !== 3) extra.autogen = ''
    const nav = {
      name: 'intelligentWriting' as const,
      params: { step: String(s) },
      query: buildQuery(extra),
    }
    if (replace) router.replace(nav)
    else router.push(nav)
  }

  return {
    step,
    wizardMode,
    lunwen,
    ensureLunwen,
    goToStep,
    openProEdition,
    openWizard,
    closeProEdition,
    buildQuery,
  }
}

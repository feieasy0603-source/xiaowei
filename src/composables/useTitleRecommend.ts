import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { recommendTitles } from '@/api/modules/ai'
import { useApiEnabled } from '@/api/http'
import { requireLogin } from '@/composables/useRequireLogin'
import { hotTitleCategories } from '@/mocks/hotTitles'
import { buildTitlesFromKeyword } from '@/utils/titleRecommend'

export type RecommendPhase = 'idle' | 'analyzing' | 'done'

export function useTitleRecommend() {
  const visible = ref(false)
  const phase = ref<RecommendPhase>('idle')
  const aiTitles = ref<string[]>([])
  const activeCategoryId = ref(hotTitleCategories[0]!.id)
  const batchIndex = ref(0)

  const activeCategory = computed(
    () =>
      hotTitleCategories.find((c) => c.id === activeCategoryId.value) ??
      hotTitleCategories[0]!,
  )

  const hotTitlesPage = computed(() => {
    const cat = activeCategory.value
    const size = 4
    const start = (batchIndex.value * size) % cat.titles.length
    const items: string[] = []
    for (let i = 0; i < size; i++) {
      items.push(cat.titles[(start + i) % cat.titles.length]!)
    }
    return items
  })

  function open() {
    visible.value = true
  }

  function close() {
    visible.value = false
  }

  function switchCategory(id: string) {
    activeCategoryId.value = id
    batchIndex.value = 0
  }

  function refreshHotBatch() {
    batchIndex.value += 1
  }

  function resetResults() {
    phase.value = 'idle'
    aiTitles.value = []
  }

  async function analyze(keyword: string, productId?: string) {
    const k = keyword.trim()
    if (k.length < 2) {
      resetResults()
      ElMessage.info('请先输入 2 字以上的选题关键词，推荐会更精准')
      return
    }

    phase.value = 'analyzing'
    aiTitles.value = []

    try {
      if (useApiEnabled()) {
        if (!(await requireLogin())) {
          phase.value = 'idle'
          return
        }
        const titles = await recommendTitles(k, productId)
        if (titles.length > 0) {
          aiTitles.value = titles
          phase.value = 'done'
          return
        }
        ElMessage.warning('未生成推荐标题，请更换关键词')
        phase.value = 'idle'
        return
      }
    } catch (e) {
      ElMessage.error((e as Error).message)
      phase.value = 'idle'
      return
    }

    await new Promise((r) => setTimeout(r, 900))
    aiTitles.value = buildTitlesFromKeyword(k)
    phase.value = 'done'
  }

  return {
    visible,
    phase,
    aiTitles,
    activeCategoryId,
    hotTitleCategories,
    activeCategory,
    hotTitlesPage,
    open,
    close,
    switchCategory,
    refreshHotBatch,
    resetResults,
    analyze,
  }
}

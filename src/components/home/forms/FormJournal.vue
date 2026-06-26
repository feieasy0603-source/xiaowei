<script setup lang="ts">
import { computed, onMounted } from 'vue'
import CompactFormBase from '@/components/home/shared/compact/CompactFormBase.vue'
import CompactJournalLevelRow from '@/components/home/shared/compact/CompactJournalLevelRow.vue'
import CompactLanguageRow from '@/components/home/shared/compact/CompactLanguageRow.vue'
import CompactModelRow from '@/components/home/shared/compact/CompactModelRow.vue'
import CompactWordCountRow from '@/components/home/shared/compact/CompactWordCountRow.vue'
import { usePaperRoute } from '@/composables/usePaperRoute'
import { usePaperStore } from '@/stores/paper'
import type { ProductConfig } from '@/types/product'

const props = defineProps<{ product: ProductConfig }>()
const { openProEdition } = usePaperRoute()
const paperStore = usePaperStore()

const wordCount = computed({
  get: () => paperStore.draft?.meta.wordCount ?? props.product.defaultWordCount ?? 5000,
  set: (v: number) => {
    const d = paperStore.draft
    if (!d) return
    paperStore.updateDraft({ meta: { ...d.meta, wordCount: v } })
  },
})

const wordOptions = [
  { value: 3000, label: '3000字左右' },
  { value: 5000, label: '5000字左右' },
  { value: 8000, label: '8000字左右' },
  { value: 10000, label: '10000字左右' },
]

onMounted(() => {
  const d = paperStore.draft
  if (!d) return
  if (d.meta.category === '教育经管') {
    paperStore.updateDraft({ meta: { ...d.meta, category: '国家级期刊' } })
  }
})
</script>

<template>
  <CompactFormBase :product="product">
    <CompactJournalLevelRow
      :pro-link-text="product.proLinkText"
      @pro="openProEdition()"
    />
    <CompactWordCountRow v-model="wordCount" :options="wordOptions" />
    <CompactLanguageRow />
    <CompactModelRow />
  </CompactFormBase>
</template>

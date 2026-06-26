<script setup lang="ts">
import { computed } from 'vue'
import CompactFormBase from '@/components/home/shared/compact/CompactFormBase.vue'
import CompactDegreeRow from '@/components/home/shared/compact/CompactDegreeRow.vue'
import CompactLanguageRow from '@/components/home/shared/compact/CompactLanguageRow.vue'
import CompactModelRow from '@/components/home/shared/compact/CompactModelRow.vue'
import CompactWordCountRow from '@/components/home/shared/compact/CompactWordCountRow.vue'
import { usePaperStore } from '@/stores/paper'
import type { ProductConfig } from '@/types/product'

const props = defineProps<{ product: ProductConfig }>()
const paperStore = usePaperStore()

const wordCount = computed({
  get: () => paperStore.draft?.meta.wordCount ?? props.product.defaultWordCount ?? 8000,
  set: (v: number) => {
    const d = paperStore.draft
    if (!d) return
    paperStore.updateDraft({ meta: { ...d.meta, wordCount: v } })
  },
})

const wordOptions = [
  { value: 6000, label: '约6000字' },
  { value: 8000, label: '约8000字' },
  { value: 12000, label: '约12000字' },
]
</script>

<template>
  <CompactFormBase :product="product">
    <CompactDegreeRow />
    <CompactLanguageRow />
    <CompactModelRow />
    <CompactWordCountRow v-model="wordCount" :options="wordOptions" />
  </CompactFormBase>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import CompactFormBase from '@/components/home/shared/compact/CompactFormBase.vue'
import CompactDegreeRow from '@/components/home/shared/compact/CompactDegreeRow.vue'
import CompactLanguageRow from '@/components/home/shared/compact/CompactLanguageRow.vue'
import CompactModelRow from '@/components/home/shared/compact/CompactModelRow.vue'
import CompactWordCountRow from '@/components/home/shared/compact/CompactWordCountRow.vue'
import { usePaperRoute } from '@/composables/usePaperRoute'
import { usePaperStore } from '@/stores/paper'
import { defaultMeta } from '@/types/paper'
import type { ProductConfig } from '@/types/product'

const props = defineProps<{ product: ProductConfig }>()
const { openProEdition } = usePaperRoute()
const paperStore = usePaperStore()

const recommendedWordCount = computed(() => props.product.defaultWordCount ?? 12000)

const wordCount = computed({
  get: () => paperStore.draft?.meta.wordCount ?? recommendedWordCount.value,
  set: (v: number) => {
    const d = paperStore.draft
    if (!d) return
    paperStore.updateDraft({ meta: { ...d.meta, wordCount: v } })
  },
})

const wordOptions = [
  { value: 8000, label: '8000字左右' },
  { value: 12000, label: '12000字左右' },
  { value: 15000, label: '15000字左右' },
  { value: 20000, label: '20000字左右' },
]

function applyProductDefaults() {
  const d = paperStore.draft
  if (!d) return
  const def = defaultMeta()
  const isFresh =
    d.meta.wordCount === def.wordCount &&
    d.meta.degree === def.degree &&
    !d.title.trim()
  if (isFresh) {
    paperStore.updateDraft({
      meta: {
        ...d.meta,
        wordCount: recommendedWordCount.value,
        paperType: props.product.label,
      },
    })
  }
}

onMounted(applyProductDefaults)
watch(() => props.product.id, applyProductDefaults)
</script>

<template>
  <CompactFormBase :product="product">
    <CompactDegreeRow
      :pro-link-text="product.proLinkText"
      @pro="openProEdition()"
    />
    <CompactLanguageRow />
    <CompactModelRow />
    <CompactWordCountRow v-model="wordCount" :options="wordOptions" />
  </CompactFormBase>
</template>

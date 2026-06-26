<script setup lang="ts">
import CompactFormBase from '@/components/home/shared/compact/CompactFormBase.vue'
import CompactDegreeRow from '@/components/home/shared/compact/CompactDegreeRow.vue'
import CompactModelRow from '@/components/home/shared/compact/CompactModelRow.vue'
import { usePaperRoute } from '@/composables/usePaperRoute'
import type { ProductConfig } from '@/types/product'

defineProps<{ product: ProductConfig }>()
const { openProEdition } = usePaperRoute()

const degreeOptions = ['本科', '硕士', '博士', '学者']
</script>

<template>
  <CompactFormBase :product="product">
    <CompactDegreeRow
      :options="degreeOptions"
      :pro-link-text="product.proLinkText"
      @pro="openProEdition()"
    />
    <CompactModelRow />
  </CompactFormBase>
</template>

<script setup lang="ts">
import CompactFormBase from '@/components/home/shared/compact/CompactFormBase.vue'
import CompactDegreeRow from '@/components/home/shared/compact/CompactDegreeRow.vue'
import CompactModelRow from '@/components/home/shared/compact/CompactModelRow.vue'
import type { ProductConfig } from '@/types/product'

defineProps<{ product: ProductConfig }>()
</script>

<template>
  <CompactFormBase :product="product">
    <CompactDegreeRow />
    <CompactModelRow />
  </CompactFormBase>
</template>

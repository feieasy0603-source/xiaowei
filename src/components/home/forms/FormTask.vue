<script setup lang="ts">
import CompactFormBase from '@/components/home/shared/compact/CompactFormBase.vue'
import CompactDegreeRow from '@/components/home/shared/compact/CompactDegreeRow.vue'
import CompactModelRow from '@/components/home/shared/compact/CompactModelRow.vue'
import FaqSection from '@/components/home/FaqSection.vue'
import { faqTask } from '@/mocks/menu'
import type { ProductConfig } from '@/types/product'

defineProps<{ product: ProductConfig }>()
</script>

<template>
  <CompactFormBase :product="{ ...product, showFaq: false }">
    <CompactDegreeRow />
    <CompactModelRow />
    <div class="task-faq">
      <FaqSection :items="faqTask" />
    </div>
  </CompactFormBase>
</template>

<style scoped>
.task-faq {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f1f5f9;
}
</style>

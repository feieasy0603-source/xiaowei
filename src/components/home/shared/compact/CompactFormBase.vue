<script setup lang="ts">
import CompactFormShell from '@/components/home/shared/CompactFormShell.vue'
import TitleBarCompact from '@/components/home/shared/TitleBarCompact.vue'
import { useProductSubmit } from '@/composables/useProductSubmit'
import type { ProductConfig } from '@/types/product'

const props = defineProps<{
  product: ProductConfig
  skipPreview?: boolean
  jobPayload?: Record<string, unknown> | (() => Record<string, unknown>)
}>()

const { loading, agreed, recentNotice, recentIsDemo, submit } = useProductSubmit(
  () => props.product,
)

function onSubmit() {
  const extra =
    typeof props.jobPayload === 'function' ? props.jobPayload() : props.jobPayload
  if (props.skipPreview) {
    submit({ skipPreview: true, jobPayload: extra })
  } else {
    submit(extra ? { jobPayload: extra } : undefined)
  }
}
</script>

<template>
  <CompactFormShell
    v-model:agreed="agreed"
    :product="product"
    :title="product.titleFieldLabel"
    :loading="loading"
    :recent-notice="recentNotice"
    :recent-demo="recentIsDemo"
    @submit="onSubmit()"
  >
    <TitleBarCompact :placeholder="product.titlePlaceholder" />
    <div class="options-block">
      <slot />
    </div>
  </CompactFormShell>
</template>

<style scoped>
.options-block {
  border-top: 1px solid #f1f5f9;
  margin-top: 4px;
}
</style>

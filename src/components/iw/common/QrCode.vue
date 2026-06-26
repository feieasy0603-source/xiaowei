<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  content: string
  size?: number
}>()

const size = computed(() => props.size ?? 180)

const imgSrc = computed(() => {
  if (!props.content) return ''
  return `https://api.qrserver.com/v1/create-qr-code/?size=${size.value}x${size.value}&data=${encodeURIComponent(props.content)}`
})
</script>

<template>
  <img v-if="imgSrc" :src="imgSrc" :width="size" :height="size" alt="支付二维码" class="qr-img" />
</template>

<style scoped>
.qr-img {
  display: block;
  margin: 0 auto;
  border-radius: 8px;
}
</style>

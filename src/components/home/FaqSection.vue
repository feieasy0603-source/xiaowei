<script setup lang="ts">
import { faqItems } from '@/mocks/menu'

withDefaults(
  defineProps<{
    items?: { q: string; a: string }[]
  }>(),
  {
    items: () => faqItems,
  },
)
</script>

<template>
  <div class="faq">
    <div v-for="(item, i) in items" :key="i" class="faq-item">
      <p class="faq-q">{{ item.q }}</p>
      <p class="faq-a">{{ item.a }}</p>
    </div>
  </div>
</template>

<style scoped>
.faq {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px 28px;
  margin-top: 28px;
  padding-top: 22px;
  border-top: 1px dashed #e2e8f0;
}

.faq-item {
  padding: 12px 14px;
  background: #f8fafc;
  border-radius: 10px;
  border: 1px solid #f1f5f9;
  transition: border-color 0.12s;
}

.faq-item:hover {
  border-color: #e2e8f0;
}

.faq-q {
  font-size: 13px;
  font-weight: 600;
  color: var(--xw-text);
  margin-bottom: 6px;
  line-height: 1.45;
}

.faq-a {
  font-size: 12px;
  color: var(--xw-muted);
  line-height: 1.55;
}

@media (max-width: 700px) {
  .faq {
    grid-template-columns: 1fr;
  }
}
</style>

<script setup lang="ts">
import OutlineProPanel from '@/components/home/pro/OutlineProPanel.vue'
import OutlineSearchPanel from '@/components/home/pro/OutlineSearchPanel.vue'
import { usePaperStore } from '@/stores/paper'

const paperStore = usePaperStore()

function onSelectOutline(text: string) {
  paperStore.clearOutline()
  paperStore.setOutlineText(text)
}
</script>

<template>
  <div class="pro-edition">
    <div class="pro-grid">
      <OutlineProPanel />
      <OutlineSearchPanel @select="onSelectOutline" />
    </div>
  </div>
</template>

<style scoped>
.pro-edition {
  width: 100%;
}

.pro-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  align-items: stretch;
}

@media (max-width: 1024px) {
  .pro-grid {
    grid-template-columns: 1fr;
  }
}
</style>

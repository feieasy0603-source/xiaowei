<script setup lang="ts">
import { computed } from 'vue'
import ProcessFlowPanel from '@/components/home/process/ProcessFlowPanel.vue'
import ProductForm from '@/components/home/ProductForm.vue'
import { useProductsStore } from '@/stores/products'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
const productsStore = useProductsStore()
const product = computed(() => productsStore.getProduct(appStore.activeMenuId))
</script>

<template>
  <div class="intelligent-writing">
    <header v-if="!product.compactLayout && product.banner" class="page-hero">
      <div class="hero-text">
        <p class="hero-banner">{{ product.banner }}</p>
      </div>
    </header>

    <div class="layout-container">
      <div class="intelligent-writing-content">
        <section
          class="intelligent-writing-right col-form"
          :class="{ 'col-form--compact': product.compactLayout }"
        >
          <ProductForm :key="product.id" />
        </section>
        <aside class="intelligent-writing-middle col-process">
          <ProcessFlowPanel
            :key="product.id"
            :variant="product.processVariant"
            :label="product.label"
          />
        </aside>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-hero {
  margin-bottom: 18px;
  padding: 0 2px;
}

.hero-title {
  font-size: 24px;
  font-weight: 800;
  color: var(--xw-text);
  letter-spacing: -0.02em;
  margin-bottom: 6px;
}

.hero-banner {
  font-size: 14px;
  color: var(--xw-text-secondary);
  line-height: 1.55;
  max-width: 720px;
}

.col-form--compact {
  display: flex;
  justify-content: center;
  align-items: flex-start;
}

.col-process {
  position: sticky;
  top: 12px;
}

@media (max-width: 1024px) {
  .col-process {
    position: static;
  }
}
</style>

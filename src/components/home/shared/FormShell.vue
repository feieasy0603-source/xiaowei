<script setup lang="ts">
import FaqSection from '@/components/home/FaqSection.vue'
import { useApiEnabled } from '@/api/http'
import type { ProductConfig } from '@/types/product'

defineProps<{
  product: ProductConfig
  loading?: boolean
  recentNotice?: string
  recentDemo?: boolean
}>()

defineEmits<{ submit: [] }>()

const agreed = defineModel<boolean>('agreed', { default: false })
</script>

<template>
  <div class="form-shell" :class="{ centered: product.centerTitle }">
    <slot name="head" />

    <div class="form-body">
      <slot />
    </div>

    <div class="form-footer">
      <el-checkbox v-if="product.agreementText" v-model="agreed" class="agree">
        {{ product.agreementText }}
      </el-checkbox>

      <el-button
        type="primary"
        size="large"
        class="submit-btn"
        :loading="loading"
        @click="$emit('submit')"
      >
        {{ product.submitLabel }}
      </el-button>

      <div
        v-if="recentNotice"
        class="notice-bar"
        :class="{ demo: recentDemo }"
      >
        <span v-if="recentDemo && !useApiEnabled()" class="demo-tag">演示</span>
        {{ recentNotice }}
      </div>
    </div>

    <FaqSection v-if="product.showFaq !== false" />
  </div>
</template>

<style scoped>
.form-shell {
  background: var(--xw-card);
  border-radius: var(--xw-radius-lg);
  padding: 28px 32px 32px;
  border: 1px solid var(--xw-border);
  box-shadow: var(--xw-shadow);
}

.form-body {
  margin-bottom: 4px;
}

.form-footer {
  margin-top: 4px;
}

.form-shell.centered :deep(.form-inner-title) {
  text-align: center;
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 20px;
  color: var(--xw-text);
}

.agree {
  margin: 18px 0 14px;
  font-size: 13px;
  line-height: 1.65;
  color: var(--xw-text-secondary);
}

.agree :deep(.el-checkbox__label) {
  color: var(--xw-text-secondary);
}

.submit-btn {
  width: 100%;
  height: 50px;
  font-size: 17px;
  font-weight: 600;
  border-radius: 12px;
  background: linear-gradient(90deg, #3b82f6, #2563eb);
  border: none;
  letter-spacing: 0.04em;
  box-shadow: 0 6px 20px rgb(37 99 235 / 28%);
  transition: transform 0.12s, box-shadow 0.12s;
}

.submit-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 24px rgb(37 99 235 / 35%);
}

.notice-bar {
  margin-top: 14px;
  padding: 10px 14px;
  background: linear-gradient(90deg, #fefce8, #fef9c3);
  border: 1px solid #fde047;
  border-radius: 10px;
  font-size: 13px;
  color: #854d0e;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.notice-bar.demo {
  background: linear-gradient(90deg, #f8fafc, #f1f5f9);
  border-color: #e2e8f0;
  color: #64748b;
}

.demo-tag {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  background: #e2e8f0;
  color: #475569;
  flex-shrink: 0;
}

@media (max-width: 640px) {
  .form-shell {
    padding: 20px 18px 24px;
  }

  .form-footer {
    position: sticky;
    bottom: 0;
    z-index: 5;
    margin: 0 -18px -24px;
    padding: 12px 18px 16px;
    background: linear-gradient(
      180deg,
      rgb(255 255 255 / 0%) 0%,
      rgb(255 255 255 / 92%) 12%,
      #fff 24%
    );
    backdrop-filter: blur(6px);
  }
}
</style>

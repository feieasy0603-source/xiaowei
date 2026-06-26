<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import FaqSection from '@/components/home/FaqSection.vue'
import { useApiEnabled } from '@/api/http'
import type { ProductConfig } from '@/types/product'

defineProps<{
  product: ProductConfig
  title: string
  loading?: boolean
  recentNotice?: string
  recentDemo?: boolean
}>()

const emit = defineEmits<{ submit: [] }>()

const agreed = defineModel<boolean>('agreed', { default: false })

const router = useRouter()
const route = useRoute()

function goOrders() {
  router.push({ name: 'orders', query: { ...route.query } })
}
</script>

<template>
  <div class="compact-form-shell">
    <h2 class="form-title">{{ title }}</h2>

    <div class="form-body">
      <slot />
    </div>

    <div class="action-row">
      <el-button
        type="primary"
        size="large"
        class="btn-primary"
        :loading="loading"
        @click="$emit('submit')"
      >
        {{ product.submitLabel }}
      </el-button>
      <el-button size="large" class="btn-secondary" @click="goOrders">
        查询结果
      </el-button>
    </div>

    <el-checkbox
      v-if="product.agreementText"
      v-model="agreed"
      class="agree"
    >
      {{ product.agreementText }}
    </el-checkbox>

    <div
      v-if="recentNotice"
      class="notice-bar"
      :class="{ demo: recentDemo }"
    >
      <span v-if="recentDemo && !useApiEnabled()" class="demo-tag">演示</span>
      {{ recentNotice }}
    </div>

    <FaqSection v-if="product.showFaq !== false" class="compact-faq" />
  </div>
</template>

<style scoped>
.compact-form-shell {
  max-width: 640px;
  margin: 0 auto;
  padding: 28px 32px 32px;
  background: var(--xw-card);
  border-radius: var(--xw-radius-lg);
  border: 1px solid var(--xw-border);
  box-shadow: var(--xw-shadow);
}

.form-title {
  text-align: center;
  font-size: 20px;
  font-weight: 700;
  color: #1e40af;
  margin-bottom: 20px;
  letter-spacing: 0.02em;
}

.form-body {
  margin-bottom: 8px;
}

.form-body :deep(.el-radio-group.compact-radios) {
  display: inline-flex !important;
  column-gap: 72px;
  row-gap: 12px;
}

.form-body :deep(.el-radio-group.compact-radios--loose) {
  column-gap: 96px;
}

.form-body :deep(.compact-radios .el-radio) {
  margin-right: 0 !important;
  margin-left: 0 !important;
  height: auto;
}

.form-body :deep(.compact-radios .el-radio__label) {
  font-size: 14px;
  color: var(--xw-text);
  padding-left: 10px;
}

.action-row {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 24px;
  margin-bottom: 16px;
}

.btn-primary {
  min-width: 160px;
  height: 44px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 10px;
  background: linear-gradient(180deg, #5b9aff, #4a80f0);
  border: none;
  box-shadow: 0 4px 14px rgb(74 128 240 / 35%);
}

.btn-secondary {
  min-width: 160px;
  height: 44px;
  font-size: 16px;
  font-weight: 500;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #93c5fd;
  color: var(--xw-primary);
}

.btn-secondary:hover {
  background: #eff6ff;
  border-color: var(--xw-primary);
  color: var(--xw-primary-dark);
}

.agree {
  display: flex;
  justify-content: center;
  margin: 0 auto;
  max-width: 520px;
}

.agree :deep(.el-checkbox__label) {
  font-size: 12px;
  color: var(--xw-muted);
  line-height: 1.6;
  white-space: normal;
}

.notice-bar {
  margin-top: 14px;
  padding: 10px 14px;
  background: linear-gradient(90deg, #fefce8, #fef9c3);
  border: 1px solid #fde047;
  border-radius: 10px;
  font-size: 13px;
  color: #854d0e;
  text-align: center;
}

.notice-bar.demo {
  background: #f8fafc;
  border-color: #e2e8f0;
  color: #64748b;
}

.demo-tag {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  background: #e2e8f0;
  margin-right: 6px;
}

.compact-faq {
  margin-top: 24px;
}

@media (max-width: 560px) {
  .compact-form-shell {
    padding: 20px 16px 24px;
  }

  .action-row {
    flex-direction: column;
    align-items: stretch;
  }

  .btn-primary,
  .btn-secondary {
    width: 100%;
    min-width: 0;
  }
}
</style>

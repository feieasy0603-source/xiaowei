<script setup lang="ts">
defineProps<{
  label: string
}>()
</script>

<template>
  <div class="form-row">
    <div class="form-row-label">{{ label }}</div>
    <div class="form-row-content">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.form-row {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid #f1f5f9;
}

.form-row:last-child {
  border-bottom: none;
}

.form-row-label {
  flex: 0 0 88px;
  text-align: right;
  font-size: 14px;
  font-weight: 500;
  color: var(--xw-text-secondary);
  line-height: 32px;
  padding-top: 2px;
}

.form-row-content {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 20px 32px;
}

@media (max-width: 560px) {
  .form-row {
    flex-direction: column;
    gap: 8px;
    padding: 12px 0;
  }

  .form-row-label {
    flex: none;
    text-align: left;
    line-height: 1.4;
    padding-top: 0;
  }

  .form-row-content {
    width: 100%;
    gap: 12px;
  }
}
</style>

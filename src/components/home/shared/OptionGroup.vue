<script setup lang="ts">
defineProps<{
  label: string
  variant?: 'default' | 'cards' | 'inline'
}>()
</script>

<template>
  <div
    class="option-group"
    :class="{
      'option-cards': variant === 'cards',
      'option-inline': variant === 'inline',
    }"
  >
    <label class="section-label">{{ label }}</label>
    <slot />
  </div>
</template>

<style scoped>
.option-group {
  margin-bottom: 22px;
}

.section-label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--xw-text-secondary);
  margin-bottom: 12px;
}

/* —— 卡片式单选（字数等） —— */
.option-cards :deep(.el-radio-group) {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  width: 100%;
}

.option-cards :deep(.el-radio) {
  display: block;
  width: 100%;
  height: auto;
  margin: 0 !important;
  white-space: normal;
}

.option-cards :deep(.el-radio__input) {
  display: none;
}

.option-cards :deep(.el-radio__label) {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 44px;
  padding: 12px 36px 12px 14px;
  box-sizing: border-box;
  border: 2px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  font-size: 14px;
  color: var(--xw-text);
  line-height: 1.35;
  transition: all 0.15s;
}

.option-cards :deep(.el-radio__label:hover) {
  border-color: #93c5fd;
}

.option-cards :deep(.el-radio.is-checked .el-radio__label) {
  border-color: var(--xw-primary);
  background: #eff6ff;
  color: var(--xw-primary-dark);
  font-weight: 600;
  box-shadow: 0 0 0 1px rgb(59 130 246 / 20%);
}

.option-cards :deep(.el-radio.is-checked .el-radio__label::after) {
  content: '✓';
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--xw-primary);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

/* —— 行内胶囊（语言等） —— */
.option-inline :deep(.el-radio-group) {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  width: 100%;
}

.option-inline :deep(.el-radio) {
  margin: 0 !important;
  height: auto;
}

.option-inline :deep(.el-radio__input) {
  display: none;
}

.option-inline :deep(.el-radio__label) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 72px;
  padding: 8px 18px;
  border: 2px solid #e2e8f0;
  border-radius: 999px;
  background: #fff;
  font-size: 14px;
  transition: all 0.15s;
}

.option-inline :deep(.el-radio.is-checked .el-radio__label) {
  border-color: var(--xw-primary);
  background: #eff6ff;
  color: var(--xw-primary-dark);
  font-weight: 600;
}

/* —— 默认横排 radio —— */
.option-group:not(.option-cards):not(.option-inline) :deep(.el-radio-group) {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 20px;
}

.option-group:not(.option-cards):not(.option-inline) :deep(.el-radio) {
  margin-right: 0;
  height: auto;
}

@media (max-width: 480px) {
  .option-cards :deep(.el-radio-group) {
    grid-template-columns: 1fr;
  }
}
</style>

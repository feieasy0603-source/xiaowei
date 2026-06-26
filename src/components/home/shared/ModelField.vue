<script setup lang="ts">
import OptionGroup from '@/components/home/shared/OptionGroup.vue'

const model = defineModel<'standard' | 'academia'>('model', { default: 'standard' })
</script>

<template>
  <OptionGroup label="模型选择" variant="cards">
    <el-radio-group v-model="model" class="model-radio-group">
      <el-radio value="standard">标准模型</el-radio>
      <el-radio value="academia" class="radio-academia">
        <span class="model-line">Academia Genius-4.0</span>
        <span class="model-tag">专业性更强</span>
      </el-radio>
    </el-radio-group>
  </OptionGroup>
</template>

<style scoped>
.model-radio-group :deep(.el-radio__label) {
  flex-direction: column;
  gap: 4px;
  padding-top: 10px;
  padding-bottom: 10px;
}

.radio-academia :deep(.el-radio__label) {
  align-items: flex-start;
  justify-content: center;
}

.model-line {
  display: block;
  font-size: 13px;
  line-height: 1.3;
}

.model-tag {
  font-size: 11px;
  color: #ea580c;
  background: #ffedd5;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 600;
}
</style>

<script setup lang="ts">
import { computed } from 'vue'
import { QuestionFilled } from '@element-plus/icons-vue'
import FormRow from '@/components/home/shared/FormRow.vue'
import { usePaperStore } from '@/stores/paper'

const paperStore = usePaperStore()

const model = computed({
  get: () => paperStore.draft?.model ?? 'standard',
  set: (v: 'standard' | 'academia') => {
    paperStore.updateDraft({ model: v })
  },
})
</script>

<template>
  <FormRow label="模型选择">
    <el-radio-group v-model="model" class="compact-radios model-radios">
      <el-radio value="standard">标准模型</el-radio>
      <el-radio value="academia" class="radio-academia">
        Academia Genius-4.0
        <span class="model-tag">专业性更强</span>
        <el-tooltip content="更强的学术表达与结构能力" placement="top">
          <el-icon class="model-help"><QuestionFilled /></el-icon>
        </el-tooltip>
      </el-radio>
    </el-radio-group>
  </FormRow>
</template>

<style scoped>
.model-tag {
  margin-left: 4px;
  font-size: 11px;
  color: #ea580c;
  background: #ffedd5;
  padding: 1px 6px;
  border-radius: 4px;
  vertical-align: middle;
}

.model-help {
  margin-left: 4px;
  font-size: 14px;
  color: var(--xw-muted);
  vertical-align: middle;
}

.radio-academia :deep(.el-radio__label) {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px;
}
</style>

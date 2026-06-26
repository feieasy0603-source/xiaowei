<script setup lang="ts">
import FormRow from '@/components/home/shared/FormRow.vue'
import { usePaperStore } from '@/stores/paper'

const paperStore = usePaperStore()

function syncLanguage(v: 'zh' | 'en' | 'ja') {
  const d = paperStore.draft!
  paperStore.updateDraft({ meta: { ...d.meta, language: v } })
}
</script>

<template>
  <FormRow label="选择语言">
    <el-radio-group
      class="compact-radios compact-radios--loose"
      :model-value="paperStore.draft?.meta.language"
      @update:model-value="syncLanguage"
    >
      <el-radio value="zh">中文</el-radio>
      <el-radio value="en">英文</el-radio>
      <el-radio value="ja">日文</el-radio>
    </el-radio-group>
  </FormRow>
</template>

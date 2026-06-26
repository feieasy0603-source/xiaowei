<script setup lang="ts">
import FormRow from '@/components/home/shared/FormRow.vue'

export type WordCountOption = { value: number; label: string }

const model = defineModel<number>({ required: true })

defineProps<{
  options: WordCountOption[]
}>()
</script>

<template>
  <FormRow label="选择字数">
    <el-radio-group v-model="model" class="compact-radios compact-radios--loose">
      <el-radio v-for="opt in options" :key="opt.value" :value="opt.value">
        {{ opt.label }}
      </el-radio>
    </el-radio-group>
  </FormRow>
</template>

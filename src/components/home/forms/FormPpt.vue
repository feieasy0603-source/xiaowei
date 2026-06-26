<script setup lang="ts">
import { ref } from 'vue'
import CompactFormBase from '@/components/home/shared/compact/CompactFormBase.vue'
import FormRow from '@/components/home/shared/FormRow.vue'
import type { ProductConfig } from '@/types/product'

defineProps<{ product: ProductConfig }>()
const pages = ref(15)
</script>

<template>
  <CompactFormBase :product="product" skip-preview :job-payload="{ pages }">
    <FormRow label="PPT 页数">
      <el-radio-group v-model="pages" class="compact-radios">
        <el-radio :value="10">约10页</el-radio>
        <el-radio :value="15">约15页</el-radio>
        <el-radio :value="20">约20页</el-radio>
        <el-radio :value="25">约25页</el-radio>
      </el-radio-group>
    </FormRow>
  </CompactFormBase>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import FormShell from '@/components/home/shared/FormShell.vue'
import UploadBox from '@/components/home/shared/UploadBox.vue'
import { useProductSubmit } from '@/composables/useProductSubmit'
import { useApiEnabled } from '@/api/http'
import type { ProductConfig } from '@/types/product'

const props = defineProps<{ product: ProductConfig }>()
const fileName = ref('')
const fileId = ref<string>()
const { loading, agreed, recentNotice, submit } = useProductSubmit(() => props.product)

function onSubmit() {
  if (useApiEnabled() && !fileId.value) {
    ElMessage.warning('请先上传文件')
    return
  }
  submit({
    requireTitle: false,
    skipPreview: true,
    jobPayload: fileId.value ? { fileId: fileId.value, fileName: fileName.value } : undefined,
  })
}
</script>

<template>
  <FormShell
    v-model:agreed="agreed"
    :product="product"
    :loading="loading"
    :recent-notice="recentNotice"
    @submit="onSubmit"
  >
    <h2 class="form-inner-title">{{ product.label }}</h2>
    <UploadBox v-model:file-name="fileName" v-model:file-id="fileId" :label="product.titleFieldLabel" />
  </FormShell>
</template>

<style scoped>
.form-inner-title { text-align: center; font-size: 22px; font-weight: 700; margin-bottom: 16px; }
</style>

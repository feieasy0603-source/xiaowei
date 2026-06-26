<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import FormShell from '@/components/home/shared/FormShell.vue'
import UploadBox from '@/components/home/shared/UploadBox.vue'
import ModelField from '@/components/home/shared/ModelField.vue'
import FaqSection from '@/components/home/FaqSection.vue'
import { faqParaphrase } from '@/mocks/menu'
import { useProductSubmit } from '@/composables/useProductSubmit'
import { useApiEnabled } from '@/api/http'
import type { ProductConfig } from '@/types/product'

const props = defineProps<{ product: ProductConfig }>()
const file1 = ref('')
const file2 = ref('')
const fileId1 = ref<string>()
const fileId2 = ref<string>()
const model = ref<'standard' | 'academia'>('standard')
const { loading, agreed, recentNotice, submit } = useProductSubmit(() => props.product)

function onSubmit() {
  if (useApiEnabled() && !fileId1.value) {
    ElMessage.warning('请先上传待降重文档')
    return
  }
  submit({
    requireTitle: false,
    skipPreview: true,
    jobPayload: {
      fileId: fileId1.value,
      fileId2: fileId2.value,
      fileName: file1.value,
      fileName2: file2.value,
      modelType: model.value,
    },
  })
}
</script>

<template>
  <FormShell
    v-model:agreed="agreed"
    :product="{ ...product, showFaq: false }"
    :loading="loading"
    :recent-notice="recentNotice"
    @submit="onSubmit"
  >
    <h2 class="form-inner-title">{{ product.label }}</h2>
    <UploadBox
      v-model:file-name="file1"
      v-model:file-name2="file2"
      v-model:file-id="fileId1"
      v-model:file-id2="fileId2"
      dual
    />
    <ModelField v-model:model="model" />
    <FaqSection :items="faqParaphrase" />
  </FormShell>
</template>

<style scoped>
.form-inner-title { text-align: center; font-size: 22px; font-weight: 700; margin-bottom: 20px; }
</style>

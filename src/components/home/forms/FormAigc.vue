<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import FormShell from '@/components/home/shared/FormShell.vue'
import UploadBox from '@/components/home/shared/UploadBox.vue'
import FaqSection from '@/components/home/FaqSection.vue'
import { useProductSubmit } from '@/composables/useProductSubmit'
import { useApiEnabled } from '@/api/http'
import type { ProductConfig } from '@/types/product'

const props = defineProps<{ product: ProductConfig }>()
const fileName = ref('')
const fileId = ref<string>()
const version = ref('standard')
const { loading, agreed, recentNotice, submit } = useProductSubmit(() => props.product)

function onSubmit() {
  if (useApiEnabled() && !fileId.value) {
    ElMessage.warning('请先上传待检测文档')
    return
  }
  submit({
    requireTitle: false,
    skipPreview: true,
    successMsg: '检测说明已提交',
    jobPayload: {
      fileId: fileId.value,
      fileName: fileName.value,
      checkVersion: version.value,
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
    <p class="section-label">
      | {{ product.titleFieldLabel }}
      <span class="hint">（AI 生成 AIGC 检测说明报告，非第三方查重接口）</span>
    </p>
    <UploadBox
      v-model:file-name="fileName"
      v-model:file-id="fileId"
      hint="仅支持 Word、PDF、TXT 文件，小于 15M"
      accept=".doc,.docx,.pdf,.txt"
    />
    <div class="option-group">
      <label>选择查重版本</label>
      <el-radio-group v-model="version">
        <el-radio value="standard">标准版</el-radio>
        <el-radio value="strict">严格版</el-radio>
      </el-radio-group>
    </div>
    <FaqSection />
  </FormShell>
</template>

<style scoped>
.form-inner-title { text-align: center; font-size: 22px; font-weight: 700; margin-bottom: 16px; }
.section-label { font-size: 15px; font-weight: 600; margin-bottom: 12px; }
.hint { font-weight: 400; color: #64748b; font-size: 13px; }
.option-group { margin-bottom: 16px; }
.option-group label { font-size: 14px; font-weight: 600; }
</style>

<script setup lang="ts">
import { UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import type { UploadFile } from 'element-plus/es/components/upload/index.mjs'
import { uploadFile } from '@/api/modules/files'
import { useApiEnabled } from '@/api/http'

export interface UploadedFileInfo {
  fileId?: string
  fileName: string
  url?: string
}

const props = defineProps<{
  label?: string
  hint?: string
  accept?: string
  dual?: boolean
}>()

const fileName = defineModel<string>('fileName', { default: '' })
const fileName2 = defineModel<string>('fileName2', { default: '' })
const fileId = defineModel<string | undefined>('fileId', { default: undefined })
const fileId2 = defineModel<string | undefined>('fileId2', { default: undefined })

async function onChange(file: UploadFile, slot: 1 | 2 = 1) {
  if (!file.raw) return
  const name = file.name
  if (useApiEnabled()) {
    try {
      const res = await uploadFile(file.raw)
      const id = res.fileId != null ? String(res.fileId) : undefined
      if (slot === 1) {
        fileId.value = id
      } else {
        fileId2.value = id
      }
      if (!id) {
        ElMessage.warning('文件已上传但未返回 fileId')
      }
    } catch (e) {
      ElMessage.warning((e as Error).message)
      return
    }
  }
  if (slot === 1) fileName.value = name
  else fileName2.value = name
  ElMessage.success(`已选择：${name}`)
}
</script>

<template>
  <div class="upload-box">
    <p v-if="label" class="upload-label">{{ label }}</p>
    <div v-if="dual" class="dual-upload">
      <div class="upload-card">
        <el-upload
          :auto-upload="false"
          accept=".pdf,.doc,.docx,.txt"
          :show-file-list="false"
          @change="(f: UploadFile) => onChange(f, 1)"
        >
          <div class="upload-inner">
            <el-icon :size="32" color="#3b82f6"><UploadFilled /></el-icon>
            <p>上传论文或报告原文</p>
            <span class="sub">PDF / Word / TXT（压缩包暂不支持）</span>
          </div>
        </el-upload>
        <p v-if="fileName" class="fname">{{ fileName }}</p>
      </div>
      <div class="upload-card">
        <el-upload
          :auto-upload="false"
          accept=".doc,.docx,.pdf"
          :show-file-list="false"
          @change="(f: UploadFile) => onChange(f, 2)"
        >
          <div class="upload-inner">
            <el-icon :size="32" color="#3b82f6"><UploadFilled /></el-icon>
            <p>上传论文原文</p>
            <span class="sub">Word · PDF</span>
          </div>
        </el-upload>
        <p v-if="fileName2" class="fname">{{ fileName2 }}</p>
      </div>
    </div>
    <el-upload
      v-else
      drag
      :auto-upload="false"
      :accept="accept ?? '.doc,.docx,.pdf'"
      :show-file-list="false"
      @change="(f: UploadFile) => onChange(f, 1)"
    >
      <el-icon class="icon"><UploadFilled /></el-icon>
      <div class="el-upload__text">点击或拖拽文件到此处上传</div>
      <template #tip>
        <div class="el-upload__tip">{{ hint ?? '仅支持 Word、PDF 文件，小于 15M' }}</div>
      </template>
    </el-upload>
    <p v-if="!dual && fileName" class="fname">{{ fileName }}</p>
  </div>
</template>

<style scoped>
.upload-label {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #334155;
}

.upload-label::before {
  content: '* ';
  color: #ef4444;
}

.dual-upload {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

.upload-card {
  border: 1px dashed #93c5fd;
  border-radius: 12px;
  padding: 16px;
  background: #f8fafc;
}

.upload-inner {
  text-align: center;
  padding: 12px;
  cursor: pointer;
}

.upload-inner p {
  font-size: 14px;
  font-weight: 600;
  margin-top: 8px;
  color: #334155;
}

.sub {
  font-size: 12px;
  color: #94a3b8;
}

.fname {
  font-size: 12px;
  color: #3b82f6;
  margin-top: 8px;
}

.icon {
  font-size: 48px;
  color: #3b82f6;
}

@media (max-width: 640px) {
  .dual-upload {
    grid-template-columns: 1fr;
  }
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { adminApiUrl, adminFetch, getAdminToken } from '@/api/http'
import { applyAdminBranding, type AdminSiteBranding } from '@/stores/siteBranding'

interface BrandingDto extends AdminSiteBranding {
  updatedAt?: string
}

const loading = ref(false)
const saving = ref(false)
const uploadingLogo = ref(false)
const uploadingFavicon = ref(false)

const form = ref({
  siteTitle: '小微智能写作',
  slogan: '一站式论文辅助平台',
  documentTitle: '小微智能 AI 论文写作',
  logoText: 'AI',
  logoUrl: '' as string,
  faviconUrl: '' as string,
})

function assetPreview(url?: string | null) {
  if (!url) return ''
  const v = url.trim()
  if (v.startsWith('http://') || v.startsWith('https://')) return v
  if (v.startsWith('/api/')) return v
  if (v.startsWith('/files/')) return adminApiUrl(v)
  return adminApiUrl(`/files/download/${v}`)
}

const logoPreview = computed(() => assetPreview(form.value.logoUrl))
const faviconPreview = computed(() => assetPreview(form.value.faviconUrl))

async function load() {
  loading.value = true
  try {
    const data = await adminFetch<BrandingDto>('/admin/branding-settings')
    form.value = {
      siteTitle: data.siteTitle,
      slogan: data.slogan,
      documentTitle: data.documentTitle,
      logoText: data.logoText,
      logoUrl: data.logoUrl ?? '',
      faviconUrl: data.faviconUrl ?? '',
    }
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const data = await adminFetch<BrandingDto>('/admin/branding-settings', {
      method: 'PUT',
      body: JSON.stringify({
        siteTitle: form.value.siteTitle,
        slogan: form.value.slogan,
        documentTitle: form.value.documentTitle,
        logoText: form.value.logoText,
        logoUrl: form.value.logoUrl.trim() || null,
        faviconUrl: form.value.faviconUrl.trim() || null,
      }),
    })
    applyAdminBranding(data)
    ElMessage.success('站点品牌已保存')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

async function uploadAsset(kind: 'logo' | 'favicon', file: File) {
  const isLogo = kind === 'logo'
  if (isLogo) uploadingLogo.value = true
  else uploadingFavicon.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const token = getAdminToken()
    const res = await fetch(adminApiUrl(`/admin/branding-settings/upload-${kind}`), {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: fd,
    })
    const json = (await res.json()) as { code: number; message: string; data: BrandingDto }
    if (json.code !== 0) throw new Error(json.message || '上传失败')
    form.value.logoUrl = json.data.logoUrl ?? form.value.logoUrl
    form.value.faviconUrl = json.data.faviconUrl ?? form.value.faviconUrl
    applyAdminBranding(json.data)
    ElMessage.success(isLogo ? 'Logo 已上传' : 'Favicon 已上传')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    if (isLogo) uploadingLogo.value = false
    else uploadingFavicon.value = false
  }
}

function onLogoFileChange(file: { raw?: File }) {
  if (file.raw) void uploadAsset('logo', file.raw)
}

function onFaviconFileChange(file: { raw?: File }) {
  if (file.raw) void uploadAsset('favicon', file.raw)
}

function clearLogo() {
  form.value.logoUrl = ''
}

function clearFavicon() {
  form.value.faviconUrl = ''
}

onMounted(() => void load())
</script>

<template>
  <div class="page">
    <h2>站点品牌</h2>
    <p class="desc">配置用户端与管理端的 Logo、标题、副标题与浏览器 Tab 图标（favicon），保存后立即生效。</p>

    <el-card v-loading="loading" shadow="never" class="form-card">
      <el-form label-width="110px" style="max-width: 560px">
        <el-form-item label="站点标题">
          <el-input v-model="form.siteTitle" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="form.slogan" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="浏览器标题">
          <el-input v-model="form.documentTitle" maxlength="64" show-word-limit />
          <p class="hint-inline">显示在浏览器标签页</p>
        </el-form-item>
        <el-form-item label="Logo 文字">
          <el-input v-model="form.logoText" maxlength="8" placeholder="无 Logo 图时显示" />
        </el-form-item>

        <el-form-item label="Logo 图片">
          <div class="upload-row">
            <div class="preview-box">
              <img v-if="logoPreview" :src="logoPreview" alt="logo" class="logo-preview" />
              <span v-else class="preview-fallback">{{ form.logoText || 'AI' }}</span>
            </div>
            <div class="upload-actions">
              <el-upload
                :auto-upload="false"
                :show-file-list="false"
                accept=".png,.jpg,.jpeg,.gif,.webp,.svg"
                @change="onLogoFileChange"
              >
                <el-button :loading="uploadingLogo">上传 Logo</el-button>
              </el-upload>
              <el-button v-if="form.logoUrl" link type="danger" @click="clearLogo">清除</el-button>
              <el-input
                v-model="form.logoUrl"
                placeholder="或粘贴图片 URL / 存储路径"
                class="url-input"
              />
            </div>
          </div>
        </el-form-item>

        <el-form-item label="Favicon">
          <div class="upload-row">
            <div class="preview-box favicon-box">
              <img v-if="faviconPreview" :src="faviconPreview" alt="favicon" class="favicon-preview" />
              <span v-else class="preview-fallback small">ico</span>
            </div>
            <div class="upload-actions">
              <el-upload
                :auto-upload="false"
                :show-file-list="false"
                accept=".png,.jpg,.jpeg,.gif,.webp,.svg,.ico"
                @change="onFaviconFileChange"
              >
                <el-button :loading="uploadingFavicon">上传 Favicon</el-button>
              </el-upload>
              <el-button v-if="form.faviconUrl" link type="danger" @click="clearFavicon">清除</el-button>
              <el-input
                v-model="form.faviconUrl"
                placeholder="或粘贴图标 URL"
                class="url-input"
              />
            </div>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  padding: 20px;
}

.desc {
  color: #64748b;
  font-size: 14px;
  margin: 0 0 16px;
  max-width: 640px;
  line-height: 1.55;
}

.form-card {
  max-width: 720px;
}

.hint-inline {
  margin: 4px 0 0;
  font-size: 12px;
  color: #94a3b8;
}

.upload-row {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  width: 100%;
}

.preview-box {
  width: 56px;
  height: 56px;
  border-radius: 10px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.favicon-box {
  width: 40px;
  height: 40px;
}

.logo-preview {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.favicon-preview {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.preview-fallback {
  font-weight: 800;
  color: #2563eb;
  font-size: 14px;
}

.preview-fallback.small {
  font-size: 11px;
  color: #64748b;
}

.upload-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.url-input {
  width: 100%;
}
</style>

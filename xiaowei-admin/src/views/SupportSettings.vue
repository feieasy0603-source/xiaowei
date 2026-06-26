<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { adminFetch } from '@/api/http'

interface SupportDto {
  enabled: boolean
  title: string
  workHours: string
  phone?: string | null
  email?: string | null
  wechatId?: string | null
  qq?: string | null
  externalUrl?: string | null
  note?: string | null
  updatedAt?: string
}

const loading = ref(false)
const saving = ref(false)
const form = ref<SupportDto>({
  enabled: true,
  title: '在线客服',
  workHours: '工作日 9:00–18:00',
  phone: '',
  email: '',
  wechatId: '',
  qq: '',
  externalUrl: '',
  note: '',
})

async function load() {
  loading.value = true
  try {
    const data = await adminFetch<SupportDto>('/admin/support-settings')
    form.value = {
      enabled: data.enabled !== false,
      title: data.title ?? '在线客服',
      workHours: data.workHours ?? '',
      phone: data.phone ?? '',
      email: data.email ?? '',
      wechatId: data.wechatId ?? '',
      qq: data.qq ?? '',
      externalUrl: data.externalUrl ?? '',
      note: data.note ?? '',
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
    await adminFetch('/admin/support-settings', {
      method: 'PUT',
      body: JSON.stringify({
        enabled: form.value.enabled,
        title: form.value.title,
        workHours: form.value.workHours,
        phone: form.value.phone?.trim() || null,
        email: form.value.email?.trim() || null,
        wechatId: form.value.wechatId?.trim() || null,
        qq: form.value.qq?.trim() || null,
        externalUrl: form.value.externalUrl?.trim() || null,
        note: form.value.note?.trim() || null,
      }),
    })
    ElMessage.success('客服信息已保存，用户端立即生效')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

onMounted(() => void load())
</script>

<template>
  <div class="page">
    <h2>在线客服</h2>
    <p class="desc">配置用户点击「客服」按钮时展示的电话、微信、外链等，前台浮动按钮与顶栏共用。</p>

    <el-card v-loading="loading" shadow="never" class="form-card">
      <el-form label-width="100px" style="max-width: 520px">
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="form.title" maxlength="64" />
        </el-form-item>
        <el-form-item label="服务时间">
          <el-input v-model="form.workHours" placeholder="工作日 9:00–18:00" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="form.phone" placeholder="400-xxxx-xxxx" />
        </el-form-item>
        <el-form-item label="微信号">
          <el-input v-model="form.wechatId" placeholder="用户添加微信咨询" />
        </el-form-item>
        <el-form-item label="QQ">
          <el-input v-model="form.qq" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="外链">
          <el-input
            v-model="form.externalUrl"
            placeholder="企业微信、在线客服系统链接等"
          />
        </el-form-item>
        <el-form-item label="补充说明">
          <el-input v-model="form.note" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
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
  max-width: 560px;
}

.form-card {
  max-width: 640px;
}
</style>

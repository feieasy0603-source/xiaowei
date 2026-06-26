<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { adminFetch, setAdminToken } from '@/api/http'
import { useAdminBranding } from '@/stores/siteBranding'

const router = useRouter()
const route = useRoute()
const brandingStore = useAdminBranding()
const username = ref('admin')
const password = ref(import.meta.env.PROD ? '' : 'admin123')
const loading = ref(false)

async function login() {
  loading.value = true
  try {
    const data = await adminFetch<{ token: string }>('/auth/admin/login', {
      method: 'POST',
      skipAuthRedirect: true,
      body: JSON.stringify({ username: username.value, password: password.value }),
    })
    setAdminToken(data.token)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.push(redirect.startsWith('/') ? redirect : '/')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-bg" />
    <div class="login-panel">
      <div class="login-brand">
        <span class="logo" :class="{ 'logo--image': brandingStore.logoImageUrl.value }">
          <img
            v-if="brandingStore.logoImageUrl.value"
            :src="brandingStore.logoImageUrl.value"
            :alt="brandingStore.siteTitle.value"
          />
          <span v-else>{{ brandingStore.logoText.value }}</span>
        </span>
        <h1>{{ brandingStore.siteTitle.value }}</h1>
        <p>{{ brandingStore.slogan.value }} · 管理平台</p>
      </div>
      <el-card class="login-card" shadow="always">
        <h2>管理员登录</h2>
        <el-form label-position="top" @submit.prevent="login">
          <el-form-item label="账号">
            <el-input v-model="username" size="large" placeholder="admin" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="password" type="password" size="large" show-password @keyup.enter="login" />
          </el-form-item>
          <el-button type="primary" size="large" :loading="loading" style="width: 100%" @click="login">
            进入管理端
          </el-button>
        </el-form>
        <p class="hint">管理 AI 生成任务、文献库、提纲模板与用户充值</p>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}
.login-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #0f172a 0%, #1e3a5f 45%, #312e81 100%);
}
.login-bg::after {
  content: '';
  position: absolute;
  width: 480px;
  height: 480px;
  border-radius: 50%;
  background: radial-gradient(circle, rgb(59 130 246 / 25%), transparent 70%);
  top: -120px;
  right: -80px;
}
.login-panel {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 48px;
  align-items: center;
  padding: 24px;
}
.login-brand {
  color: #f8fafc;
  max-width: 280px;
}
.login-brand .logo {
  display: inline-flex;
  width: 48px;
  height: 48px;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  font-size: 22px;
  font-weight: 800;
  margin-bottom: 16px;
  overflow: hidden;
}
.login-brand .logo--image {
  padding: 4px;
  background: #fff;
}
.login-brand .logo img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}
.login-brand h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
}
.login-brand p {
  margin: 10px 0 0;
  color: #94a3b8;
  font-size: 15px;
  line-height: 1.5;
}
.login-card {
  width: 400px;
  padding: 8px 4px 4px;
  border-radius: 16px;
}
.login-card h2 {
  margin: 0 0 20px;
  font-size: 18px;
  text-align: center;
  color: #0f172a;
}
.hint {
  margin: 16px 0 0;
  font-size: 12px;
  color: #94a3b8;
  text-align: center;
}
@media (max-width: 768px) {
  .login-panel {
    flex-direction: column;
  }
  .login-brand {
    text-align: center;
  }
}
</style>

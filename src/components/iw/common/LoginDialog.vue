<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { fetchMe, login, register } from '@/api/modules/auth'
import { saveUserPreferences } from '@/composables/useUserPreferences'
import type { UserMe } from '@/api/modules/auth'
import { getToken, useApiEnabled } from '@/api/http'
import { consumeInviteCode, getStoredInviteCode } from '@/composables/useReferralCode'
import { useAppStore } from '@/stores/app'
import { usePaperStore } from '@/stores/paper'

const PHONE_RE = /^1[3-9]\d{9}$/
const showDevTools = import.meta.env.DEV
const showOfflineDemo = showDevTools && !useApiEnabled()

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const paperStore = usePaperStore()
const activeTab = ref('login')
const submitting = ref(false)

const phone = ref(showDevTools ? '13800138000' : '')
const password = ref(showDevTools ? 'demo123' : '')
const regPhone = ref('')
const regPassword = ref('')
const regConfirm = ref('')
const regNickname = ref('')
const pendingInvite = ref(getStoredInviteCode())

watch(
  () => appStore.loginDialogVisible,
  (open) => {
    if (open) pendingInvite.value = getStoredInviteCode()
  },
)

const dialogTitle = computed(() =>
  appStore.authDialogMode === 'register' ? '注册' : '登录',
)

watch(
  () => appStore.authDialogMode,
  (mode) => {
    activeTab.value = mode === 'register' ? 'register' : 'login'
  },
  { immediate: true },
)

watch(
  () => appStore.loginDialogVisible,
  (visible) => {
    if (visible) {
      activeTab.value = appStore.authDialogMode === 'register' ? 'register' : 'login'
    }
  },
)

function switchToRegister() {
  appStore.authDialogMode = 'register'
  activeTab.value = 'register'
}

function switchToLogin() {
  appStore.authDialogMode = 'login'
  activeTab.value = 'login'
}

function syncPrefsFromMe(me: UserMe) {
  const p = me.preferences
  if (!p || typeof p !== 'object') return
  saveUserPreferences({
    degree: p.degree,
    wordCount: p.wordCount,
    model: p.model as 'standard' | 'academia' | undefined,
    schoolId: p.schoolId,
    category: p.category,
    language: p.language as 'zh' | 'en' | 'ja' | undefined,
  })
}

async function afterAuth() {
  await appStore.refreshProfile()
  if (useApiEnabled() && getToken()) {
    try {
      syncPrefsFromMe(await fetchMe())
    } catch {
      /* ignore */
    }
  }
  const newLunwen = await paperStore.ensureServerPaper()
  if (newLunwen && route.name === 'intelligentWriting') {
    await router.replace({
      name: 'intelligentWriting',
      params: route.params as { step: string },
      query: { ...route.query, lunwen: newLunwen },
    })
  }
}

async function onDemoLogin() {
  if (!showOfflineDemo) return
  appStore.mockLogin()
  ElMessage.success('已进入演示模式')
}

async function onPasswordLogin() {
  const p = phone.value.trim()
  if (!p || !password.value) {
    ElMessage.warning('请输入手机号和密码')
    return
  }
  if (!PHONE_RE.test(p)) {
    ElMessage.warning('手机号格式不正确')
    return
  }
  if (!useApiEnabled()) {
    if (showDevTools) {
      appStore.mockLogin()
      ElMessage.success('已进入演示模式')
    } else {
      ElMessage.warning('当前未连接后端，无法登录')
    }
    return
  }
  try {
    submitting.value = true
    await login(p, password.value)
    await afterAuth()
    ElMessage.success('登录成功')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    submitting.value = false
  }
}

async function onRegister() {
  const p = regPhone.value.trim()
  if (!PHONE_RE.test(p)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  if (regPassword.value.length < 6 || regPassword.value.length > 32) {
    ElMessage.warning('密码长度为 6-32 位')
    return
  }
  if (regPassword.value !== regConfirm.value) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (!useApiEnabled()) {
    ElMessage.warning('当前未连接后端，无法注册')
    return
  }
  try {
    submitting.value = true
    const inviteCode = getStoredInviteCode() || undefined
    const auth = await register(
      p,
      regPassword.value,
      regConfirm.value,
      regNickname.value.trim() || undefined,
      inviteCode,
    )
    consumeInviteCode()
    pendingInvite.value = ''
    await afterAuth()
    if (auth.inviteApplied && auth.inviteeReward != null && Number(auth.inviteeReward) > 0) {
      ElMessage.success(`注册成功，已获赠 ¥${Number(auth.inviteeReward).toFixed(2)} 邀请礼`)
    } else if (auth.inviteApplied) {
      ElMessage.success('注册成功，已通过好友邀请完成注册')
    } else if (auth.inviteInvalid && inviteCode) {
      ElMessage.warning('邀请码无效，已正常完成注册')
    } else {
      ElMessage.success('注册成功，已自动登录')
    }
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    submitting.value = false
  }
}

function onTabChange(name: string | number) {
  if (name === 'register') appStore.authDialogMode = 'register'
  else if (name === 'login') appStore.authDialogMode = 'login'
}
</script>

<template>
  <el-dialog
    v-model="appStore.loginDialogVisible"
    :title="dialogTitle"
    width="440px"
    destroy-on-close
    @closed="appStore.authDialogMode = 'login'"
  >
    <el-alert
      v-if="pendingInvite && activeTab === 'register'"
      type="success"
      :closable="false"
      show-icon
      class="invite-alert"
      title="好友邀请"
      :description="`您通过邀请链接注册，邀请码：${pendingInvite}`"
    />
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="密码登录" name="login">
        <el-form label-width="80px" class="auth-form">
          <el-form-item label="手机号">
            <el-input v-model="phone" placeholder="请输入手机号" maxlength="11" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="password"
              type="password"
              placeholder="请输入密码"
              show-password
              @keyup.enter="onPasswordLogin"
            />
          </el-form-item>
        </el-form>
        <p class="switch-hint">
          没有账号？
          <button type="button" class="link-btn" @click="switchToRegister">立即注册</button>
        </p>
      </el-tab-pane>

      <el-tab-pane label="注册账号" name="register">
        <el-form label-width="80px" class="auth-form">
          <el-form-item label="手机号">
            <el-input v-model="regPhone" placeholder="11 位手机号" maxlength="11" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="regPassword"
              type="password"
              placeholder="6-32 位密码"
              show-password
              maxlength="32"
            />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input
              v-model="regConfirm"
              type="password"
              placeholder="再次输入密码"
              show-password
              maxlength="32"
              @keyup.enter="onRegister"
            />
          </el-form-item>
          <el-form-item label="昵称">
            <el-input
              v-model="regNickname"
              placeholder="选填，默认可用手机号"
              maxlength="32"
            />
          </el-form-item>
        </el-form>
        <p class="switch-hint">
          已有账号？
          <button type="button" class="link-btn" @click="switchToLogin">去登录</button>
        </p>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="appStore.loginDialogVisible = false">取消</el-button>
      <template v-if="activeTab === 'register'">
        <el-button type="primary" :loading="submitting" @click="onRegister">
          注册并登录
        </el-button>
      </template>
      <template v-else-if="activeTab === 'login'">
        <el-button v-if="showOfflineDemo" :loading="submitting" @click="onDemoLogin">
          演示登录
        </el-button>
        <el-button type="primary" :loading="submitting" @click="onPasswordLogin">
          登录
        </el-button>
      </template>
    </template>
  </el-dialog>
</template>

<style scoped>
.invite-alert {
  margin-bottom: 12px;
}

.auth-form {
  margin-top: 8px;
}

.qr-placeholder {
  height: 180px;
  background: #f3f4f6;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--xw-muted);
}

.switch-hint {
  margin-top: 4px;
  font-size: 13px;
  color: var(--xw-text-secondary);
}

.link-btn {
  border: none;
  background: none;
  color: var(--xw-primary);
  cursor: pointer;
  font-size: 13px;
  padding: 0;
}

.link-btn:hover {
  text-decoration: underline;
}
</style>

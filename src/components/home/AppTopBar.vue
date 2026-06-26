<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import { updateProfile } from '@/api/modules/auth'
import { apiModeLabel, useApiEnabled } from '@/api/http'
import RechargeDialog from '@/components/iw/common/RechargeDialog.vue'
import VipDialog from '@/components/iw/common/VipDialog.vue'
import { useCustomerService } from '@/composables/useCustomerService'
import { useAppStore } from '@/stores/app'
import { useSiteBrandingStore } from '@/stores/siteBranding'

const appStore = useAppStore()
const brandingStore = useSiteBrandingStore()
const router = useRouter()
const { open: openCustomerService } = useCustomerService()
const showDevModeTag = import.meta.env.DEV
const apiMode = computed(() => useApiEnabled())
const modeTag = computed(() =>
  apiMode.value ? '已连接后端' : '离线演示',
)
const rechargeVisible = ref(false)
const vipVisible = ref(false)

onMounted(() => {
  if (appStore.isLoggedIn && useApiEnabled()) {
    void appStore.refreshProfile()
  }
})

const displayName = computed(() => {
  const n = appStore.nickname.trim()
  if (!n) return '用户'
  return n.length > 8 ? `${n.slice(0, 8)}…` : n
})

const avatarLetter = computed(() => {
  const n = appStore.nickname.trim()
  if (!n) return '用'
  return n.charAt(0).toUpperCase()
})

const isVip = computed(() => appStore.vipLevel > 0)

/** 顶栏紧凑金额：大额用「万」避免撑破布局 */
const formattedBalance = computed(() => {
  const v = appStore.balance
  if (v >= 1_000_000) {
    const wan = v / 10_000
    return wan >= 100 ? `${Math.round(wan)}万` : `${wan.toFixed(1)}万`
  }
  if (v >= 10_000) {
    return `${(v / 10_000).toFixed(2)}万`
  }
  return v.toFixed(2)
})

function onRecharge() {
  if (!appStore.isLoggedIn) {
    appStore.openLogin()
    return
  }
  rechargeVisible.value = true
}

function onUserMenu(cmd: string) {
  if (cmd === 'nickname') void onEditNickname()
  else if (cmd === 'settings') router.push({ name: 'settings' })
  else if (cmd === 'account') router.push({ name: 'account' })
  else if (cmd === 'logout') appStore.logout()
}

async function onEditNickname() {
  if (!appStore.isLoggedIn) {
    appStore.openLogin()
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('修改显示昵称', '个人资料', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValue: appStore.nickname,
      inputPattern: /^.{1,20}$/,
      inputErrorMessage: '昵称 1-20 个字符',
    })
    const nick = value?.trim()
    if (!nick) return
    if (useApiEnabled()) {
      await updateProfile({ nickname: nick })
    }
    appStore.nickname = nick
    ElMessage.success('昵称已更新')
  } catch {
    /* 取消 */
  }
}

function onVip() {
  if (isVip.value) {
    ElMessage.info('您已是 VIP 会员')
    return
  }
  if (!appStore.isLoggedIn) {
    appStore.openLogin()
    return
  }
  vipVisible.value = true
}
</script>

<template>
  <header class="top-bar">
    <div class="top-bar-inner">
      <div class="brand-row">
        <span v-if="brandingStore.logoImageUrl" class="brand-mark brand-mark-img">
          <img :src="brandingStore.logoImageUrl" :alt="brandingStore.siteTitle" />
        </span>
        <span v-else class="brand-mark">{{ brandingStore.logoText }}</span>
        <div class="brand-text">
          <span class="brand-name">{{ brandingStore.siteTitle }}</span>
          <span class="slogan">{{ brandingStore.slogan }}</span>
        </div>
        <span
          v-if="showDevModeTag"
          class="mode-tag"
          :class="apiModeLabel()"
        >
          {{ modeTag }}
        </span>
      </div>

      <div v-if="!appStore.isLoggedIn" class="top-actions guest">
        <button type="button" class="top-btn ghost" @click="openCustomerService">客服</button>
        <div class="auth-group">
          <button type="button" class="top-btn ghost" @click="appStore.openRegister()">
            注册
          </button>
          <button type="button" class="top-btn solid" @click="appStore.openLogin()">
            登录
          </button>
        </div>
      </div>

      <div v-else class="member-toolbar">
        <el-dropdown trigger="click" @command="onUserMenu">
          <div class="user-card" role="button" tabindex="0">
            <span class="avatar" aria-hidden="true">{{ avatarLetter }}</span>
            <div class="user-meta">
              <span class="user-name">{{ displayName }}</span>
              <span class="balance-row">
                <span class="balance-label">余额</span>
                <span class="balance-value">¥{{ formattedBalance }}</span>
                <span v-if="isVip" class="vip-badge">VIP{{ appStore.vipLevel }}</span>
              </span>
            </div>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="nickname">修改昵称</el-dropdown-item>
              <el-dropdown-item command="settings">设置</el-dropdown-item>
              <el-dropdown-item command="account">用户中心</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <div class="member-divider" aria-hidden="true" />

        <div class="member-actions">
          <button type="button" class="top-btn ghost-light" @click="openCustomerService">
            客服
          </button>
          <button type="button" class="top-btn btn-recharge" @click="onRecharge">
            充值
          </button>
          <button
            v-if="!isVip"
            type="button"
            class="top-btn btn-vip"
            @click="onVip"
          >
            开通 VIP
          </button>
        </div>
      </div>
    </div>
  </header>

  <RechargeDialog v-model:visible="rechargeVisible" />
  <VipDialog v-model:visible="vipVisible" />
</template>

<style scoped>
.top-bar {
  background: var(--xw-gradient);
  color: #fff;
  padding: 0 20px;
  height: var(--xw-topbar-height);
  box-shadow: 0 2px 12px rgb(37 99 235 / 18%);
}

.top-bar-inner {
  height: 100%;
  max-width: 1280px;
  margin: 0 auto;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.brand-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}

.brand-mark {
  width: 32px;
  height: 32px;
  background: rgb(255 255 255 / 95%);
  color: var(--xw-primary-dark);
  border-radius: 8px;
  font-size: 11px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.brand-mark-img {
  padding: 2px;
  background: rgb(255 255 255 / 95%);
}

.brand-mark-img img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.brand-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.brand-name {
  font-size: 15px;
  font-weight: 700;
  white-space: nowrap;
  line-height: 1.25;
}

.slogan {
  font-size: 11px;
  opacity: 0.92;
  white-space: nowrap;
  line-height: 1.3;
}

.mode-tag {
  flex-shrink: 0;
  margin-left: 4px;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  background: rgb(255 255 255 / 18%);
  border: 1px solid rgb(255 255 255 / 35%);
  white-space: nowrap;
}

.mode-tag.api {
  background: rgb(34 197 94 / 25%);
  border-color: rgb(134 239 172 / 50%);
}

.mode-tag.offline {
  background: rgb(251 191 36 / 20%);
  border-color: rgb(253 224 71 / 45%);
}

/* —— 未登录 —— */
.top-actions.guest .auth-group {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 4px;
  border-radius: 10px;
  background: rgb(0 0 0 / 8%);
  border: 1px solid rgb(255 255 255 / 12%);
}

/* —— 已登录：统一工具条 —— */
.member-toolbar {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding: 4px 6px 4px 4px;
  border-radius: 10px;
  background: rgb(0 0 0 / 10%);
  border: 1px solid rgb(255 255 255 / 14%);
  flex-shrink: 1;
  min-width: 0;
  max-width: min(100%, 420px);
}

.user-card {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 2px 4px 2px 2px;
  cursor: pointer;
  border-radius: 8px;
  transition: background 0.15s;
}

.user-card:hover {
  background: rgb(255 255 255 / 12%);
}

.avatar {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  background: rgb(255 255 255 / 22%);
  border: 1px solid rgb(255 255 255 / 30%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.user-meta {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  line-height: 1.2;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100px;
}

.balance-row {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  opacity: 0.92;
}

.balance-label {
  opacity: 0.75;
  flex-shrink: 0;
}

.balance-value {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}

.vip-badge {
  font-size: 9px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 3px;
  background: linear-gradient(135deg, rgb(251 191 36 / 45%), rgb(245 158 11 / 35%));
  border: 1px solid rgb(253 224 71 / 55%);
  color: #fffbeb;
  flex-shrink: 0;
}

.member-divider {
  width: 1px;
  height: 24px;
  background: rgb(255 255 255 / 20%);
  flex-shrink: 0;
}

.member-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  padding-right: 2px;
}

.top-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 28px;
  padding: 0 12px;
  font-size: 12px;
  border-radius: 7px;
  cursor: pointer;
  white-space: nowrap;
  border: none;
  transition: background 0.15s, color 0.15s, opacity 0.15s;
}

.top-actions.guest .top-btn.ghost {
  border: 1px solid rgb(255 255 255 / 38%);
  background: transparent;
  color: #fff;
  height: 32px;
  padding: 0 14px;
  font-size: 13px;
  border-radius: 8px;
  min-width: 72px;
}

.top-actions.guest .top-btn.ghost:hover {
  background: rgb(255 255 255 / 14%);
}

.top-actions.guest .top-btn.solid {
  border: 1px solid #fff;
  background: #fff;
  color: var(--xw-primary-dark);
  font-weight: 600;
  height: 32px;
  padding: 0 14px;
  font-size: 13px;
  border-radius: 8px;
  min-width: 72px;
  box-shadow: 0 2px 10px rgb(15 23 42 / 12%);
}

.top-actions.guest .top-btn.solid:hover {
  background: rgb(255 255 255 / 94%);
}

.btn-recharge {
  background: #fff;
  color: var(--xw-primary-dark);
  font-weight: 600;
  padding: 0 14px;
}

.btn-recharge:hover {
  background: rgb(255 255 255 / 92%);
}

.btn-vip {
  background: rgb(251 191 36 / 25%);
  color: #fff;
  border: 1px solid rgb(253 224 71 / 45%);
  font-weight: 500;
}

.btn-vip:hover {
  background: rgb(251 191 36 / 38%);
}

.ghost-light {
  background: transparent;
  color: rgb(255 255 255 / 92%);
  border: 1px solid rgb(255 255 255 / 35%);
  padding: 0 10px;
  font-weight: 500;
}

.ghost-light:hover {
  background: rgb(255 255 255 / 12%);
}

.btn-logout {
  background: transparent;
  color: rgb(255 255 255 / 88%);
  padding: 0 8px;
  font-weight: 400;
}

.btn-logout:hover {
  color: #fff;
  background: rgb(255 255 255 / 10%);
}

@media (max-width: 900px) {
  .slogan {
    display: none;
  }
}

@media (max-width: 720px) {
  .member-toolbar {
    gap: 8px;
    padding: 4px;
  }

  .user-name {
    max-width: 72px;
  }

  .btn-vip {
    display: none;
  }

  .btn-recharge {
    padding: 0 10px;
  }
}

@media (max-width: 480px) {
  .top-bar {
    padding: 0 12px;
  }

  .top-actions.guest .auth-group {
    gap: 6px;
    padding: 3px;
  }

  .top-actions.guest .top-btn {
    min-width: 64px;
    padding: 0 12px;
    font-size: 12px;
  }

  .balance-label {
    display: none;
  }

  .user-meta {
    max-width: 88px;
  }
}
</style>

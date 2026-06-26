import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchMe, logout as clearAuthToken } from '@/api/modules/auth'
import { getToken } from '@/api/http'

const DCODE_KEY = 'xiaowei_dcode'
const MENU_KEY = 'xiaowei_active_menu'

export type AuthDialogMode = 'login' | 'register'

export const useAppStore = defineStore('app', () => {
  const dCode = ref(localStorage.getItem(DCODE_KEY) ?? '')
  const activeMenuId = ref(localStorage.getItem(MENU_KEY) ?? 'graduation')
  /** 专业版提纲页：仅切换右侧内容，不整页跳转 */
  const proEditionOpen = ref(false)
  const wizardOpen = ref(false)
  const balance = ref(0)
  const vipLevel = ref(0)
  const nickname = ref('')
  const isLoggedIn = ref(false)
  const loginDialogVisible = ref(false)
  const authDialogMode = ref<AuthDialogMode>('login')

  function setActiveMenu(id: string) {
    activeMenuId.value = id
    localStorage.setItem(MENU_KEY, id)
  }

  function openProEdition() {
    proEditionOpen.value = true
  }

  function closeProEdition() {
    proEditionOpen.value = false
  }

  function openWizard() {
    wizardOpen.value = true
    proEditionOpen.value = false
  }

  function closeWizard() {
    wizardOpen.value = false
  }

  function setDCode(code: string) {
    dCode.value = code
    if (code) {
      localStorage.setItem(DCODE_KEY, code)
    } else {
      localStorage.removeItem(DCODE_KEY)
    }
  }

  function openLogin() {
    authDialogMode.value = 'login'
    loginDialogVisible.value = true
  }

  function openRegister() {
    authDialogMode.value = 'register'
    loginDialogVisible.value = true
  }

  function mockLogin() {
    isLoggedIn.value = true
    loginDialogVisible.value = false
    balance.value = 100
    nickname.value = '演示用户'
    vipLevel.value = 1
  }

  function applyAuth(data: { balance?: number; nickname?: string; vipLevel?: number }) {
    isLoggedIn.value = true
    loginDialogVisible.value = false
    if (data.balance != null) balance.value = Number(data.balance)
    if (data.vipLevel != null) vipLevel.value = Number(data.vipLevel)
    if (data.nickname != null) nickname.value = data.nickname
  }

  function logout() {
    clearAuthToken()
    isLoggedIn.value = false
    balance.value = 0
    vipLevel.value = 0
    nickname.value = ''
  }

  async function refreshProfile(): Promise<boolean> {
    if (!getToken()) {
      isLoggedIn.value = false
      return false
    }
    try {
      const me = await fetchMe()
      applyAuth({
        balance: me.balance,
        nickname: me.nickname,
        vipLevel: me.vipLevel,
      })
      return true
    } catch {
      logout()
      return false
    }
  }

  return {
    dCode,
    activeMenuId,
    proEditionOpen,
    wizardOpen,
    balance,
    vipLevel,
    nickname,
    isLoggedIn,
    loginDialogVisible,
    authDialogMode,
    setActiveMenu,
    openProEdition,
    closeProEdition,
    openWizard,
    closeWizard,
    setDCode,
    openLogin,
    openRegister,
    mockLogin,
    applyAuth,
    refreshProfile,
    logout,
  }
})

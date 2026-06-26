import { getToken } from '@/api/http'
import { useApiEnabled } from '@/api/http'
import { useAppStore } from '@/stores/app'

/** API 模式下要求已登录，未登录则弹出登录框 */
export async function requireLogin(): Promise<boolean> {
  if (!useApiEnabled()) return true
  const appStore = useAppStore()
  const token = getToken()
  if (token && !appStore.isLoggedIn) {
    const ok = await appStore.refreshProfile()
    if (!ok) {
      appStore.openLogin()
      return false
    }
  }
  if (appStore.isLoggedIn || getToken()) return true
  appStore.openLogin()
  return false
}

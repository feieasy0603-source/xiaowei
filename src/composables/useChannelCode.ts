import { useAppStore } from '@/stores/app'
import { resolveChannel } from '@/api/modules/channels'
import { useApiEnabled } from '@/api/http'

/** 校验并写入渠道码（路由、分享链接等） */
export async function validateAndSetDCode(code: string): Promise<void> {
  const appStore = useAppStore()
  const trimmed = code.trim()
  if (!trimmed) {
    appStore.setDCode('')
    return
  }
  if (!useApiEnabled()) {
    appStore.setDCode(trimmed)
    return
  }
  try {
    const info = await resolveChannel(trimmed)
    appStore.setDCode(info.valid ? trimmed : '')
  } catch {
    appStore.setDCode('')
  }
}

/** 从 URL 读取 dCode 并校验渠道是否有效 */
export async function initChannelFromUrl(): Promise<void> {
  const appStore = useAppStore()
  const params = new URLSearchParams(window.location.search)
  const fromUrl = params.get('dCode')?.trim()
  const code = fromUrl || appStore.dCode?.trim()
  if (!code) return

  if (!useApiEnabled()) {
    appStore.setDCode(code)
    return
  }

  try {
    const info = await resolveChannel(code)
    if (info.valid) {
      appStore.setDCode(code)
    } else {
      appStore.setDCode('')
    }
  } catch {
    if (fromUrl) appStore.setDCode('')
  }
}

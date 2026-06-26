import { apiFetch } from '@/api/http'

export interface ChannelInfo {
  valid: boolean
  dCode?: string
  name?: string
}

export async function resolveChannel(dCode: string): Promise<ChannelInfo> {
  return apiFetch<ChannelInfo>(`/channels/resolve?dCode=${encodeURIComponent(dCode)}`)
}

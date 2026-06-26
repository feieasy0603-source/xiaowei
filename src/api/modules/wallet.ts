import { apiFetch } from '@/api/http'

export interface QuotaSummaryItem {
  taskType: string
  dailyFree: number
  usedToday: number
  freeRemaining: number
  discountPercent: number
}

export interface WalletInfo {
  balance: number
  vipLevel: number
}

export async function fetchWallet(): Promise<WalletInfo> {
  return apiFetch<WalletInfo>('/wallet')
}

export async function fetchWalletQuota(): Promise<QuotaSummaryItem[]> {
  return apiFetch<QuotaSummaryItem[]>('/wallet/quota')
}

export async function rechargeWallet(amount: number): Promise<WalletInfo> {
  return apiFetch<WalletInfo>('/wallet/recharge', {
    method: 'POST',
    body: JSON.stringify({ amount }),
  })
}

export interface WalletRechargePrepay {
  orderNo: string
  amount: number
  qrContent: string
  mock?: boolean
  method?: 'wechat' | 'alipay'
}

export type WalletPayChannel = 'wechat' | 'alipay'

export async function redeemGiftCode(code: string) {
  return apiFetch<{ balance: number; giftAmount?: number; message?: string }>(
    '/wallet/redeem-gift',
    { method: 'POST', body: JSON.stringify({ code }) },
  )
}

export async function prepayWalletRecharge(
  amount: number,
  method: WalletPayChannel = 'wechat',
): Promise<WalletRechargePrepay> {
  return apiFetch<WalletRechargePrepay>('/wallet/recharge/prepay', {
    method: 'POST',
    body: JSON.stringify({ amount, method }),
  })
}

export async function confirmWalletRechargeMock(orderNo: string): Promise<WalletInfo> {
  return apiFetch<WalletInfo>('/wallet/recharge/confirm-mock', {
    method: 'POST',
    body: JSON.stringify({ orderNo }),
  })
}

export interface WalletLogItem {
  id: number
  type: string
  amount: number
  balanceAfter: number
  refType?: string
  refId?: string
  remark?: string
  createdAt?: string
}

export interface WalletLogsPage {
  items: WalletLogItem[]
  page: number
  size: number
  total: number
}

export async function fetchWalletLogs(page = 0, size = 20): Promise<WalletLogsPage> {
  return apiFetch<WalletLogsPage>(`/wallet/logs?page=${page}&size=${size}`)
}

export async function fetchWalletRechargeStatus(
  orderNo: string,
): Promise<{ orderNo: string; status: string }> {
  return apiFetch(`/wallet/recharge/status?orderNo=${encodeURIComponent(orderNo)}`)
}

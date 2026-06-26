import { apiFetch } from '@/api/http'

export interface VipPlan {
  level: number
  name: string
  price: number
  benefits: string[]
}

export interface VipQuotaRule {
  vipLevel: number
  taskType: string
  dailyFree: number
  discountPercent: number
}

export async function fetchVipRules(): Promise<VipQuotaRule[]> {
  return apiFetch<VipQuotaRule[]>('/vip/rules')
}

export async function fetchVipPlans(): Promise<VipPlan[]> {
  return apiFetch<VipPlan[]>('/vip/plans')
}

export async function purchaseVipWithBalance(level: number): Promise<{ vipLevel: number }> {
  return apiFetch<{ vipLevel: number }>('/vip/purchase', {
    method: 'POST',
    body: JSON.stringify({ level }),
  })
}

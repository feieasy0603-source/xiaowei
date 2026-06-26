import { apiFetch } from '@/api/http'

export interface QuotaQuote {
  taskType: string
  vipLevel?: number
  originalAmount: number
  finalAmount: number
  discountPercent: number
  dailyFree: number
  usedToday: number
  freeRemaining: number
  willUseFreeQuota: boolean
}

export interface OrderDelivery {
  id: number
  source: 'paper' | 'job'
  fileType: string
  fileName: string
  downloadUrl: string
}

export interface OrderDto {
  id?: number | null
  orderNo: string
  /** 仅任务号查询、无关联订单时为 true */
  jobOnly?: boolean
  productId: string
  productLabel: string
  paperId?: string
  amount: number
  payStatus: string
  payMethod?: string
  paidAt?: string
  createdAt: string
  jobId?: number
  jobNo?: string
  jobStatus?: string
  jobProgress?: number
  quota?: QuotaQuote
  /** 游客查询时附带的可下载交付列表 */
  deliveries?: OrderDelivery[]
  /** 复用同草稿未支付订单，避免重复下单 */
  reused?: boolean
  /** 推广码无效，订单仍可创建但不计渠道 */
  channelInvalid?: boolean
}

export interface CreateOrderParams {
  productId: string
  paperId?: string
  dCode?: string
  degree?: string
  wordCount?: number
  modelType?: string
}

export interface PrepayInfo {
  orderId: number
  orderNo: string
  amount: number
  prepayId: string
  qrContent: string
  expireSeconds: number
  mock?: boolean
  method?: 'wechat' | 'alipay'
}

export type PayChannel = 'wechat' | 'alipay'

export interface OrderPriceQuote extends QuotaQuote {
  productId: string
  productLabel?: string
  price: number
}

export async function quoteOrder(params: {
  productId: string
  degree?: string
  wordCount?: number
  modelType?: string
}): Promise<OrderPriceQuote> {
  const q = new URLSearchParams({ productId: params.productId })
  if (params.degree) q.set('degree', params.degree)
  if (params.wordCount != null) q.set('wordCount', String(params.wordCount))
  if (params.modelType) q.set('modelType', params.modelType)
  const raw = await apiFetch<Record<string, unknown>>(`/orders/quote?${q}`)
  return {
    productId: String(raw.productId),
    productLabel: raw.productLabel as string | undefined,
    taskType: String(raw.taskType ?? ''),
    vipLevel: raw.vipLevel as number | undefined,
    originalAmount: Number(raw.originalAmount ?? 0),
    finalAmount: Number(raw.finalAmount ?? raw.price ?? 0),
    discountPercent: Number(raw.discountPercent ?? 0),
    dailyFree: Number(raw.dailyFree ?? 0),
    usedToday: Number(raw.usedToday ?? 0),
    freeRemaining: Number(raw.freeRemaining ?? 0),
    willUseFreeQuota: Boolean(raw.willUseFreeQuota),
    price: Number(raw.price ?? raw.finalAmount ?? 0),
  }
}

export interface OrdersPageResult {
  items: OrderDto[]
  total: number
  page: number
  size: number
}

export async function fetchOrders(page = 1, size = 20): Promise<OrdersPageResult> {
  const q = new URLSearchParams({ page: String(page), size: String(size) })
  return apiFetch<OrdersPageResult>(`/orders?${q}`)
}

/** 凭订单号或任务号查询，无需登录（J 开头按任务号，否则按订单号） */
export async function lookupByNo(no: string): Promise<OrderDto> {
  const trimmed = no.trim()
  const q = new URLSearchParams()
  if (/^J/i.test(trimmed)) {
    q.set('jobNo', trimmed)
  } else {
    q.set('orderNo', trimmed)
  }
  return apiFetch<OrderDto>(`/orders/lookup?${q}`)
}

export async function fetchOrder(orderId: number): Promise<OrderDto> {
  return apiFetch<OrderDto>(`/orders/${orderId}`)
}

export async function createOrder(params: CreateOrderParams): Promise<OrderDto> {
  return apiFetch<OrderDto>('/orders', {
    method: 'POST',
    body: JSON.stringify(params),
  })
}

export async function prepayOrder(orderId: number, method: PayChannel = 'wechat'): Promise<PrepayInfo> {
  return apiFetch<PrepayInfo>(`/orders/${orderId}/prepay`, {
    method: 'POST',
    body: JSON.stringify({ method }),
  })
}

export async function payOrderWithBalance(orderId: number): Promise<OrderDto> {
  return apiFetch<OrderDto>(`/orders/${orderId}/pay-balance`, { method: 'POST' })
}

export async function payOrderWechatMock(orderId: number): Promise<OrderDto> {
  return apiFetch<OrderDto>(`/orders/${orderId}/pay-wechat-mock`, { method: 'POST' })
}

export async function payOrderAlipayMock(orderId: number): Promise<OrderDto> {
  return apiFetch<OrderDto>(`/orders/${orderId}/pay-alipay-mock`, { method: 'POST' })
}

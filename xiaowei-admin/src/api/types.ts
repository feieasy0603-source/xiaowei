export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface AdminUser {
  id: number
  phone?: string
  nickname?: string
  balance: number
  vipLevel?: number
  status: string
  createdAt?: string
  wxOpenId?: string
  updatedAt?: string
}

export interface WalletLogRow {
  id: number
  userId: number
  type: string
  amount: number
  balanceAfter: number
  refType?: string
  refId?: string
  remark?: string
  createdAt: string
}

export interface AdminOrder {
  id: number
  orderNo: string
  userId: number
  userPhone?: string
  userNickname?: string
  productId: string
  productLabel?: string
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
  channelId?: number
  quoteDegree?: string
  quoteWordCount?: number
  quoteModelType?: string
}

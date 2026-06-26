export function payStatusLabel(row: {
  payStatus?: string
  jobOnly?: boolean
}): string {
  if (row.jobOnly || row.payStatus === 'job_only') return '任务查询'
  if (row.payStatus === 'paid') return '已支付'
  if (row.payStatus === 'refunded') return '已退款'
  if (row.payStatus === 'unpaid') return '待支付'
  return row.payStatus ?? '—'
}

export function payStatusTagType(row: {
  payStatus?: string
  jobOnly?: boolean
}): 'success' | 'info' | 'warning' {
  if (row.jobOnly || row.payStatus === 'job_only') return 'info'
  if (row.payStatus === 'paid') return 'success'
  return 'warning'
}

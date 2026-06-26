import { describe, expect, it } from 'vitest'
import { payStatusLabel, payStatusTagType } from './orderStatus'

describe('orderStatus', () => {
  it('labels paid and refunded', () => {
    expect(payStatusLabel({ payStatus: 'paid' })).toBe('已支付')
    expect(payStatusLabel({ payStatus: 'refunded' })).toBe('已退款')
  })

  it('labels job-only rows', () => {
    expect(payStatusLabel({ jobOnly: true })).toBe('任务查询')
    expect(payStatusTagType({ payStatus: 'job_only' })).toBe('info')
  })

  it('uses warning tag for unpaid', () => {
    expect(payStatusTagType({ payStatus: 'unpaid' })).toBe('warning')
  })
})

import { onUnmounted } from 'vue'

/** 微信扫码支付轮询：每 intervalMs 检查一次是否已支付 */
export function usePaymentPoll(intervalMs = 3000) {
  let pollTimer: ReturnType<typeof setTimeout> | null = null
  let stopped = true

  function stopPoll() {
    stopped = true
    if (pollTimer) {
      clearTimeout(pollTimer)
      pollTimer = null
    }
  }

  /** checkPaid 返回 true 表示已完成，将停止轮询 */
  function startPoll(checkPaid: () => Promise<boolean>) {
    stopPoll()
    stopped = false

    const tick = async () => {
      try {
        const done = await checkPaid()
        if (done) stopPoll()
      } catch {
        /* ignore transient poll errors */
      } finally {
        if (!stopped) {
          pollTimer = setTimeout(() => void tick(), intervalMs)
        }
      }
    }

    void tick()
  }

  onUnmounted(stopPoll)

  return { startPoll, stopPoll }
}

/** prepay 响应 mock 且非生产构建时展示模拟支付入口 */
export function showMockPayEntry(prepayMock: boolean) {
  return prepayMock && !import.meta.env.PROD
}

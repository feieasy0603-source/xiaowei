import { onMounted, ref } from 'vue'
import { fetchOrders, type OrderDto } from '@/api/modules/orders'
import { useApiEnabled } from '@/api/http'
import { useAppStore } from '@/stores/app'

const notice = ref('')
const isDemo = ref(false)
let fetched = false

function maskTitle(title: string): string {
  const t = title.trim()
  if (t.length <= 4) return `${t.slice(0, 1)}***`
  return `${t.slice(0, 2)}${'*'.repeat(Math.min(6, t.length - 4))}${t.slice(-2)}`
}

function formatRelativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins}分钟前`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  return `${days}天前`
}

function formatOrderNotice(order: OrderDto): string {
  const when = formatRelativeTime(order.paidAt ?? order.createdAt)
  const label = order.productLabel || '论文'
  const st = (order.jobStatus ?? '').toLowerCase()
  if (st === 'success' || st === 'completed') {
    return `${when} ${label}任务生成成功`
  }
  if (st === 'running' || st === 'pending') {
    return `${when} ${label}任务处理中…`
  }
  return `${when} ${label}订单已提交`
}

async function refresh() {
  if (!useApiEnabled()) {
    notice.value = ''
    isDemo.value = false
    return
  }
  try {
    const { items: orders } = await fetchOrders(1, 10)
    const done = orders.find((o) => {
      const st = (o.jobStatus ?? '').toLowerCase()
      return st === 'success' || st === 'completed' || st === 'running' || st === 'pending'
    })
    if (done) {
      notice.value = formatOrderNotice(done)
      isDemo.value = false
      return
    }
    notice.value = ''
    isDemo.value = false
  } catch {
    notice.value = ''
    isDemo.value = false
  }
}

export function useRecentActivity() {
  function setFromSubmit(productLabel: string, title: string) {
    const masked = maskTitle(title)
    if (!useApiEnabled()) {
      notice.value = `演示模式：${productLabel}《${masked}》未实际提交`
      isDemo.value = true
      return
    }
    notice.value = `刚刚 ${productLabel}《${masked}》已提交`
    isDemo.value = false
  }

  function setFromJob(jobNo: string) {
    notice.value = `任务 ${jobNo} 已提交`
    isDemo.value = false
  }

  onMounted(() => {
    if (fetched) return
    fetched = true
    const appStore = useAppStore()
    if (useApiEnabled() && appStore.isLoggedIn) void refresh()
    else if (useApiEnabled()) void refresh()
  })

  return {
    notice,
    isDemo,
    refresh,
    setFromSubmit,
    setFromJob,
  }
}

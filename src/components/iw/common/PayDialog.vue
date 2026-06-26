<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import QrCode from '@/components/iw/common/QrCode.vue'
import { useApiEnabled } from '@/api/http'
import {
  createOrder,
  fetchOrder,
  payOrderWithBalance,
  payOrderWechatMock,
  payOrderAlipayMock,
  prepayOrder,
  type OrderDto,
  type PayChannel,
  type QuotaQuote,
} from '@/api/modules/orders'
import { fetchWalletQuota, redeemGiftCode, type QuotaSummaryItem } from '@/api/modules/wallet'
import { useAppStore } from '@/stores/app'
import { requireLogin } from '@/composables/useRequireLogin'
import { showMockPayEntry, usePaymentPoll } from '@/composables/usePaymentPoll'

const props = defineProps<{
  visible: boolean
  /** 支付已有待支付订单（订单列表等场景） */
  existingOrderId?: number
  amount?: number
  productId?: string
  paperId?: string
  degree?: string
  wordCount?: number
  modelType?: string
}>()

const emit = defineEmits<{
  'update:visible': [v: boolean]
  paid: [payload: { orderId: number; jobId?: number }]
}>()

const appStore = useAppStore()
const payMode = ref<'wechat' | 'alipay' | 'balance'>('wechat')
const giftCode = ref('')
const countdown = ref(300)
const paying = ref(false)
const pendingOrder = ref<OrderDto | null>(null)
const qrContent = ref('')
const prepayMock = ref(false)
const showMockPay = () => showMockPayEntry(prepayMock.value)
const prepayLoading = ref(false)
const quotaSummary = ref<QuotaSummaryItem[]>([])
const orderQuota = ref<QuotaQuote | null>(null)
const { startPoll, stopPoll } = usePaymentPoll()
let timer: ReturnType<typeof setInterval> | null = null

function startOrderPoll(orderId: number) {
  startPoll(async () => {
    const o = await fetchOrder(orderId)
    if (o.payStatus === 'paid') {
      await appStore.refreshProfile()
      afterPaid(o)
      return true
    }
    return false
  })
}

function orderAmount(): number {
  return Number(pendingOrder.value?.amount ?? props.amount ?? 0)
}

const displayAmount = () => orderAmount().toFixed(2)

const isVipFree = () =>
  orderAmount() === 0 || orderQuota.value?.willUseFreeQuota === true

async function ensureOrder() {
  if (pendingOrder.value) return pendingOrder.value
  if (props.existingOrderId) {
    pendingOrder.value = await fetchOrder(props.existingOrderId)
    orderQuota.value = pendingOrder.value.quota ?? null
    if (isVipFree()) payMode.value = 'balance'
    return pendingOrder.value
  }
  const productId = props.productId ?? appStore.activeMenuId
  pendingOrder.value = await createOrder({
    productId,
    paperId: props.paperId,
    dCode: appStore.dCode || undefined,
    degree: props.degree,
    wordCount: props.wordCount,
    modelType: props.modelType,
  })
  orderQuota.value = pendingOrder.value.quota ?? null
  if (isVipFree()) payMode.value = 'balance'
  if (pendingOrder.value.reused) {
    ElMessage.info('已恢复该论文的待支付订单')
  }
  if (pendingOrder.value.channelInvalid) {
    ElMessage.warning('推广码无效，订单不计渠道分成')
  }
  return pendingOrder.value
}

async function loadQuota() {
  if (!useApiEnabled() || !appStore.isLoggedIn) return
  try {
    quotaSummary.value = await fetchWalletQuota()
  } catch {
    quotaSummary.value = []
  }
}

function orderIdOrThrow(order: OrderDto): number {
  if (order.id == null) throw new Error('订单 ID 无效')
  return order.id
}

async function loadPrepay(method: PayChannel = payMode.value === 'alipay' ? 'alipay' : 'wechat') {
  if (!useApiEnabled()) return
  prepayLoading.value = true
  stopPoll()
  try {
    const order = await ensureOrder()
    if (orderAmount() === 0) {
      qrContent.value = ''
      return
    }
    const prepay = await prepayOrder(orderIdOrThrow(order), method)
    qrContent.value = prepay.qrContent
    prepayMock.value = prepay.mock === true
    if (!prepayMock.value) startOrderPoll(orderIdOrThrow(order))
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    prepayLoading.value = false
  }
}

watch(
  () => props.visible,
  (v) => {
    if (v) {
      countdown.value = 300
      if (!props.existingOrderId) {
        pendingOrder.value = null
        orderQuota.value = null
      }
      qrContent.value = ''
      prepayMock.value = false
      stopPoll()
      payMode.value = 'wechat'
      timer = setInterval(() => {
        if (countdown.value > 0) countdown.value--
        else if (timer) clearInterval(timer)
      }, 1000)
      if (useApiEnabled() && appStore.isLoggedIn) {
        void loadQuota()
        if (props.existingOrderId) {
          void fetchOrder(props.existingOrderId).then((o) => {
            pendingOrder.value = o
            orderQuota.value = o.quota ?? null
            void loadPrepay()
          })
        } else {
          void loadPrepay()
        }
      }
    } else if (timer) {
      clearInterval(timer)
      timer = null
      stopPoll()
    }
  },
)

watch(payMode, (mode) => {
  if (!props.visible || !useApiEnabled()) return
  if (mode === 'wechat' || mode === 'alipay') {
    void loadPrepay(mode)
  } else {
    stopPoll()
    qrContent.value = ''
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  stopPoll()
})

async function redeemGift() {
  if (!giftCode.value.trim()) {
    ElMessage.warning('请输入礼包码')
    return
  }
  if (!(await ensureLogin())) return
  try {
    await redeemGiftCode(giftCode.value.trim())
    await appStore.refreshProfile()
    giftCode.value = ''
    ElMessage.success('礼包码兑换成功，余额已更新')
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

function close() {
  emit('update:visible', false)
}

async function ensureLogin() {
  return requireLogin()
}

function afterPaid(paid: OrderDto) {
  void appStore.refreshProfile()
  ElMessage.success('支付成功，正在生成…')
  emit('paid', { orderId: orderIdOrThrow(paid), jobId: paid.jobId })
  close()
}

async function payBalance() {
  if (!useApiEnabled()) {
    ElMessage.info('演示模式：未发起真实支付')
    close()
    return
  }
  if (!(await ensureLogin())) return

  paying.value = true
  try {
    const order = await ensureOrder()
    if (orderAmount() > 0 && appStore.balance < orderAmount()) {
      ElMessage.warning(
        `余额不足（当前 ¥${appStore.balance.toFixed(2)}，需 ¥${orderAmount().toFixed(2)}），请先充值或使用微信扫码`,
      )
      return
    }
    const paid = await payOrderWithBalance(orderIdOrThrow(order))
    await appStore.refreshProfile()
    afterPaid(paid)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    paying.value = false
  }
}

async function payScanMock(method: PayChannel) {
  if (!useApiEnabled()) {
    ElMessage.info('演示模式：未发起真实支付')
    close()
    return
  }
  if (!(await ensureLogin())) return

  paying.value = true
  try {
    const order = await ensureOrder()
    const paid =
      method === 'alipay'
        ? await payOrderAlipayMock(orderIdOrThrow(order))
        : await payOrderWechatMock(orderIdOrThrow(order))
    await appStore.refreshProfile()
    afterPaid(paid)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    paying.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="isVipFree() ? '确认使用 VIP 免费额度' : '扫码支付'"
    width="440px"
    @update:model-value="emit('update:visible', $event)"
  >
    <p class="amount">
      应付金额：<strong>¥{{ displayAmount() }}</strong>
      <el-tag v-if="isVipFree()" type="success" size="small" class="vip-tag">VIP 免费额度</el-tag>
    </p>
    <p v-if="orderQuota && !isVipFree()" class="quota-hint">
      原价 ¥{{ Number(orderQuota.originalAmount).toFixed(2) }}，VIP 享
      {{ orderQuota.discountPercent }}% 折扣后 ¥{{ displayAmount() }}
    </p>
    <p v-else-if="orderQuota && isVipFree()" class="quota-hint">
      今日剩余免费 {{ orderQuota.freeRemaining }} / {{ orderQuota.dailyFree }} 次（已用
      {{ orderQuota.usedToday }}），无需扣余额
    </p>
    <p v-else-if="quotaSummary.length" class="quota-hint">
      <span v-for="q in quotaSummary" :key="q.taskType" class="quota-chip">
        {{ q.taskType }}：今日免费 {{ q.freeRemaining }}/{{ q.dailyFree }}
      </span>
    </p>
    <p v-if="!isVipFree()" class="countdown">剩余支付时间 {{ countdown }} 秒</p>
    <p v-else class="balance-line">当前余额 ¥{{ appStore.balance.toFixed(2) }}（本单不扣款）</p>

    <el-tabs v-model="payMode">
      <el-tab-pane v-if="!isVipFree()" label="微信扫码" name="wechat">
        <div v-loading="prepayLoading" class="qr-box">
          <template v-if="qrContent">
            <QrCode :content="qrContent" :size="180" />
            <p class="qr-label">{{ showMockPay() ? '开发环境：可使用下方按钮模拟支付' : '请使用微信扫一扫完成支付' }}</p>
          </template>
          <span v-else-if="prepayLoading">正在创建订单…</span>
          <span v-else>加载中…</span>
        </div>
        <el-button
          v-if="showMockPay()"
          type="primary"
          class="full-btn"
          :loading="paying"
          @click="payScanMock('wechat')"
        >
          模拟微信扫码支付成功
        </el-button>
      </el-tab-pane>
      <el-tab-pane v-if="!isVipFree()" label="支付宝扫码" name="alipay">
        <div v-loading="prepayLoading" class="qr-box">
          <template v-if="qrContent">
            <QrCode :content="qrContent" :size="180" />
            <p class="qr-label">{{ showMockPay() ? '开发环境：可使用下方按钮模拟支付' : '请使用支付宝扫一扫完成支付' }}</p>
          </template>
          <span v-else-if="prepayLoading">正在创建订单…</span>
          <span v-else>加载中…</span>
        </div>
        <el-button
          v-if="showMockPay()"
          type="primary"
          class="full-btn"
          :loading="paying"
          @click="payScanMock('alipay')"
        >
          模拟支付宝扫码支付成功
        </el-button>
      </el-tab-pane>
      <el-tab-pane label="余额 / VIP" name="balance">
        <p class="balance-hint">
          {{
            isVipFree()
              ? '确认后将扣减 1 次 VIP 免费额度并自动开始生成，不扣账户余额'
              : `将从余额扣款 ¥${displayAmount()}（当前 ¥${appStore.balance.toFixed(2)}）`
          }}
        </p>
        <el-button type="primary" class="full-btn" :loading="paying" @click="payBalance">
          {{ isVipFree() ? '确认使用 VIP 免费额度' : '余额支付' }}
        </el-button>
      </el-tab-pane>
    </el-tabs>

    <el-input v-model="giftCode" placeholder="请输入礼包码" class="gift">
      <template #append>
        <el-button @click="redeemGift">兑换</el-button>
      </template>
    </el-input>
    <template #footer>
      <el-button @click="close">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.amount {
  font-size: 16px;
  margin-bottom: 8px;
}

.amount strong {
  color: #dc2626;
  font-size: 22px;
}

.vip-tag {
  margin-left: 8px;
  vertical-align: middle;
}

.quota-hint {
  font-size: 12px;
  color: #059669;
  margin: -4px 0 12px;
  line-height: 1.6;
}

.quota-chip {
  display: inline-block;
  margin-right: 12px;
}

.countdown,
.balance-line {
  color: var(--xw-muted);
  font-size: 13px;
  margin-bottom: 16px;
}

.qr-box {
  min-height: 120px;
  background: #f9fafb;
  border: 1px dashed var(--xw-border);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--xw-muted);
  margin-bottom: 12px;
  padding: 12px;
  text-align: center;
}

.qr-label {
  font-size: 12px;
  margin: 0 0 8px;
}

.qr-code {
  font-size: 11px;
  word-break: break-all;
  color: #334155;
  background: #fff;
  padding: 8px;
  border-radius: 6px;
  max-width: 100%;
}

.balance-hint {
  font-size: 12px;
  color: var(--xw-muted);
  margin: 8px 0 12px;
  line-height: 1.5;
}

.full-btn {
  width: 100%;
  margin-bottom: 8px;
}

.gift {
  width: 100%;
  margin-top: 12px;
}
</style>

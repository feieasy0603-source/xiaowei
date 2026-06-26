<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import QrCode from '@/components/iw/common/QrCode.vue'
import { useApiEnabled } from '@/api/http'
import {
  confirmWalletRechargeMock,
  fetchWalletRechargeStatus,
  prepayWalletRecharge,
} from '@/api/modules/wallet'
import { useAppStore } from '@/stores/app'
import { showMockPayEntry, usePaymentPoll } from '@/composables/usePaymentPoll'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ 'update:visible': [v: boolean] }>()

const appStore = useAppStore()
const amount = ref(100)
const payMethod = ref<'wechat' | 'alipay'>('wechat')
const paying = ref(false)
const prepayOrderNo = ref('')
const qrContent = ref('')
const prepayMock = ref(false)
const showMockPay = () => showMockPayEntry(prepayMock.value)
const { startPoll, stopPoll } = usePaymentPoll()

function startRechargePoll() {
  if (!prepayOrderNo.value || prepayMock.value) return
  startPoll(async () => {
    const st = await fetchWalletRechargeStatus(prepayOrderNo.value)
    if (st.status === 'paid') {
      await appStore.refreshProfile()
      ElMessage.success('充值成功')
      close()
      return true
    }
    return false
  })
}

watch(
  () => props.visible,
  (v) => {
    if (v) {
      amount.value = 100
      payMethod.value = 'wechat'
      prepayOrderNo.value = ''
      qrContent.value = ''
      prepayMock.value = false
      stopPoll()
    } else {
      stopPoll()
    }
  },
)

onUnmounted(stopPoll)

function close() {
  stopPoll()
  emit('update:visible', false)
}

async function onPrepay() {
  if (!appStore.isLoggedIn) {
    appStore.openLogin()
    return
  }
  if (amount.value < 1) {
    ElMessage.warning('充值金额至少 ¥1')
    return
  }
  if (!useApiEnabled()) {
    ElMessage.info('当前为离线模式，请联系客服充值')
    return
  }
  paying.value = true
  stopPoll()
  try {
    const prepay = await prepayWalletRecharge(amount.value, payMethod.value)
    prepayOrderNo.value = prepay.orderNo
    qrContent.value = prepay.qrContent
    prepayMock.value = prepay.mock === true
    if (!prepayMock.value) startRechargePoll()
    ElMessage.success('已创建充值订单，请完成支付')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    paying.value = false
  }
}

async function onConfirmMock() {
  if (!prepayOrderNo.value) {
    ElMessage.warning('请先创建充值订单')
    return
  }
  paying.value = true
  try {
    await confirmWalletRechargeMock(prepayOrderNo.value)
    await appStore.refreshProfile()
    ElMessage.success('充值成功')
    close()
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
    title="账户充值"
    width="420px"
    @update:model-value="emit('update:visible', $event)"
  >
    <p class="hint">
      充值金额将存入账户余额，可用于论文生成、改稿等服务。
    </p>
    <el-form label-width="80px">
      <el-form-item label="充值金额">
        <el-input-number v-model="amount" :min="1" :max="10000" :step="10" />
      </el-form-item>
      <el-form-item label="支付方式">
        <el-radio-group v-model="payMethod">
          <el-radio-button value="wechat">微信</el-radio-button>
          <el-radio-button value="alipay">支付宝</el-radio-button>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <div v-if="qrContent" class="qr-box">
      <QrCode :content="qrContent" :size="160" />
      <p class="qr-label">
        {{
          showMockPay()
            ? '开发环境：可点击下方模拟支付'
            : payMethod === 'alipay'
              ? '请使用支付宝扫一扫完成充值'
              : '请使用微信扫一扫完成充值'
        }}
      </p>
      <el-button
        v-if="showMockPay()"
        type="primary"
        class="full-btn"
        :loading="paying"
        @click="onConfirmMock"
      >
        模拟支付成功
      </el-button>
    </div>
    <template #footer>
      <el-button @click="close">关闭</el-button>
      <el-button type="primary" :loading="paying" @click="onPrepay">
        {{ qrContent ? '重新创建订单' : '创建充值订单' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.hint {
  font-size: 13px;
  color: var(--xw-muted);
  line-height: 1.6;
  margin-bottom: 16px;
}

.qr-box {
  margin-top: 12px;
  padding: 12px;
  background: #f9fafb;
  border-radius: 8px;
  border: 1px dashed var(--xw-border);
  text-align: center;
}

.qr-label {
  font-size: 12px;
  margin: 8px 0;
  color: var(--xw-muted);
}

.full-btn {
  width: 100%;
}
</style>

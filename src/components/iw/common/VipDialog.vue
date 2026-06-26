<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { useApiEnabled } from '@/api/http'
import { fetchVipPlans, purchaseVipWithBalance } from '@/api/modules/vip'
import type { VipPlan } from '@/api/modules/vip'
import { useAppStore } from '@/stores/app'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ 'update:visible': [v: boolean] }>()

const appStore = useAppStore()
const plans = ref<VipPlan[]>([])
const loading = ref(false)
const paying = ref(false)
const selectedLevel = ref(1)

watch(
  () => props.visible,
  (v) => {
    if (v && useApiEnabled()) void loadPlans()
  },
)

onMounted(() => {
  if (props.visible && useApiEnabled()) void loadPlans()
})

async function loadPlans() {
  loading.value = true
  try {
    plans.value = await fetchVipPlans()
    if (plans.value.length && !plans.value.some((p) => p.level === selectedLevel.value)) {
      selectedLevel.value = plans.value[0]!.level
    }
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function close() {
  emit('update:visible', false)
}

async function onPurchase() {
  if (!appStore.isLoggedIn) {
    appStore.openLogin()
    return
  }
  if (!useApiEnabled()) {
    ElMessage.info('当前为离线模式，无法开通 VIP')
    return
  }
  paying.value = true
  try {
    await purchaseVipWithBalance(selectedLevel.value)
    await appStore.refreshProfile()
    ElMessage.success('VIP 开通成功')
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
    title="开通 VIP"
    width="480px"
    @update:model-value="emit('update:visible', $event)"
  >
    <p v-if="appStore.vipLevel > 0" class="current-vip">
      当前等级：VIP{{ appStore.vipLevel }}
    </p>
    <div v-loading="loading" class="plans">
      <label
        v-for="plan in plans"
        :key="plan.level"
        class="plan-card"
        :class="{ active: selectedLevel === plan.level }"
      >
        <input v-model="selectedLevel" type="radio" :value="plan.level" />
        <div class="plan-body">
          <strong>VIP{{ plan.level }} · {{ plan.name }}</strong>
          <p class="price">¥{{ plan.price.toFixed(2) }}</p>
          <ul>
            <li v-for="(b, i) in plan.benefits" :key="i">{{ b }}</li>
          </ul>
        </div>
      </label>
    </div>
    <p class="hint">将从账户余额扣款。余额不足请先充值。</p>
    <template #footer>
      <el-button @click="close">关闭</el-button>
      <el-button type="warning" :loading="paying" @click="onPurchase">
        余额开通 VIP
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.current-vip {
  margin-bottom: 12px;
  color: #b45309;
  font-weight: 600;
}

.plans {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 80px;
}

.plan-card {
  display: flex;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--xw-border);
  border-radius: 8px;
  cursor: pointer;
}

.plan-card.active {
  border-color: #f59e0b;
  background: #fffbeb;
}

.plan-card input {
  margin-top: 4px;
}

.plan-body strong {
  display: block;
  margin-bottom: 4px;
}

.price {
  color: #dc2626;
  font-weight: 700;
  margin-bottom: 6px;
}

.plan-body ul {
  padding-left: 18px;
  font-size: 12px;
  color: var(--xw-muted);
  line-height: 1.6;
}

.hint {
  margin-top: 12px;
  font-size: 12px;
  color: var(--xw-muted);
}
</style>

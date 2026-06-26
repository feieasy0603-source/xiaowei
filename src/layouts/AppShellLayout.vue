<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import AppSidebar from '@/components/home/AppSidebar.vue'
import AppTopBar from '@/components/home/AppTopBar.vue'
import FloatingActions from '@/components/home/FloatingActions.vue'
import LoginDialog from '@/components/iw/common/LoginDialog.vue'
import CustomerServiceDialog from '@/components/iw/common/CustomerServiceDialog.vue'
import { useCustomerService } from '@/composables/useCustomerService'
import PayDialog from '@/components/iw/common/PayDialog.vue'
import { useTaskPayStore } from '@/stores/taskPay'
import { useProductsStore } from '@/stores/products'
import { useAppStore } from '@/stores/app'
import { usePaperRoute } from '@/composables/usePaperRoute'
import { usePaperStore } from '@/stores/paper'
import { getToken, useApiEnabled } from '@/api/http'

const route = useRoute()
const router = useRouter()
const productsStore = useProductsStore()
const appStore = useAppStore()
const taskPay = useTaskPayStore()
const { visible: csVisible } = useCustomerService()
const { openProEdition, closeProEdition, goToStep } = usePaperRoute()

function onTaskPaid(payload: { orderId: number; jobId?: number }) {
  taskPay.close()
  if (payload.jobId) {
    void router.push({ name: 'jobDetail', params: { id: String(payload.jobId) } })
  } else {
    ElMessage.success('支付成功')
  }
}

const product = computed(() => productsStore.getProduct(appStore.activeMenuId))
const isOrders = computed(() => route.name === 'orders')
const isAccount = computed(() => route.name === 'account')
const isSubPage = computed(() => isOrders.value || isAccount.value)

const step = computed(() => {
  const s = Number(route.params.step)
  return Number.isFinite(s) ? s : 0
})

const proMode = computed(
  () =>
    !isSubPage.value &&
    (appStore.proEditionOpen || appStore.wizardOpen || step.value > 0),
)

const showSubHeader = computed(() => isSubPage.value || proMode.value)

function onBack() {
  if (isSubPage.value) {
    router.push({ name: 'intelligentWriting', params: { step: '0' }, query: route.query })
    return
  }
  if (step.value === 3) {
    if (appStore.wizardOpen) goToStep(2, true)
    else openProEdition()
    return
  }
  if (step.value === 2) {
    goToStep(1, true)
    return
  }
  if (step.value === 1) {
    goToStep(0, true)
    return
  }
  if (appStore.proEditionOpen) closeProEdition()
  else if (appStore.wizardOpen) {
    appStore.closeWizard()
    router.replace({
      name: 'intelligentWriting',
      params: { step: '0' },
      query: { ...route.query, wizard: undefined },
    })
  }
}

const paperStore = usePaperStore()

function onWindowFocus() {
  const id = paperStore.currentId
  if (id && useApiEnabled() && getToken()) {
    void paperStore.pullFromApi(id)
  }
}

onMounted(() => {
  window.addEventListener('focus', onWindowFocus)
})

onUnmounted(() => {
  window.removeEventListener('focus', onWindowFocus)
})
</script>

<template>
  <div class="app-shell">
    <AppTopBar class="top-bar-full" />

    <div class="app-body">
      <AppSidebar class="sidebar-fixed" />

      <div class="shell-main">
        <div class="shell-body">
          <header v-if="showSubHeader" class="sub-header">
            <div v-if="isSubPage" class="sub-header-inner">
              <h1 class="sub-title">{{ isAccount ? '用户中心' : '查询结果' }}</h1>
            </div>
            <div v-else class="sub-header-inner pro">
              <button type="button" class="back-btn" @click="onBack">← 返回</button>
              <h1 class="sub-title">
                {{ appStore.wizardOpen && step === 0 ? '写作向导' : `${product.label} · 专业版` }}
              </h1>
            </div>
          </header>

          <router-view />
        </div>
      </div>
    </div>

    <FloatingActions />
    <LoginDialog />
    <CustomerServiceDialog v-model:visible="csVisible" />

    <PayDialog
      v-if="taskPay.orderId"
      :visible="taskPay.visible"
      :existing-order-id="taskPay.orderId"
      :product-id="taskPay.productId"
      :paper-id="taskPay.paperId"
      :degree="taskPay.degree"
      :word-count="taskPay.wordCount"
      :model-type="taskPay.modelType"
      @update:visible="(v) => { if (!v) taskPay.close() }"
      @paid="onTaskPaid"
    />
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--xw-bg-soft);
}

.top-bar-full {
  flex-shrink: 0;
  z-index: 20;
}

.app-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.sidebar-fixed {
  position: sticky;
  top: 0;
  align-self: flex-start;
  height: calc(100vh - var(--xw-topbar-height));
  flex-shrink: 0;
  z-index: 10;
}

.shell-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.shell-body {
  flex: 1;
  padding: 20px 24px 40px;
  width: 100%;
}

.sub-header {
  margin-bottom: 16px;
}

.sub-header-inner.pro {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.back-btn {
  border: none;
  background: none;
  color: var(--xw-muted);
  font-size: 14px;
  cursor: pointer;
  padding: 0;
  align-self: flex-start;
}

.back-btn:hover {
  color: var(--xw-primary);
}

.sub-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--xw-text);
  letter-spacing: -0.02em;
}

@media (max-width: 900px) {
  .app-body {
    flex-direction: column;
  }

  .sidebar-fixed {
    position: static;
    height: auto;
    width: 100%;
  }

  .shell-body {
    padding: 12px 14px 28px;
  }
}
</style>

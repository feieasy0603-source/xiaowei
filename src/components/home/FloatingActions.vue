<script setup lang="ts">
import { ref } from 'vue'
import { ChatDotRound, Present } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import ShareDialog from '@/components/iw/common/ShareDialog.vue'
import { useCustomerService } from '@/composables/useCustomerService'
import { useAppStore } from '@/stores/app'
import { useApiEnabled } from '@/api/http'

const appStore = useAppStore()
const showShare = import.meta.env.PROD || useApiEnabled()
const shareVisible = ref(false)
const { open: openCustomerService } = useCustomerService()

function onShare() {
  if (!useApiEnabled()) {
    ElMessage.info('离线演示模式暂无分享功能')
    return
  }
  if (!appStore.isLoggedIn) {
    appStore.openLogin()
    return
  }
  shareVisible.value = true
}
</script>

<template>
  <div class="float-actions">
    <el-tooltip v-if="showShare" content="分享有礼" placement="left">
      <button type="button" class="fab gift" title="分享有礼" @click="onShare">
        <el-icon :size="20"><Present /></el-icon>
      </button>
    </el-tooltip>
    <ShareDialog v-model:visible="shareVisible" />
    <el-tooltip content="在线客服" placement="left">
      <button type="button" class="fab cs" title="在线客服" @click="openCustomerService">
        <el-icon :size="20"><ChatDotRound /></el-icon>
      </button>
    </el-tooltip>
  </div>
</template>

<style scoped>
.float-actions {
  position: fixed;
  right: 18px;
  bottom: 96px;
  z-index: 200;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.fab {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: 0 6px 20px rgb(15 23 42 / 18%);
  transition: transform 0.12s, box-shadow 0.12s;
}

.fab:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgb(15 23 42 / 22%);
}

.fab.gift {
  background: linear-gradient(145deg, #f87171, #ef4444);
}

.fab.cs {
  background: linear-gradient(145deg, #60a5fa, #3b82f6);
}
</style>

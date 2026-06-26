<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { fetchShareInfo, type ShareInfo } from '@/api/modules/auth'
import { useApiEnabled } from '@/api/http'
import { useAppStore } from '@/stores/app'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ 'update:visible': [v: boolean] }>()

const appStore = useAppStore()
const router = useRouter()
const loading = ref(false)
const info = ref<ShareInfo | null>(null)

async function load() {
  if (!useApiEnabled()) {
    ElMessage.warning('请连接后端后使用分享功能')
    return
  }
  if (!appStore.isLoggedIn) {
    appStore.openLogin()
    emit('update:visible', false)
    return
  }
  loading.value = true
  try {
    info.value = await fetchShareInfo()
  } catch (e) {
    ElMessage.error((e as Error).message)
    emit('update:visible', false)
  } finally {
    loading.value = false
  }
}

async function copyLink() {
  if (!info.value?.shareLink) return
  try {
    await navigator.clipboard.writeText(info.value.shareLink)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.warning('复制失败')
  }
}

watch(
  () => props.visible,
  (v) => {
    if (v) void load()
  },
)
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="分享有礼"
    width="480px"
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-loading="loading" :class="{ disabled: info && info.enabled === false }">
      <el-alert
        v-if="info && info.shareLinkRelative"
        type="warning"
        :closable="false"
        show-icon
        title="分享链接未配置完整域名"
        description="当前为相对路径，好友可能无法直接打开。请运维设置 REFERRAL_FRONTEND_BASE_URL。"
        class="disabled-alert"
      />
      <el-alert
        v-if="info && info.enabled === false"
        type="info"
        :closable="false"
        show-icon
        title="分享统计中"
        description="平台暂未开启邀请余额奖励，您仍可分享链接邀请好友注册。"
        class="disabled-alert"
      />
      <template v-if="info">
        <p class="rules">{{ info.rules }}</p>
        <p>邀请码：<strong>{{ info.referralCode }}</strong></p>
        <p>已邀请 {{ info.invitedCount }} 人</p>
        <el-input :model-value="info.shareLink" readonly class="link-input">
          <template #append>
            <el-button @click="copyLink">复制</el-button>
          </template>
        </el-input>
      </template>
    </div>
    <template #footer>
      <el-button @click="emit('update:visible', false)">关闭</el-button>
      <el-button
        type="primary"
        link
        @click="router.push({ name: 'account', query: { tab: 'share' } }); emit('update:visible', false)"
      >
        查看详情
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.rules {
  font-size: 13px;
  color: #64748b;
  line-height: 1.55;
  margin: 0 0 12px;
}

.link-input {
  margin-top: 12px;
}

.disabled-alert {
  margin-bottom: 12px;
}

.disabled .rules {
  color: #94a3b8;
}
</style>

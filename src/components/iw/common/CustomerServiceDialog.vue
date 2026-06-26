<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { fetchSupportInfo, offlineSupportInfo, type SupportInfo } from '@/api/modules/meta'
import { useApiEnabled } from '@/api/http'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ 'update:visible': [v: boolean] }>()

const loading = ref(false)
const info = ref<SupportInfo | null>(null)

async function load() {
  loading.value = true
  try {
    info.value = useApiEnabled() ? await fetchSupportInfo() : offlineSupportInfo()
  } catch {
    info.value = offlineSupportInfo()
  } finally {
    loading.value = false
  }
}

async function copyText(label: string, text?: string | null) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`${label}已复制`)
  } catch {
    ElMessage.warning('复制失败')
  }
}

function openExternal() {
  const url = info.value?.externalUrl
  if (!url) return
  window.open(url, '_blank', 'noopener,noreferrer')
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
    :title="info?.title ?? '在线客服'"
    width="420px"
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-loading="loading">
      <template v-if="info">
        <p v-if="info.workHours" class="hours">
          <span class="label">服务时间</span>{{ info.workHours }}
        </p>

        <div v-if="info.enabled && info.hasChannel" class="channels">
          <div v-if="info.phone" class="row">
            <span class="label">电话</span>
            <a :href="`tel:${info.phone}`" class="link">{{ info.phone }}</a>
            <el-button link type="primary" @click="copyText('电话', info.phone)">复制</el-button>
          </div>
          <div v-if="info.wechatId" class="row">
            <span class="label">微信</span>
            <span class="value">{{ info.wechatId }}</span>
            <el-button link type="primary" @click="copyText('微信号', info.wechatId)">复制</el-button>
          </div>
          <div v-if="info.qq" class="row">
            <span class="label">QQ</span>
            <span class="value">{{ info.qq }}</span>
            <el-button link type="primary" @click="copyText('QQ', info.qq)">复制</el-button>
          </div>
          <div v-if="info.email" class="row">
            <span class="label">邮箱</span>
            <a :href="`mailto:${info.email}`" class="link">{{ info.email }}</a>
          </div>
          <el-button
            v-if="info.externalUrl"
            type="primary"
            class="full-btn"
            @click="openExternal"
          >
            打开在线客服页
          </el-button>
        </div>

        <el-alert
          v-else-if="info.enabled"
          type="warning"
          :closable="false"
          show-icon
          title="客服渠道尚未配置"
          description="请稍后再试，或前往用户中心提交问题。"
        />

        <el-alert
          v-else
          type="info"
          :closable="false"
          show-icon
          title="客服暂不在线"
          description="请在工作时间内再次联系，或留下邮件我们会回复。"
        />

        <p v-if="info.note" class="note">{{ info.note }}</p>
      </template>
    </div>
  </el-dialog>
</template>

<style scoped>
.hours {
  margin: 0 0 14px;
  font-size: 14px;
  color: #475569;
}

.label {
  color: #64748b;
  margin-right: 8px;
}

.channels {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 14px;
}

.value {
  color: #1e293b;
  font-weight: 500;
}

.link {
  color: var(--el-color-primary);
  text-decoration: none;
}

.link:hover {
  text-decoration: underline;
}

.full-btn {
  width: 100%;
  margin-top: 8px;
}

.note {
  margin: 14px 0 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.55;
}
</style>

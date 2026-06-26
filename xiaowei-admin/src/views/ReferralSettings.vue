<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { adminFetch } from '@/api/http'

interface ReferralSettingsDto {
  inviterReward: number
  inviteeReward: number
  rulesText?: string | null
  enabled: boolean
  updatedAt?: string
  defaultInviterReward?: number
  defaultInviteeReward?: number
}

const loading = ref(false)
const saving = ref(false)
const form = ref({
  inviterReward: 5,
  inviteeReward: 2,
  rulesText: '',
  enabled: true,
})
const defaults = ref({ inviter: 5, invitee: 2 })
const updatedAt = ref('')

const previewRules = computed(() => {
  if (form.value.rulesText.trim()) return form.value.rulesText.trim()
  if (!form.value.enabled) {
    return '分享活动暂未开启奖励发放，您仍可分享链接邀请好友注册。'
  }
  return `分享专属链接，好友注册成功后：邀请人获得 ¥${form.value.inviterReward}，好友获得 ¥${form.value.inviteeReward}。`
})

async function load() {
  loading.value = true
  try {
    const data = await adminFetch<ReferralSettingsDto>('/admin/referral-settings')
    form.value = {
      inviterReward: Number(data.inviterReward),
      inviteeReward: Number(data.inviteeReward),
      rulesText: data.rulesText ?? '',
      enabled: data.enabled !== false,
    }
    defaults.value = {
      inviter: Number(data.defaultInviterReward ?? 5),
      invitee: Number(data.defaultInviteeReward ?? 2),
    }
    updatedAt.value = data.updatedAt ?? ''
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const data = await adminFetch<ReferralSettingsDto>('/admin/referral-settings', {
      method: 'PUT',
      body: JSON.stringify({
        inviterReward: form.value.inviterReward,
        inviteeReward: form.value.inviteeReward,
        rulesText: form.value.rulesText.trim() || null,
        enabled: form.value.enabled,
      }),
    })
    form.value.rulesText = data.rulesText ?? ''
    updatedAt.value = data.updatedAt ?? ''
    ElMessage.success('分享奖励已保存，用户端将立即生效')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

function resetToDefaults() {
  form.value.inviterReward = defaults.value.inviter
  form.value.inviteeReward = defaults.value.invitee
}

onMounted(() => void load())
</script>

<template>
  <div class="page">
    <h2>分享邀请奖励</h2>
    <p class="desc">
      在此调整邀请人与新用户注册奖励金额；留空「活动说明」时将按金额自动生成文案。环境变量
      <code>REFERRAL_*</code> 仅作数据库无记录时的默认值。
    </p>

    <el-card v-loading="loading" shadow="never" class="form-card">
      <el-form label-width="120px" style="max-width: 520px">
        <el-form-item label="开启奖励">
          <el-switch v-model="form.enabled" active-text="发放余额奖励" inactive-text="仅统计邀请" />
        </el-form-item>
        <el-form-item label="邀请人奖励">
          <el-input-number v-model="form.inviterReward" :min="0" :max="99999" :precision="2" :step="1" />
          <span class="unit">元 / 人</span>
        </el-form-item>
        <el-form-item label="新用户奖励">
          <el-input-number v-model="form.inviteeReward" :min="0" :max="99999" :precision="2" :step="1" />
          <span class="unit">元 / 注册</span>
        </el-form-item>
        <el-form-item label="活动说明">
          <el-input
            v-model="form.rulesText"
            type="textarea"
            :rows="4"
            placeholder="留空则根据上方金额自动生成；支持自定义完整说明文案"
          />
        </el-form-item>
        <el-form-item label="文案预览">
          <p class="preview">{{ previewRules }}</p>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
          <el-button @click="resetToDefaults">恢复环境默认金额</el-button>
        </el-form-item>
      </el-form>
      <p v-if="updatedAt" class="meta">最近更新：{{ updatedAt.slice(0, 19) }}</p>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  padding: 20px;
}

.desc {
  color: #64748b;
  font-size: 14px;
  margin: 0 0 16px;
  max-width: 640px;
  line-height: 1.55;
}

.form-card {
  max-width: 720px;
}

.unit {
  margin-left: 8px;
  color: #64748b;
  font-size: 13px;
}

.preview {
  margin: 0;
  font-size: 13px;
  color: #334155;
  line-height: 1.6;
  white-space: pre-wrap;
}

.meta {
  margin: 16px 0 0;
  font-size: 12px;
  color: #94a3b8;
}

code {
  font-size: 12px;
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
}
</style>

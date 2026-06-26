<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import {
  changePassword,
  fetchMe,
  fetchShareInfo,
  updateProfile,
  type ShareInfo,
  type UserWritingPreferences,
} from '@/api/modules/auth'
import { fetchMyPapers, fetchPaper, type PaperSummary } from '@/api/modules/papers'
import { fetchWalletLogs, redeemGiftCode, type WalletLogItem } from '@/api/modules/wallet'
import { fetchVipRules, type VipQuotaRule } from '@/api/modules/vip'
import { fetchSchools } from '@/api/modules/schools'
import { useApiEnabled } from '@/api/http'
import { useAppStore } from '@/stores/app'
import {
  loadUserPreferences,
  saveUserPreferences,
  type UserWritingPrefs,
} from '@/composables/useUserPreferences'
import type { PaperLanguage, PaperModel } from '@/types/paper'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const apiMode = useApiEnabled()

const activeTab = ref(
  typeof route.query.tab === 'string' ? route.query.tab : 'overview',
)

const prefs = ref<UserWritingPrefs>(loadUserPreferences())
const schools = ref<{ id: string; name: string }[]>([])
const savingPrefs = ref(false)

const oldPwd = ref('')
const newPwd = ref('')
const confirmPwd = ref('')
const changingPwd = ref(false)

const drafts = ref<PaperSummary[]>([])
const draftsLoading = ref(false)

const walletLogs = ref<WalletLogItem[]>([])
const walletTotal = ref(0)
const logsLoading = ref(false)

const share = ref<ShareInfo | null>(null)
const shareLoading = ref(false)

const vipRules = ref<VipQuotaRule[]>([])
const rulesLoading = ref(false)

const profilePhone = ref('')
const profileVip = ref(0)

async function loadProfile() {
  if (!apiMode || !appStore.isLoggedIn) return
  try {
    const me = await fetchMe()
    profilePhone.value = me.phone
    profileVip.value = me.vipLevel
    if (me.preferences) {
      const p = me.preferences
      prefs.value = {
        ...prefs.value,
        degree: p.degree,
        wordCount: p.wordCount,
        schoolId: p.schoolId,
        category: p.category,
        language:
          p.language === 'zh' || p.language === 'en' || p.language === 'ja'
            ? (p.language as PaperLanguage)
            : prefs.value.language,
        model:
          p.model === 'academia' || p.model === 'standard'
            ? (p.model as PaperModel)
            : prefs.value.model,
      }
      saveUserPreferences(prefs.value)
    }
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function savePrefs() {
  savingPrefs.value = true
  try {
    saveUserPreferences(prefs.value)
    if (apiMode) {
      await updateProfile({
        preferences: prefs.value as UserWritingPreferences,
      })
    }
    ElMessage.success('写作偏好已保存')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    savingPrefs.value = false
  }
}

async function onChangePassword() {
  if (!oldPwd.value.trim()) {
    ElMessage.warning('请输入当前密码')
    return
  }
  if (newPwd.value.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (newPwd.value !== confirmPwd.value) {
    ElMessage.warning('两次新密码不一致')
    return
  }
  if (!apiMode) {
    ElMessage.warning('离线模式无法改密')
    return
  }
  changingPwd.value = true
  try {
    await changePassword(oldPwd.value, newPwd.value, confirmPwd.value)
    oldPwd.value = ''
    newPwd.value = ''
    confirmPwd.value = ''
    appStore.logout()
    ElMessage.success('密码已修改，请使用新密码重新登录')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    changingPwd.value = false
  }
}

async function loadDrafts() {
  if (!apiMode) return
  draftsLoading.value = true
  try {
    drafts.value = await fetchMyPapers(40)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    draftsLoading.value = false
  }
}

async function loadLogs() {
  if (!apiMode) return
  logsLoading.value = true
  try {
    const page = await fetchWalletLogs(0, 30)
    walletLogs.value = page.items
    walletTotal.value = page.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    logsLoading.value = false
  }
}

async function loadShare() {
  if (!apiMode) return
  shareLoading.value = true
  try {
    share.value = await fetchShareInfo()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    shareLoading.value = false
  }
}

async function loadVipRules() {
  if (!apiMode) {
    vipRules.value = []
    return
  }
  rulesLoading.value = true
  try {
    vipRules.value = await fetchVipRules()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    rulesLoading.value = false
  }
}

async function openDraft(row: PaperSummary) {
  if (row.productId) appStore.setActiveMenu(row.productId)
  if (apiMode) {
    try {
      await fetchPaper(row.id)
    } catch (e) {
      ElMessage.error((e as Error).message || '草稿不存在或无权访问')
      return
    }
  }
  void router.push({
    name: 'intelligentWriting',
    params: { step: String(row.maxVisitedStep ?? 0) },
    query: { lunwen: row.id },
  })
}

const giftCode = ref('')
const redeemingGift = ref(false)

async function onRedeemGift() {
  const code = giftCode.value.trim()
  if (!code) {
    ElMessage.warning('请输入礼包码')
    return
  }
  redeemingGift.value = true
  try {
    const res = await redeemGiftCode(code)
    giftCode.value = ''
    await appStore.refreshProfile()
    ElMessage.success(res.message || `兑换成功，余额 ¥${Number(res.balance).toFixed(2)}`)
    if (activeTab.value === 'wallet') void loadLogs()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    redeemingGift.value = false
  }
}

async function copyShareLink() {
  if (!share.value?.shareLink) return
  try {
    await navigator.clipboard.writeText(share.value.shareLink)
    ElMessage.success('分享链接已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择链接复制')
  }
}

const vipRulesGrouped = computed(() => {
  const map = new Map<number, VipQuotaRule[]>()
  for (const r of vipRules.value) {
    const list = map.get(r.vipLevel) ?? []
    list.push(r)
    map.set(r.vipLevel, list)
  }
  return [...map.entries()].sort((a, b) => a[0] - b[0])
})

function logTypeLabel(row: WalletLogItem) {
  if (row.refType === 'referral_invitee') return '邀请注册礼'
  if (row.refType === 'referral_inviter') return '邀请奖励'
  if (row.refType === 'vip_purchase') return 'VIP 购买'
  if (row.refType === 'order') return '订单扣款'
  if (row.refType === 'gift_code') return '礼包兑换'
  if (row.refType === 'user_recharge' || row.refType === 'admin_recharge') return '充值'
  if (row.type === 'recharge') return '入账'
  if (row.type === 'deduct') return '扣款'
  return row.type
}

watch(activeTab, (tab) => {
  void router.replace({ query: { ...route.query, tab } })
  if (tab === 'drafts') void loadDrafts()
  if (tab === 'wallet') void loadLogs()
  if (tab === 'share') void loadShare()
  if (tab === 'vip') void loadVipRules()
})

onMounted(async () => {
  if (apiMode) {
    try {
      schools.value = await fetchSchools()
    } catch {
      schools.value = []
    }
  }
  await loadProfile()
  if (activeTab.value === 'drafts') void loadDrafts()
  if (activeTab.value === 'wallet') void loadLogs()
  if (activeTab.value === 'share') void loadShare()
  if (activeTab.value === 'vip') void loadVipRules()
})
</script>

<template>
  <div class="user-account xw-page-pad">
    <el-tabs v-model="activeTab" class="account-tabs">
      <el-tab-pane label="概览" name="overview">
        <el-card shadow="never" class="card-block">
          <p><strong>昵称</strong>：{{ appStore.nickname || '—' }}</p>
          <p v-if="profilePhone"><strong>手机</strong>：{{ profilePhone }}</p>
          <p>
            <strong>余额</strong>：¥{{ appStore.balance.toFixed(2) }}
            <el-tag v-if="profileVip > 0" type="warning" size="small" class="ml8">VIP{{ profileVip }}</el-tag>
          </p>
          <div class="quick-links">
            <el-button @click="router.push({ name: 'orders' })">我的订单</el-button>
            <el-button type="primary" plain @click="activeTab = 'drafts'">我的草稿</el-button>
            <el-button type="success" plain @click="activeTab = 'share'">分享有礼</el-button>
            <el-button plain @click="activeTab = 'gift'">礼包兑换</el-button>
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="写作偏好" name="prefs">
        <el-card shadow="never" class="card-block">
          <el-form label-width="100px" class="prefs-form">
            <el-form-item label="学历">
              <el-select v-model="prefs.degree" placeholder="请选择" clearable style="width: 100%">
                <el-option label="专科" value="专科" />
                <el-option label="本科" value="本科" />
                <el-option label="硕士" value="硕士" />
                <el-option label="博士" value="博士" />
              </el-select>
            </el-form-item>
            <el-form-item label="目标字数">
              <el-input-number v-model="prefs.wordCount" :min="3000" :max="50000" :step="1000" />
            </el-form-item>
            <el-form-item label="写作模型">
              <el-select v-model="prefs.model" clearable style="width: 100%">
                <el-option label="标准版" value="standard" />
                <el-option label="学术版" value="academia" />
              </el-select>
            </el-form-item>
            <el-form-item label="默认学校">
              <el-select
                v-model="prefs.schoolId"
                filterable
                clearable
                placeholder="选填"
                style="width: 100%"
              >
                <el-option v-for="s in schools" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="学科门类">
              <el-input v-model="prefs.category" placeholder="如：教育经管" clearable />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingPrefs" @click="savePrefs">保存偏好</el-button>
            </el-form-item>
          </el-form>
          <p class="hint">新建草稿时将自动带入以上默认值。</p>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="修改密码" name="security">
        <el-card shadow="never" class="card-block">
          <el-form label-width="100px" class="prefs-form" @submit.prevent="onChangePassword">
            <el-form-item label="当前密码">
              <el-input v-model="oldPwd" type="password" show-password autocomplete="current-password" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="newPwd" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <el-form-item label="确认新密码">
              <el-input v-model="confirmPwd" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="changingPwd" @click="onChangePassword">保存密码</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="我的草稿" name="drafts">
        <el-table v-loading="draftsLoading" :data="drafts" stripe empty-text="暂无云端草稿">
          <el-table-column prop="title" label="标题" min-width="200" />
          <el-table-column prop="productId" label="产品" width="100" />
          <el-table-column label="更新" width="168">
            <template #default="{ row }">{{ row.updatedAt?.slice(0, 16) ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="88">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDraft(row)">打开</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="余额流水" name="wallet">
        <p class="hint">共 {{ walletTotal }} 条记录</p>
        <el-table v-loading="logsLoading" :data="walletLogs" stripe size="small">
          <el-table-column label="时间" width="168">
            <template #default="{ row }">{{ row.createdAt?.slice(0, 19) ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ logTypeLabel(row) }}</template>
          </el-table-column>
          <el-table-column label="金额" width="96">
            <template #default="{ row }">
              <span :class="row.type === 'deduct' ? 'neg' : 'pos'">
                {{ row.type === 'deduct' ? '-' : '+' }}¥{{ Number(row.amount).toFixed(2) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="说明" min-width="160" show-overflow-tooltip />
          <el-table-column label="余额" width="96">
            <template #default="{ row }">¥{{ Number(row.balanceAfter).toFixed(2) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="礼包兑换" name="gift">
        <el-card shadow="never">
          <p class="hint">输入礼包码可兑换余额，每个码仅可使用一次。</p>
          <el-form label-width="88px" style="max-width: 420px">
            <el-form-item label="礼包码">
              <el-input v-model="giftCode" placeholder="请输入礼包码" clearable @keyup.enter="onRedeemGift" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="redeemingGift" @click="onRedeemGift">兑换</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="分享有礼" name="share">
        <el-card v-loading="shareLoading" shadow="never" class="card-block">
          <template v-if="share">
            <el-alert
              v-if="share.shareLinkRelative"
              type="warning"
              :closable="false"
              show-icon
              title="分享链接未配置完整域名"
              description="当前为相对路径。生产环境请设置 REFERRAL_FRONTEND_BASE_URL=https://你的域名/"
              class="share-alert"
            />
            <el-alert
              v-if="share.enabled === false"
              type="info"
              :closable="false"
              show-icon
              title="奖励暂未开启"
              description="当前仅统计邀请人数，余额奖励由平台在后台配置开启后生效。"
              class="share-alert"
            />
            <p class="rules">{{ share.rules }}</p>
            <p>我的邀请码：<strong>{{ share.referralCode }}</strong></p>
            <p>已成功邀请：<strong>{{ share.invitedCount }}</strong> 人</p>
            <el-input :model-value="share.shareLink" readonly>
              <template #append>
                <el-button @click="copyShareLink">复制链接</el-button>
              </template>
            </el-input>
          </template>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="VIP 权益" name="vip">
        <el-card v-loading="rulesLoading" shadow="never" class="card-block">
          <p class="hint">以下为平台当前生效的 VIP 免费额度与折扣规则（只读）。</p>
          <div v-for="[level, rules] in vipRulesGrouped" :key="level" class="vip-group">
            <h4>VIP {{ level }}</h4>
            <ul>
              <li v-for="r in rules" :key="r.taskType">
                {{ r.taskType }}：每日免费 {{ r.dailyFree }} 次，付费折扣 {{ r.discountPercent }}%
              </li>
            </ul>
          </div>
          <el-empty v-if="!vipRulesGrouped.length && !rulesLoading" description="暂无规则数据" />
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.user-account {
  max-width: 920px;
  margin: 0 auto;
}

.card-block {
  margin-bottom: 16px;
}

.card-block p {
  margin: 8px 0;
}

.quick-links {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.hint {
  font-size: 13px;
  color: var(--xw-muted);
  margin: 0 0 12px;
}

.share-alert {
  margin-bottom: 12px;
}

.prefs-form {
  max-width: 480px;
}

.ml8 {
  margin-left: 8px;
}

.pos {
  color: #16a34a;
}

.neg {
  color: #dc2626;
}

.vip-group h4 {
  margin: 12px 0 6px;
  font-size: 15px;
}

.vip-group ul {
  margin: 0 0 12px;
  padding-left: 20px;
  font-size: 14px;
  color: #334155;
}

.rules {
  line-height: 1.6;
  color: #475569;
}
</style>

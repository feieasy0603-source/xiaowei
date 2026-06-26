<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import LiteratureList from '@/components/iw/literature/LiteratureList.vue'
import NavFooter from '@/components/iw/common/NavFooter.vue'
import { useApiEnabled } from '@/api/http'
import { requireLogin } from '@/composables/useRequireLogin'
import { usePaperStore } from '@/stores/paper'

const emit = defineEmits<{ prev: []; next: [] }>()

const paperStore = usePaperStore()
const exportDrawer = ref(false)

const literature = computed({
  get: () => paperStore.draft?.literature ?? [],
  set: (v) => paperStore.setLiterature(v),
})

function copyGbt() {
  const text = literature.value.map((x) => x.gbtCitation).join('\n')
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制 GB/T 格式文献')
  })
}

function clearExport() {
  paperStore.setLiterature([])
  ElMessage.info('已清空导出列表')
}

async function onNext() {
  const n = literature.value.length
  if (n === 0) {
    ElMessage.warning('请至少添加 1 篇文献后再继续')
    return
  }
  if (n < 4) {
    try {
      await ElMessageBox.confirm(
        `当前仅 ${n} 篇文献，建议至少 4 篇以保证生成质量，仍要继续吗？`,
        '文献偏少',
        { type: 'warning', confirmButtonText: '继续', cancelButtonText: '返回添加' },
      )
    } catch {
      return
    }
  }
  if (useApiEnabled()) {
    if (!(await requireLogin())) return
    if (!(await paperStore.persistDraft())) return
  }
  emit('next')
}
</script>

<template>
  <div class="step-literature">
    <section class="xw-card">
      <h2 class="xw-section-title">文献检索</h2>
      <p class="xw-hint">基于平台文献库关键词检索 · 支持 GB/T 7714 引用格式</p>

      <LiteratureList v-model="literature" :default-keyword="paperStore.title" />

      <div class="export-bar">
        <el-button type="primary" @click="exportDrawer = true">
          导出列表 {{ literature.length }}
        </el-button>
      </div>
    </section>

    <NavFooter show-prev show-next @prev="emit('prev')" @next="onNext" />

    <el-drawer v-model="exportDrawer" title="文献导出列表" size="480px">
      <p class="xw-hint drawer-hint">GB/T 7714-2015 格式</p>
      <ul class="export-list">
        <li v-for="item in literature" :key="item.id">{{ item.gbtCitation }}</li>
      </ul>
      <el-empty v-if="!literature.length" description="暂无选中文献" />
      <template #footer>
        <el-button @click="copyGbt">一键复制 GB/T 格式</el-button>
        <el-button type="danger" plain @click="clearExport">全部删除</el-button>
        <el-button @click="exportDrawer = false">关闭</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.export-bar {
  margin-top: 16px;
}

.drawer-hint {
  margin-bottom: 12px;
}

.export-list {
  list-style: none;
  font-size: 13px;
  line-height: 1.8;
}

.export-list li {
  padding: 10px 0;
  border-bottom: 1px solid var(--xw-border);
}
</style>

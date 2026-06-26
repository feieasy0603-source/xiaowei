<script setup lang="ts">
import FormRow from '@/components/home/shared/FormRow.vue'
import { usePaperStore } from '@/stores/paper'

defineProps<{ proLinkText?: string }>()
const emit = defineEmits<{ pro: [] }>()

const paperStore = usePaperStore()

const levels = [
  { value: 'provincial', label: '省级期刊' },
  { value: 'national', label: '国家级期刊' },
  { value: 'core', label: '核心期刊' },
]

function syncLevel(v: string) {
  const d = paperStore.draft!
  const label = levels.find((l) => l.value === v)?.label ?? v
  paperStore.updateDraft({ meta: { ...d.meta, category: label } })
}

function currentLevel(): string {
  const cat = paperStore.draft?.meta.category ?? ''
  const hit = levels.find((l) => l.label === cat || l.value === cat)
  return hit?.value ?? 'national'
}
</script>

<template>
  <FormRow label="选择期刊等级">
    <div class="row-inner">
      <el-radio-group
        class="compact-radios compact-radios--loose"
        :model-value="currentLevel()"
        @update:model-value="syncLevel"
      >
        <el-radio v-for="l in levels" :key="l.value" :value="l.value">
          {{ l.label }}
        </el-radio>
      </el-radio-group>
      <button v-if="proLinkText" type="button" class="pro-link" @click="emit('pro')">
        {{ proLinkText }}
      </button>
    </div>
  </FormRow>
</template>

<style scoped>
.row-inner {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 32px;
  gap: 24px;
}

.row-inner .compact-radios {
  flex: 0 1 auto;
}

.pro-link {
  margin-left: auto;
  border: none;
  background: none;
  color: var(--xw-primary);
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
  padding: 4px 0;
}

.pro-link:hover {
  text-decoration: underline;
}

@media (max-width: 720px) {
  .pro-link {
    margin-left: 0;
    width: 100%;
    text-align: left;
  }
}
</style>

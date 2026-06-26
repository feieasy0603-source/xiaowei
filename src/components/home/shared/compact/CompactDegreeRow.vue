<script setup lang="ts">
import FormRow from '@/components/home/shared/FormRow.vue'
import { usePaperStore } from '@/stores/paper'

defineProps<{
  proLinkText?: string
  options?: string[]
}>()

const emit = defineEmits<{ pro: [] }>()

const paperStore = usePaperStore()
const degrees = ['专科', '本科', '硕士']

function syncDegree(v: string) {
  const d = paperStore.draft!
  paperStore.updateDraft({ meta: { ...d.meta, degree: v } })
}
</script>

<template>
  <FormRow label="选择学历">
    <div class="degree-row">
      <el-radio-group
        class="compact-radios compact-radios--loose"
        :model-value="paperStore.draft?.meta.degree"
        @update:model-value="syncDegree"
      >
        <el-radio v-for="d in options ?? degrees" :key="d" :value="d">{{ d }}</el-radio>
      </el-radio-group>
      <button v-if="proLinkText" type="button" class="pro-link" @click="emit('pro')">
        {{ proLinkText }}
      </button>
    </div>
  </FormRow>
</template>

<style scoped>
.degree-row {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  width: 100%;
  min-height: 32px;
  gap: 24px;
}

.degree-row .compact-radios {
  flex: 0 1 auto;
}

.pro-link {
  flex: 0 0 auto;
  margin-left: auto;
  border: none;
  background: none;
  color: var(--xw-primary);
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
  padding: 4px 0;
  text-align: right;
}

.pro-link:hover {
  text-decoration: underline;
}

@media (max-width: 720px) {
  .degree-row {
    flex-wrap: wrap;
    row-gap: 10px;
  }

  .pro-link {
    margin-left: 0;
    width: 100%;
    text-align: right;
  }
}
</style>

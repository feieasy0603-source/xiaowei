<script setup lang="ts">
import { computed } from 'vue'
import HomeWriting from '@/components/home/HomeWriting.vue'
import ProEditionPage from '@/components/home/ProEditionPage.vue'
import StepTitle from '@/components/iw/steps/StepTitle.vue'
import StepLiterature from '@/components/iw/steps/StepLiterature.vue'
import StepOutline from '@/components/iw/steps/StepOutline.vue'
import StepPreview from '@/components/iw/steps/StepPreview.vue'
import { useAppStore } from '@/stores/app'
import { usePaperRoute } from '@/composables/usePaperRoute'

const appStore = useAppStore()
const { step, wizardMode, goToStep } = usePaperRoute()

const showPreview = computed(() => step.value === 3)
const showPro = computed(() => appStore.proEditionOpen && step.value === 0)
const showWizardTitle = computed(() => wizardMode.value && step.value === 0)
const showHome = computed(
  () => step.value === 0 && !showPro.value && !showWizardTitle.value,
)
const showLiterature = computed(() => step.value === 1)
const showOutlineStep = computed(() => step.value === 2)

function onPrev() {
  if (step.value === 3) {
    goToStep(2, true)
  }
}
</script>

<template>
  <div class="iw-content">
    <HomeWriting v-show="showHome" />
    <ProEditionPage v-show="showPro" />
    <StepTitle v-show="showWizardTitle" @next="goToStep(1)" />
    <StepLiterature v-show="showLiterature" @prev="goToStep(0, true)" @next="goToStep(2)" />
    <StepOutline v-show="showOutlineStep" @prev="goToStep(1, true)" @next="goToStep(3)" />
    <div v-show="showPreview" class="preview-wrap xw-card">
      <StepPreview @prev="onPrev" />
    </div>
  </div>
</template>

<style scoped>
.iw-content {
  min-height: 400px;
}

.preview-wrap {
  padding: 20px 24px;
}
</style>

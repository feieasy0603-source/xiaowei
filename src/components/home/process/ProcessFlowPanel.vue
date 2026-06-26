<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ProcessVariant } from '@/types/product'
import {
  illustrationKeyFor,
  preloadIllustration,
  resolveIllustrationUrls,
} from '@/mocks/processIllustrations'
import ProcessFlowArt from '@/components/home/process/ProcessFlowArt.vue'
import { useAppStore } from '@/stores/app'

const props = defineProps<{
  variant: ProcessVariant
  label: string
}>()

const appStore = useAppStore()
const imgSrc = ref<string>()
const useArt = ref(false)
const imgLoaded = ref(false)

const urls = computed(() =>
  resolveIllustrationUrls(props.variant, appStore.activeMenuId),
)

function initImage() {
  imgLoaded.value = false
  if (!urls.value) {
    imgSrc.value = undefined
    useArt.value = true
    return
  }
  const key = illustrationKeyFor(props.variant, appStore.activeMenuId)
  if (key) preloadIllustration(key)
  imgSrc.value = urls.value.primary
  useArt.value = false
}

function onImgError() {
  if (urls.value && imgSrc.value === urls.value.primary) {
    imgSrc.value = urls.value.fallback
    return
  }
  useArt.value = true
}

function onImgLoad() {
  imgLoaded.value = true
}

initImage()

watch(
  () => [props.variant, appStore.activeMenuId] as const,
  () => initImage(),
)
</script>

<template>
  <div class="process-panel">
    <div v-if="!useArt && imgSrc" class="process-illustration">
      <div v-if="!imgLoaded" class="img-skeleton" />
      <img
        :src="imgSrc"
        :alt="label"
        class="illustration-img"
        :class="{ loaded: imgLoaded }"
        loading="eager"
        decoding="async"
        @load="onImgLoad"
        @error="onImgError"
      />
    </div>
    <ProcessFlowArt v-else :variant="variant" :label="label" />
  </div>
</template>

<style scoped>
.process-panel {
  width: 100%;
}

.process-illustration {
  position: relative;
  width: 100%;
  max-width: var(--xw-process-width);
  margin: 0 auto;
  min-height: 280px;
}

.img-skeleton {
  position: absolute;
  inset: 0;
  border-radius: 12px;
  background: linear-gradient(
    110deg,
    rgb(226 232 240 / 40%) 8%,
    rgb(241 245 249 / 70%) 18%,
    rgb(226 232 240 / 40%) 33%
  );
  background-size: 200% 100%;
  animation: shimmer 1.2s linear infinite;
}

@keyframes shimmer {
  to {
    background-position-x: -200%;
  }
}

.illustration-img {
  display: block;
  width: 100%;
  height: auto;
  object-fit: contain;
  user-select: none;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.illustration-img.loaded {
  opacity: 1;
}
</style>

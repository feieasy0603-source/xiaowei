<script setup lang="ts">
import { computed } from 'vue'
import { useProductsStore } from '@/stores/products'
import { useAppStore } from '@/stores/app'
import FormGraduation from '@/components/home/forms/FormGraduation.vue'
import FormJournal from '@/components/home/forms/FormJournal.vue'
import FormRevise from '@/components/home/forms/FormRevise.vue'
import FormReview from '@/components/home/forms/FormReview.vue'
import FormAigc from '@/components/home/forms/FormAigc.vue'
import FormProposal from '@/components/home/forms/FormProposal.vue'
import FormCourse from '@/components/home/forms/FormCourse.vue'
import FormTask from '@/components/home/forms/FormTask.vue'
import FormParaphrase from '@/components/home/forms/FormParaphrase.vue'
import FormGeneric from '@/components/home/forms/FormGeneric.vue'
import FormUpload from '@/components/home/forms/FormUpload.vue'
import FormPpt from '@/components/home/forms/FormPpt.vue'
import type { Component } from 'vue'
import type { FormVariant } from '@/types/product'

const appStore = useAppStore()
const productsStore = useProductsStore()
const product = computed(() => productsStore.getProduct(appStore.activeMenuId))

const formMap: Record<FormVariant, Component> = {
  graduation: FormGraduation,
  journal: FormJournal,
  revise: FormRevise,
  review: FormReview,
  aigc: FormAigc,
  proposal: FormProposal,
  course: FormCourse,
  task: FormTask,
  paraphrase: FormParaphrase,
  generic: FormGeneric,
  upload: FormUpload,
  ppt: FormPpt,
}
</script>

<template>
  <component :is="formMap[product.formVariant]" :key="product.id" :product="product" />
</template>

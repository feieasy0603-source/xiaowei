import { defineStore } from 'pinia'
import { ref } from 'vue'

/** 首页快捷任务（改稿/降重等）支付弹窗全局状态 */
export const useTaskPayStore = defineStore('taskPay', () => {
  const visible = ref(false)
  const orderId = ref<number>()
  const productId = ref<string>()
  const paperId = ref<string>()
  const degree = ref<string>()
  const wordCount = ref<number>()
  const modelType = ref<string>()

  function open(params: {
    orderId: number
    productId: string
    paperId?: string
    degree?: string
    wordCount?: number
    modelType?: string
  }) {
    orderId.value = params.orderId
    productId.value = params.productId
    paperId.value = params.paperId
    degree.value = params.degree
    wordCount.value = params.wordCount
    modelType.value = params.modelType
    visible.value = true
  }

  function close() {
    visible.value = false
    orderId.value = undefined
    productId.value = undefined
    paperId.value = undefined
    degree.value = undefined
    wordCount.value = undefined
    modelType.value = undefined
  }

  return {
    visible,
    orderId,
    productId,
    paperId,
    degree,
    wordCount,
    modelType,
    open,
    close,
  }
})

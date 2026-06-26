import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchProducts } from '@/api/modules/products'
import { products as mockProducts, getProduct as mockGetProduct } from '@/mocks/products'
import { useApiEnabled } from '@/api/http'
import type { ProductConfig } from '@/types/product'

export const useProductsStore = defineStore('products', () => {
  const list = ref<ProductConfig[]>([...mockProducts])
  const loaded = ref(false)

  async function loadFromApi() {
    if (!useApiEnabled()) return
    try {
      list.value = await fetchProducts()
      loaded.value = true
    } catch {
      list.value = [...mockProducts]
    }
  }

  function getProduct(id: string): ProductConfig {
    return list.value.find((p) => p.id === id) ?? mockGetProduct(id)
  }

  return { list, loaded, loadFromApi, getProduct }
})

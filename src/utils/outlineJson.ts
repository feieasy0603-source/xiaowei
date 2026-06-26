import { outlineToText } from '@/composables/useOutlineNumbering'
import type { OutlineNode } from '@/types/paper'
import { buildOutlineTextFromTitle } from '@/utils/outlineFromTitle'

/** 将后端 outlineJson 转为提纲编辑区文本 */
export function outlineJsonToText(raw: string, fallbackTitle: string, depth: 2 | 3): string {
  const trimmed = raw?.trim()
  if (!trimmed) return buildOutlineTextFromTitle(fallbackTitle, depth)

  try {
    const data = JSON.parse(trimmed) as unknown
    if (typeof data === 'string' && data.trim()) return data.trim()
    if (Array.isArray(data) && data.length > 0) {
      const nodes = data as OutlineNode[]
      if (nodes[0] && 'title' in nodes[0] && 'level' in nodes[0]) {
        return outlineToText(nodes)
      }
    }
  } catch {
    /* 非 JSON，当作纯文本提纲 */
  }

  if (trimmed.includes('第') && trimmed.includes('章')) return trimmed
  return buildOutlineTextFromTitle(fallbackTitle, depth)
}

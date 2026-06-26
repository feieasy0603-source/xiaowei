import type { OutlineNode } from '@/types/paper'

const CN_NUMS = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十']

export function formatOutlineLabel(nodes: OutlineNode[], index: number): string {
  const node = nodes[index]
  if (!node) return ''

  if (node.level === 1) {
    const n = nodes.slice(0, index + 1).filter((x) => x.level === 1).length
    const num = CN_NUMS[n - 1] ?? String(n)
    return `第${num}章 ${node.title}`
  }

  if (node.level === 2) {
    let chapter = 0
    let section = 0
    for (let i = 0; i <= index; i++) {
      const cur = nodes[i]
      if (cur.level === 1) {
        chapter++
        section = 0
      } else if (cur.level === 2) {
        section++
      }
    }
    return `${chapter}.${section} ${node.title}`
  }

  let chapter = 0
  let section = 0
  let subsection = 0
  for (let i = 0; i <= index; i++) {
    const cur = nodes[i]
    if (cur.level === 1) {
      chapter++
      section = 0
      subsection = 0
    } else if (cur.level === 2) {
      section++
      subsection = 0
    } else if (cur.level === 3) {
      subsection++
    }
  }
  return `${chapter}.${section}.${subsection} ${node.title}`
}

export function outlineToText(nodes: OutlineNode[]): string {
  return nodes.map((_, i) => formatOutlineLabel(nodes, i)).join('\n')
}

export function adjustLevel(node: OutlineNode, delta: number): OutlineNode {
  const next = Math.min(3, Math.max(1, node.level + delta)) as 1 | 2 | 3
  return { ...node, level: next }
}

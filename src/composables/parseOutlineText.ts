import { nanoid } from 'nanoid'
import type { OutlineNode } from '@/types/paper'

/**
 * 将专业版「提纲编辑区」多行文本解析为 outline 节点（与后端 PaperDraftHelper 编号规则一致）
 */
export function parseOutlineText(text: string): OutlineNode[] {
  const nodes: OutlineNode[] = []
  for (const line of text.split('\n')) {
    const t = line.trim()
    if (!t) continue

    let level: 1 | 2 | 3 = 1
    let title = t

    const m3 = t.match(/^(\d+)\.(\d+)\.(\d+)\s+(.+)$/)
    const m2 = t.match(/^(\d+)\.(\d+)\s+(.+)$/)
    const m1 = t.match(/^第[一二三四五六七八九十0-9]+章\s+(.+)$/)

    if (m3) {
      level = 3
      title = m3[4]!
    } else if (m2) {
      level = 2
      title = m2[3]!
    } else if (m1) {
      level = 1
      title = m1[1]!
    } else {
      continue
    }

    nodes.push({
      id: nanoid(8),
      level,
      title: title.trim(),
      wordCount: 800,
      zhRefs: 0,
      enRefs: 0,
    })
  }
  return nodes
}

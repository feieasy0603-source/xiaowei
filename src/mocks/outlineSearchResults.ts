export type OutlineSearchItem = {
  id: number | string
  title: string
  degree: string
  depth: 2 | 3
  outlineJson?: string
}

/** 离线搜索提纲：根据标题与学历返回可选项 */
export function mockOutlineSearchResults(
  title: string,
  degree: string,
): OutlineSearchItem[] {
  const t = title.trim()
  return [
    {
      id: 1,
      title: `${t} — 标准结构提纲`,
      degree,
      depth: 2,
    },
    {
      id: 2,
      title: `${t} — 理论实证结合提纲`,
      degree,
      depth: 3,
    },
  ]
}

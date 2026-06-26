/** 根据用户输入关键词生成推荐标题（离线 / Mock） */
export function buildTitlesFromKeyword(keyword: string): string[] {
  const k = keyword.trim().replace(/\s+/g, '')
  if (k.length < 2) return []

  const suffixes = [
    '系统中基于深度学习的智能分析与优化研究',
    '环境下的关键技术与应用前景探讨',
    '领域创新发展的影响因素及对策研究',
    '背景下可持续发展路径与政策研究',
  ]

  return suffixes.map((s) => {
    const title = `${k}${s}`
    return title.length > 50 ? title.slice(0, 50) : title
  })
}

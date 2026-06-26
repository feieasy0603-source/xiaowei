import type { OutlineNode } from '@/types/paper'
import { nanoid } from 'nanoid'

function node(level: 1 | 2 | 3, title: string): OutlineNode {
  return {
    id: nanoid(8),
    level,
    title,
    wordCount: level === 1 ? 800 : level === 2 ? 400 : 200,
    zhRefs: 4,
    enRefs: 2,
  }
}

export const outlineTemplate2Level: OutlineNode[] = [
  node(1, '绪论'),
  node(2, '研究背景与意义'),
  node(2, '国内外研究现状'),
  node(2, '研究内容与方法'),
  node(1, '相关理论与技术'),
  node(2, '核心概念界定'),
  node(2, '关键技术介绍'),
  node(1, '系统设计与实现'),
  node(2, '需求分析'),
  node(2, '总体架构设计'),
  node(2, '功能模块实现'),
  node(1, '实验与结果分析'),
  node(2, '实验环境'),
  node(2, '结果讨论'),
  node(1, '结论与展望'),
]

export const outlineTemplate3Level: OutlineNode[] = [
  node(1, '绪论'),
  node(2, '研究背景'),
  node(3, '选题背景'),
  node(3, '研究意义'),
  node(2, '文献综述(国内研究现状/文献综述)'),
  node(2, '研究内容与方法'),
  node(1, '理论基础'),
  node(2, '相关理论'),
  node(3, '理论框架'),
  node(1, '系统实现'),
  node(2, '需求分析'),
  node(2, '系统设计'),
  node(3, '数据库设计'),
  node(3, '接口设计'),
  node(1, '总结与展望'),
]

export const categories = [
  '教育经管',
  '市场、人力',
  '文学艺术体育农林',
  '畜牧业电器电力、机械',
  '电子信息科学技术',
  '建筑工程、轻工业化工',
  '传媒、科普能源环保',
  '矿业、旅游',
  '党建法律、水利、食品',
  '医药卫生交通运输',
]

export const degrees = ['专科', '本科', '硕士', '博士']

export const paperTypes = ['毕业论文', '课程论文', '开题报告', '文献综述', '实训报告']

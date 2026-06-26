export interface SchoolOption {
  id: string
  name: string
}

export const schools: SchoolOption[] = [
  { id: 'pku', name: '北京大学' },
  { id: 'thu', name: '清华大学' },
  { id: 'fudan', name: '复旦大学' },
  { id: 'sjtu', name: '上海交通大学' },
  { id: 'zju', name: '浙江大学' },
  { id: 'nju', name: '南京大学' },
  { id: 'whu', name: '武汉大学' },
  { id: 'sysu', name: '中山大学' },
  { id: 'other', name: '其他高校（联系客服添加）' },
]

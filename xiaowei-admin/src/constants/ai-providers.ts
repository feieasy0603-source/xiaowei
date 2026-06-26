/** 与后端 AiProviderPreset 对齐 */
export const AI_PROVIDERS = [
  { id: 'openai', label: 'OpenAI', defaultBaseUrl: 'https://api.openai.com/v1', defaultModel: 'gpt-4o-mini', hint: '官方 API' },
  {
    id: 'deepseek',
    label: 'DeepSeek',
    defaultBaseUrl: 'https://api.deepseek.com',
    defaultModel: 'deepseek-v4-flash',
    hint: '模型：deepseek-v4-flash / deepseek-v4-pro；Base：https://api.deepseek.com',
  },
  { id: 'qwen', label: '通义千问', defaultBaseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', defaultModel: 'qwen-plus', hint: '阿里云 DashScope 兼容模式' },
  { id: 'moonshot', label: 'Moonshot', defaultBaseUrl: 'https://api.moonshot.cn/v1', defaultModel: 'moonshot-v1-8k', hint: 'Kimi API' },
  { id: 'zhipu', label: '智谱 AI', defaultBaseUrl: 'https://open.bigmodel.cn/api/paas/v4', defaultModel: 'glm-4-flash', hint: '智谱 OpenAI 兼容' },
  { id: 'azure', label: 'Azure OpenAI', defaultBaseUrl: 'https://YOUR-RESOURCE.openai.azure.com', defaultModel: 'YOUR-DEPLOYMENT', hint: '部署名填默认模型' },
  { id: 'custom', label: '自定义网关', defaultBaseUrl: 'https://your-gateway.example.com/v1', defaultModel: 'your-model', hint: '任意 OpenAI 兼容接口' },
] as const

export function providerLabel(id: string) {
  return AI_PROVIDERS.find((p) => p.id === id)?.label ?? id
}

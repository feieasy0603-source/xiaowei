# xiaowei-admin

小微智能写作 **管理端**（Vue 3 + Vite + Element Plus），对接 `xiaowei-server` 的 `/admin/**` API。

## 开发

```bash
npm install
npm run dev
```

- 地址：http://localhost:5174/admin/login  
- 默认账号：`admin` / `admin123`（需先启动后端）  
- 生产构建：`npm run build`（输出至 `dist/`，由 Nginx 挂载在 `/admin/`）

环境变量见 `.env.production.example`：

| 变量 | 说明 |
|------|------|
| `VITE_API_BASE` | API 前缀，默认 `/api` |

## 功能模块

### 概览

| 菜单 | 说明 |
|------|------|
| AI 运营看板 | 用户/订单/任务统计、AI Token 用量 |
| 生产部署向导 | 就绪检测、env 模板、Nginx 片段、关闭 AI Mock |
| 站点品牌 | Logo、favicon、站点标题与 slogan |

### AI 生成业务

| 菜单 | 说明 |
|------|------|
| 生成任务 | 分页筛选、payload/result 详情、失败重试 |
| 论文草稿 | 用户 lunwen 草稿与向导进度 |
| 文献库 | 本地文献 CRUD、JSON 批量导入 |
| 提纲模板 | 提纲搜索数据源维护 |
| AI 产品 | taskType、flowType、上下架 |
| AI 模型池 | 多模型路由、健康检查、并发池 |

### 交易运营

| 菜单 | 说明 |
|------|------|
| 用户管理 | 充值/扣款、VIP 等级、配额重置、钱包流水 |
| VIP 配额 | 按等级 × 任务类型配置免费次数与折扣 |
| 订单管理 | 筛选、标记已付、退款（取消进行中任务） |
| 充值订单 | `WR*` 余额充值单（pending/paid） |
| 支付流水 | `payment_records` 只读审计（微信/余额等） |
| 渠道管理 | 推广码 dCode、分成比例 |
| 礼包码 | 生成、过期时间 |
| 分享奖励 | 邀请人/被邀请人奖励金额与规则文案 |
| 在线客服 | 电话/微信/QQ 等联系方式 |
| 学校模板 | 导出 DOCX 校名标注 |

## 与后端关系

- 所有请求经 `adminFetch` 携带管理员 JWT（`Authorization: Bearer`）
- 401 时跳转登录页
- 完整 API 列表见 [xiaowei-server/README.md](../xiaowei-server/README.md)

## Docker

根目录 `docker compose up` 后访问 http://localhost/admin/

# 小微智能写作

一个面向论文与文档生成场景的全栈 AI 写作平台。项目覆盖用户端、管理端和 Spring Boot 后端，内置产品配置、AI 模型池、订单支付、钱包充值、VIP 配额、渠道分销、文献库、提纲模板、任务进度与交付文件下载等完整业务链路。

> 适合用作 AI 写作 SaaS、校园论文辅助平台、文档生成商业系统或 Spring Boot + Vue 全栈项目参考。

## 项目亮点

- **完整商业闭环**：从用户下单、余额/微信/支付宝支付、AI 任务生成、TXT/DOCX 交付，到后台订单、退款、流水审计。
- **真实后端优先**：默认使用 MySQL，关闭 H2/dev mock 依赖；AI Mock 与支付 Mock 均通过配置硬开关控制，生产环境可明确禁用。
- **多模型 AI 运营**：管理端支持 OpenAI、DeepSeek、通义、Moonshot、智谱、Azure 和自定义 OpenAI 兼容网关；支持模型池、健康探测、任务路由和并发控制。
- **可运营的管理后台**：订单、用户、钱包、VIP、渠道、礼包码、产品、任务、文献、提纲、客服、站点品牌都可在后台维护。
- **用户体验完整**：用户端包含 20+ 写作产品、专业版提纲、四步写作向导、实时任务进度、订单查询、用户中心、分享邀请和站点品牌配置。
- **部署友好**：提供 Docker Compose、Nginx 配置、MySQL 初始化、环境变量模板和生产部署向导。

## 在线能力概览

| 角色 | 能力 |
|------|------|
| 用户端 | 产品选择、论文/文档生成、专业版提纲、标题推荐、文献检索、任务进度、订单查询、交付下载、钱包、VIP、分享邀请 |
| 管理端 | AI 运营看板、模型池、订单与退款、充值订单、支付流水、用户与钱包、VIP 配额、渠道、礼包码、产品、文献库、提纲模板、客服、品牌设置 |
| 后端 API | JWT 鉴权、MySQL/Flyway、AI 网关、任务队列、SSE/轮询进度、支付回调、文件存储、钱包账务、并发锁与幂等保护 |

## 技术栈

| 层级 | 技术 |
|------|------|
| 用户端 | Vue 3, Vite, Pinia, Vue Router, Element Plus |
| 管理端 | Vue 3, Vite, Element Plus |
| 后端 | Spring Boot 3, Spring Security, Spring Data JPA, Flyway, MySQL, JWT, SpringDoc |
| AI 接入 | OpenAI-compatible Chat Completions, 多模型池, 健康检查, 任务路由 |
| 支付 | 微信 Native/V3 回调, 支付宝当面付/异步通知, 余额支付, VIP 免费额度 |
| 部署 | Docker Compose, Nginx, MySQL 8, 环境变量模板 |

## 系统架构

```text
用户浏览器
  ├─ 用户端 Vue (/)
  └─ 管理端 Vue (/admin)
        │
        ▼
      Nginx
        │
        ▼
Spring Boot API (/api)
  ├─ Auth / User / Wallet / VIP
  ├─ Orders / Payments / Refunds
  ├─ Jobs / AI Gateway / Model Pool
  ├─ Papers / Files / Deliveries
  ├─ Products / Channels / Gift Codes
  └─ Admin / Deploy / Branding
        │
        ├─ MySQL + Flyway
        ├─ Local file storage
        ├─ OpenAI-compatible providers
        └─ WeChat / Alipay callbacks
```

## 目录结构

```text
xiaowei/
├── src/                    # 用户端 Vue 3 应用
├── xiaowei-admin/          # 管理端 Vue 3 应用
├── xiaowei-server/         # Spring Boot API 服务
├── deploy/                 # Nginx、MySQL 初始化、部署说明
├── docker-compose.yml      # 本地/服务器一键编排
├── Dockerfile.web          # 用户端镜像
└── README.md               # 项目总览
```

## 快速开始

### 前置要求

- Node.js 20+
- JDK 17+
- Maven 3.8+
- MySQL 8+

### 1. 初始化数据库

```bash
mysql -uroot -p < deploy/mysql/init.sql
```

该脚本只创建数据库，表结构由后端启动时通过 Flyway 自动迁移。

### 2. 启动后端

```bash
cd xiaowei-server
cp .env.example .env
# 编辑 .env：至少配置 MYSQL_PASSWORD、JWT_SECRET 等
mvn spring-boot:run
```

默认地址：

- API: http://localhost:8080/api
- Swagger(dev): http://localhost:8080/api/swagger-ui/index.html

默认管理账号：

| 账号 | 密码 |
|------|------|
| `admin` | `admin123` |

### 3. 启动用户端

```bash
cp .env.example .env
npm install
npm run dev
```

访问：

```text
http://localhost:5173/#/iw/intelligentWriting/0
```

### 4. 启动管理端

```bash
cd xiaowei-admin
npm install
npm run dev
```

访问：

```text
http://localhost:5174/admin/
```

## Docker 一键部署

```bash
cp .env.example .env
# 编辑 .env：数据库密码、JWT_SECRET、支付回调密钥、AI/支付配置等
docker compose up -d --build
```

| 服务 | 地址 |
|------|------|
| 用户端 | http://localhost/ |
| 管理端 | http://localhost/admin/ |
| API | http://localhost/api/ |

更多部署细节见 [deploy/README.md](./deploy/README.md)。

## 核心业务流程

### 订单与 AI 生成

```text
选择产品
  → 创建草稿/订单
  → 报价与 VIP 配额计算
  → 支付或免费额度抵扣
  → 自动创建 AI Job
  → 模型池调度生成
  → 写入预览结果
  → 生成 TXT/DOCX 交付文件
  → 用户下载 / 管理端审计
```

### 支付方式

| 场景 | 说明 |
|------|------|
| 余额支付 | 扣减用户钱包余额，生成钱包流水 |
| VIP 免费额度 | 按等级与任务类型计算每日免费次数 |
| 微信 Native | 返回二维码内容，依赖微信 V3 回调确认到账 |
| 支付宝当面付 | 返回扫码支付内容，依赖支付宝异步通知确认到账 |
| 后台标记已付 | 管理端人工处理特殊订单，并自动触发生成任务 |
| 退款 | 余额支付退回钱包；第三方支付按配置调用对应退款流程 |

## AI 模型池

管理端 `AI 模型池` 支持：

- 多供应商配置：OpenAI、DeepSeek、通义、Moonshot、智谱、Azure、自定义网关
- 多接入点并发池
- 轮询 / 最少繁忙策略
- 任务类型绑定模型
- 健康检查和故障跳过
- Token 用量统计
- 一键测试单模型或全部模型

后端按 OpenAI-compatible `/v1/chat/completions` 协议接入，适合替换不同大模型服务商。

## 管理端模块

| 模块 | 说明 |
|------|------|
| AI 运营看板 | 用户、订单、任务、Token 用量、生产就绪状态 |
| 生产部署向导 | 检查环境变量、Mock 状态、支付与安全配置 |
| 站点品牌 | 用户端和管理端 Logo、标题、favicon、slogan |
| AI 产品 | 产品上下架、任务类型、流程类型、价格规则 |
| 生成任务 | 查看 payload/result、进度、失败原因、重试任务 |
| 论文草稿 | 查看用户草稿、向导进度、交付文件 |
| 文献库 | 本地文献 CRUD、JSON 批量导入、检索数据源 |
| 提纲模板 | 专业版提纲搜索模板维护 |
| 用户管理 | 用户资料、余额充值/扣款、VIP、配额、钱包流水 |
| 订单管理 | 订单筛选、详情、标记已付、退款、关联任务 |
| 充值订单 | 钱包充值订单与状态跟踪 |
| 支付流水 | `payment_records` 只读审计 |
| 渠道管理 | 推广码、渠道分成、佣金统计 |
| 礼包码 | 礼包码生成、启停、过期时间 |
| 分享奖励 | 邀请人/被邀请人奖励金额与规则文案 |
| 在线客服 | 电话、微信、QQ、二维码等配置 |
| 学校模板 | 学校格式模板维护 |

## 安全与生产建议

生产环境务必检查：

- `JWT_SECRET` 使用高强度随机值
- `PAY_CALLBACK_SECRET` 使用高强度随机值
- `PAYMENT_MOCK_ENABLED=false`
- `XIAOWEI_AI_MOCK=false`
- `XIAOWEI_AI_ALLOW_MOCK=false`
- 配置真实 MySQL，不使用 H2
- 配置 `CORS_ALLOWED_ORIGINS` 或使用同域 Nginx 反代
- 不提交 `.env`、上传文件、数据库文件、IDEA 本机运行配置

本仓库 `.gitignore` 已默认忽略：

- `.env*` 真实环境文件
- `node_modules/`
- `dist/`
- `target/`
- `.idea/`
- `.run/`
- `xiaowei-server/data/`

仅保留 `.env.example` / `.env.production.example` 作为配置模板。

## 常用命令

### 用户端

```bash
npm run dev
npm run build
npm run test
```

### 管理端

```bash
cd xiaowei-admin
npm run dev
npm run build
```

### 后端

```bash
cd xiaowei-server
mvn spring-boot:run
mvn -DskipTests package
```

## 环境变量入口

| 文件 | 用途 |
|------|------|
| `.env.example` | Docker Compose 总配置模板 |
| `.env.production.example` | 用户端生产构建模板 |
| `xiaowei-admin/.env.production.example` | 管理端生产构建模板 |
| `xiaowei-server/.env.example` | 后端本地开发模板 |
| `xiaowei-server/.env.sandbox.example` | 支付宝沙箱联调模板 |
| `xiaowei-server/.env.production.example` | 后端生产部署模板 |

## 文档

- [后端 API 与支付说明](./xiaowei-server/README.md)
- [管理端说明](./xiaowei-admin/README.md)
- [部署说明](./deploy/README.md)

## 当前状态

- 用户端生产构建通过
- 管理端生产构建通过
- 后端 Maven 打包通过
- 默认分支：`main`

## License

当前仓库未声明开源许可证。若要公开给他人复用，请先补充合适的 License。

# 小微智能写作（全栈）

Vue 3 用户端 + Spring Boot API + Vue 管理端：24 类产品、专业版提纲、四步写作向导、订单/钱包/VIP、分享邀请与站点品牌配置。

## 项目结构

```
xiaowei/                 # 用户端 (本目录)
xiaowei-server/          # Spring Boot API
xiaowei-admin/           # 管理端
deploy/                  # Nginx、MySQL 初始化、部署说明
docker-compose.yml       # 一键 Docker 部署
```

## 快速开始（本地）

### 1. 后端（JDK 17+、Maven、MySQL）

```bash
mysql -uroot -p < deploy/mysql/init.sql

cd xiaowei-server
cp .env.example .env   # 修改 MYSQL_PASSWORD 等
mvn spring-boot:run
```

- API: http://localhost:8080/api  
- Swagger（dev）: http://localhost:8080/api/swagger-ui/index.html  
- 详细 API 与支付说明见 [xiaowei-server/README.md](./xiaowei-server/README.md)

### 2. 用户端

```bash
cp .env.example .env
# VITE_USE_API=true  VITE_API_BASE=/api
npm install && npm run dev
```

http://localhost:5173/#/iw/intelligentWriting/0

### 3. 管理端

```bash
cd xiaowei-admin && npm install && npm run dev
```

http://localhost:5174/admin/ · 账号 `admin` / `admin123`

## Docker 部署

```bash
cp .env.example .env
# 编辑密钥与数据库密码；本地演示可将 PAYMENT_MOCK_ENABLED=true
docker compose up -d --build
```

| 入口 | 地址 |
|------|------|
| 用户端 | http://localhost/ |
| 管理端 | http://localhost/admin/ |
| API | http://localhost/api/ |

表结构由 Flyway 自动迁移；`deploy/mysql/init.sql` 仅创建数据库。运维清单见 [deploy/README.md](./deploy/README.md)。管理端 **生产部署向导**：`/admin/deploy`。

## 环境变量

### 用户端（`.env`）

| 变量 | 说明 |
|------|------|
| `VITE_API_BASE` | API 前缀，默认 `/api` |
| `VITE_USE_API` | `false` 时离线 mock（生产 build 请保持 `true`） |

### 后端（`xiaowei-server/.env`）

见 [xiaowei-server/.env.example](./xiaowei-server/.env.example)：`JWT_SECRET`、`PAY_CALLBACK_SECRET`、`REFERRAL_FRONTEND_BASE_URL`、`WECHAT_*`、`ALIPAY_*` 等。

| 模板文件 | 用途 |
|---------|------|
| `xiaowei-server/.env.example` | 本地开发 |
| `xiaowei-server/.env.sandbox.example` | 支付宝沙箱联调 |
| `xiaowei-server/.env.production.example` | 生产部署 |
| `.env.example` | Docker Compose |

## 功能概览

| 模块 | 能力 |
|------|------|
| **用户端** | 24 产品、专业版 `?pro=1`、四步向导、用户中心（草稿/流水/改密/礼包/分享/VIP 规则）、预览取消任务、Word/TXT 下载 |
| **API** | JWT 登录、草稿乐观锁、AI 提纲/文献（本地库 + OpenAlex）、Job + SSE 进度、微信 Native/V3 回调、余额/礼包 |
| **管理端** | 产品/渠道/订单/充值订单/用户/任务/文献/提纲/VIP 配额/礼包码/分享奖励/客服/品牌/学校/部署向导 |

## 任务类型 (task_type)

`paper_generate` · `revise` · `paraphrase` · `aigc_check` · `ppt_generate` · `file_translate` · `data_analysis`

## 技术栈

- 用户端: Vue 3, Vite, Pinia, Element Plus, Vue Router (hash)
- 后端: Spring Boot 3, JPA, Flyway, MySQL, JWT, SpringDoc
- 管理端: Vue 3, Element Plus

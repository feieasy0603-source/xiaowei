# 生产部署说明

本目录包含 Nginx 与 MySQL 初始化脚本，配合根目录 `docker-compose.yml` 使用。

## 目录

| 文件 | 用途 |
|------|------|
| `nginx.conf` | 统一入口：/ → 用户端，/admin → 管理端，/api → 后端 |
| `web-nginx.conf` | 用户端容器内 SPA + API 反代（含 SSE `proxy_buffering off`） |
| `admin-nginx.conf` | 管理端容器内 SPA 回退（刷新 `/admin/*` 深链不 404） |
| `mysql/init.sql` | 创建 `xiaowei` 数据库（**表结构由 Flyway 迁移**，见 `xiaowei-server/src/main/resources/db/migration/`） |

## 快速部署（Docker Compose）

```bash
cp .env.example .env
# 编辑 .env：至少修改 MYSQL_*、JWT_SECRET、PAY_CALLBACK_SECRET
docker compose up -d --build
```

| 入口 | 地址 |
|------|------|
| 用户端 | http://localhost/ |
| 管理端 | http://localhost/admin/ |
| API | http://localhost/api/ |

**注意**：请通过统一 Nginx（`:80`）访问。管理端容器不再单独暴露 `:5174`，避免 `/admin/assets` 路径不一致导致白屏。

### 本地演示 vs 生产

| 场景 | `PAYMENT_MOCK_ENABLED` | 说明 |
|------|------------------------|------|
| 本地 Docker 演示 | `true`（在 `.env` 中覆盖） | 模拟微信/支付宝扫码，无需商户号 |
| 生产上线 | `false` | 必须配置 `WECHAT_*` / `ALIPAY_*` 或仅使用余额/礼包码 |

Compose 默认读取根目录 `.env`；`docker-compose.yml` 不再硬编码密钥。

## 环境变量清单（后端 `xiaowei-server`）

生产启动前请至少配置：

```bash
SPRING_PROFILES_ACTIVE=prod,mysql
MYSQL_HOST=...
MYSQL_PASSWORD=...
JWT_SECRET=          # openssl rand -base64 32
PAY_CALLBACK_SECRET= # openssl rand -base64 24
REFERRAL_FRONTEND_BASE_URL=https://你的域名/
PAYMENT_MOCK_ENABLED=false
XIAOWEI_RATE_LIMIT_LOOKUP_MAX=30
```

`SPRING_PROFILES_ACTIVE=prod` 时，`ProductionStartupValidator` 会拒绝弱默认密钥启动。

### CORS（前后端分域时）

同域 Nginx 反代（用户端 `/`、管理端 `/admin/`、API `/api/`）**无需**配置 CORS。

若 API 与前端不同域，设置逗号分隔的允许来源：

```bash
CORS_ALLOWED_ORIGINS=https://你的域名,https://admin.你的域名
```

### 微信支付（可选）

启用 Native 扫码 + V3 异步回调时需补全：

```bash
WECHAT_PAY_ENABLED=true
WECHAT_APP_ID=
WECHAT_MCH_ID=
WECHAT_API_V3_KEY=        # 32 字节
WECHAT_SERIAL_NO=
WECHAT_PRIVATE_KEY_PEM=
WECHAT_NOTIFY_URL=https://你的域名/api/payments/callback/wechat
```

未配置微信时，用户仍可使用**余额支付**与**礼包码**。

### 支付宝当面付（可选）

启用扫码 + 异步回调时需补全（详见 `xiaowei-server/.env.production.example`）：

```bash
ALIPAY_PAY_ENABLED=true
ALIPAY_APP_ID=
ALIPAY_PRIVATE_KEY_PEM=         # 应用私钥 PKCS8
ALIPAY_PUBLIC_KEY_PEM=          # 支付宝公钥（非应用公钥）
ALIPAY_GATEWAY_URL=https://openapi.alipay.com/gateway.do
ALIPAY_NOTIFY_URL=https://你的域名/api/payments/callback/alipay
```

**沙箱联调**：复制 `xiaowei-server/.env.sandbox.example` → `.env`，填入沙箱密钥；网关改为 `https://openapi-sandbox.dl.alipaydev.com/gateway.do`；`notify` 地址需公网 HTTPS（可用 ngrok）。

Docker Compose 根目录 `.env` 同样支持上述 `ALIPAY_*` 变量（已写入 `docker-compose.yml`）。

### 文献外部补全（可选）

```bash
LITERATURE_OPENALEX_ENABLED=true
LITERATURE_OPENALEX_MIN_LOCAL=8
```

## Docker Compose 服务说明

| 服务 | 健康检查 | 持久化 |
|------|----------|--------|
| mysql | mysqladmin ping | `mysql_data` |
| server | `GET /api/products` | `upload_data` → `/app/data/uploads` |
| web / admin / nginx | HTTP 200 | — |

所有服务配置 `restart: unless-stopped`。

管理端 **生产部署向导**：登录后访问 `/admin/deploy`，可导出 env 模板与 Nginx 片段。

## Nginx SSE

`/api/jobs/*/stream` 为 Server-Sent Events，已在配置中设置：

- `proxy_buffering off`
- `proxy_read_timeout 3600s`

切勿在 API 路径上启用响应缓冲，否则生成进度会延迟或卡住。

## 健康检查

- API：`GET /api/products`（200）
- Swagger（仅非 prod）：`/api/swagger-ui/index.html`

# xiaowei-server

小微智能写作 API 服务（Spring Boot 3 + MySQL + Flyway + JWT）

## 要求

- JDK 17+（推荐 **JDK 17** 或 **21**；若用 JDK 22/23/24 需 Lombok 1.18.38+，见 `pom.xml`）
- Maven 3.8+

### 编译报错 `TypeTag :: UNKNOWN`？

IDE 使用的 **JDK 版本过新**，而 Spring Boot 自带的 **Lombok 版本偏旧**，无法在 JDK 22/23/24 上初始化注解处理器。

处理任选其一：

1. **推荐**：项目 SDK 改为 **JDK 17**（File → Project Structure → SDK）
2. 保持新 JDK：已在本项目 `pom.xml` 固定 `lombok.version=1.18.38`，执行 **Maven Reload** 后 **Build → Rebuild**
3. IntelliJ：安装/更新 **Lombok 插件**，Settings → Build → Compiler → **Enable annotation processing**

## 启动

### 使用本地 MySQL（推荐）

1. 确保 MySQL 已启动，并创建库：

```bash
mysql -uroot -p < deploy/mysql/init.sql
# 或: mysql -uroot -pmuxue123 -e "CREATE DATABASE IF NOT EXISTS xiaowei ..."
```

2. 配置连接（已提供模板）：

```bash
cd xiaowei-server
cp .env.example .env   # 若尚无 .env，按你的 root 密码修改 MYSQL_PASSWORD
```

3. 启动（会自动读取 `xiaowei-server/.env`，默认 `SPRING_PROFILES_ACTIVE=dev,mysql`）：

```bash
mvn spring-boot:run
```

- API 首页（可用链接）：http://localhost:8080/api/
- Swagger: http://localhost:8080/api/swagger-ui/index.html
- 前端页面（需先 `npm run dev`）：http://127.0.0.1:5173/#/iw/intelligentWriting/0

## 默认账号

| 类型 | 账号 | 密码 |
|------|------|------|
| 管理端 | admin | admin123 |
| 用户演示 | 调用 POST `/api/auth/demo` | — |

## 管理端 API（节选）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/users?page&phone&status` | 用户分页列表 |
| POST | `/admin/users/{id}/recharge` | 管理员充值 |
| POST | `/admin/users/{id}/deduct` | 管理员扣款 |
| GET | `/admin/users/{id}/wallet-logs` | 钱包流水 |
| PUT | `/admin/users/{id}` | 编辑用户 |
| GET | `/admin/orders?page&payStatus` | 订单分页 |
| GET | `/admin/wallet-recharges` | 余额充值订单 WR* |
| GET | `/admin/payment-records` | 支付流水（只读） |
| GET | `/admin/orders/{id}` | 订单详情（含 jobId） |
| POST | `/admin/orders/{id}/mark-paid` | 标记已付并自动 createJob |
| POST | `/admin/orders/{id}/refund` | 订单退款（余额退回钱包；微信调 V3 退款 API） |
| GET | `/admin/papers/{id}/files` | 草稿交付文件列表 |
| GET | `/admin/paper-files/{id}/download` | 管理端下载交付文件（需管理员 JWT） |
| GET | `/admin/vip-quotas` | VIP 配额规则列表 |
| POST | `/admin/vip-quotas` | 新增/编辑配额规则 |
| DELETE | `/admin/vip-quotas/{id}` | 删除配额规则 |
| GET | `/admin/users/{id}/quota` | 用户当日配额使用情况 |
| POST | `/admin/users/{id}/reset-quota` | 重置用户今日 VIP 用量 |
| POST | `/admin/products` | 新建产品 |
| POST | `/admin/deploy/disable-payment-mock` | 支付 Mock 关闭指引（需 `PAYMENT_MOCK_ENABLED=false` 并重启） |
| GET | `/admin/literature` | 文献库分页（`page`/`size`） |
| GET | `/admin/outline-templates` | 提纲模板分页 |

用户端：

| GET | `/wallet/quota` | 当前用户 VIP 配额摘要 |
| GET | `/wallet/logs` | 钱包流水（分页） |
| GET | `/papers/mine` | 我的草稿列表 |
| PUT | `/auth/profile` | 昵称与写作偏好 |
| POST | `/auth/change-password` | 修改密码 |
| GET | `/auth/share-info` | 分享链接与邀请统计 |
| GET | `/ai/literature/search` | 文献检索（需登录；本地库 + OpenAlex 补全） |

## 商业闭环（支付 → 生成 → 交付）

1. 用户 `POST /orders` 创建待支付订单（金额来自 `product_prices`，并按 VIP 配额折算）；`GET /orders?page&size` 分页列表
2. 支付成功后统一走 `OrderService.completePayment` → 自动创建 `Job`
3. Job 完成后写入 `paper.preview`，并归档 `paper_files`（TXT/DOCX）
4. 用户 `GET /files/papers/{paperId}/deliveries` 下载交付物

### 支付方式矩阵

| 场景 | 接口 | 说明 |
|------|------|------|
| 开发模拟 | `POST /orders/{id}/pay-wechat-mock` | 需 `PAYMENT_MOCK_ENABLED=true` |
| 开发回调 | `POST /payments/callback/mock` | 头 `X-Pay-Secret`，同上开关 |
| 余额支付 | `POST /orders/{id}/pay-balance` | 扣减用户余额 |
| 微信 Native | `POST /orders/{id}/prepay` body `{method:"wechat"}` | 返回 `qrContent`；真实到账靠 V3 回调 |
| 支付宝当面付 | `POST /orders/{id}/prepay` body `{method:"alipay"}` | 返回 `qrContent`；真实到账靠异步通知 |
| 微信 V3 回调 | `POST /payments/callback/wechat` | 微信服务器 POST 加密体；返回 `{"code":"SUCCESS"}` |
| 支付宝回调 | `POST /payments/callback/alipay` | 支付宝 form 通知；返回纯文本 `success` |
| VIP 免费 | 订单金额 0 | 创建订单时 `willUseFreeQuota=true`，走余额支付即可 |
| 余额充值 | `POST /wallet/recharge/prepay` body `{amount, method?}` | 订单号 `WR*`；微信/支付宝回调入账 |
| 充值状态 | `GET /wallet/recharge/status?orderNo=` | 前端轮询 |
| 模拟充值确认 | `POST /wallet/recharge/confirm-mock` | 仅 mock 开启时 |

生产：`SPRING_PROFILES_ACTIVE=prod` + `PAYMENT_MOCK_ENABLED=false`，并配置 `WECHAT_*` / `ALIPAY_*` 或仅使用余额/礼包码。

### 支付宝当面付配置

1. 登录 [支付宝开放平台](https://open.alipay.com/) → 创建应用 → **添加能力「当面付」** 并签约
2. **开发设置 → 接口加签方式**：上传应用公钥，保存**支付宝公钥**（不是应用公钥）
3. 网关（代码/env 中配置，无需在控制台再填）：
   - 正式：`https://openapi.alipay.com/gateway.do`
   - 沙箱：`https://openapi-sandbox.dl.alipaydev.com/gateway.do`
4. **授权回调地址**：仅 OAuth 登录需要；纯扫码支付可暂不填
5. **openid 配置**：保持「已启用」，与平台一致即可
6. 后端 `xiaowei-server/.env`：

```bash
PAYMENT_MOCK_ENABLED=false
ALIPAY_PAY_ENABLED=true
ALIPAY_APP_ID=你的APPID
ALIPAY_PRIVATE_KEY_PEM="-----BEGIN PRIVATE KEY-----
...应用私钥 PKCS8...
-----END PRIVATE KEY-----"
ALIPAY_PUBLIC_KEY_PEM="-----BEGIN PUBLIC KEY-----
...支付宝公钥...
-----END PUBLIC KEY-----"
ALIPAY_NOTIFY_URL=https://你的域名/api/payments/callback/alipay
# 沙箱时改用 ALIPAY_GATEWAY_URL=https://openapi-sandbox.dl.alipaydev.com/gateway.do
```

7. 重启后端；用户端支付弹窗可选「支付宝扫码」，充值弹窗可选支付宝

### VIP 配额

- 规则表 `vip_quota_config`：按 `vip_level` × `task_type` 配置每日免费次数与超额折扣
- 用量表 `user_daily_quota`：按自然日记录已用次数
- 创建订单时 `VipQuotaService.quoteForOrder` 计算 `finalAmount`；有剩余免费次数时金额为 0
- 支付完成（`payMethod=vip_quota` 或金额为 0）时扣减当日免费次数；无订单的 Job 可走 `tryConsumeFreeForJob`
- 种子数据（`V4__vip_quota.sql`）：VIP1 每日 1 次 `paper_generate` 免费 + 10% 超额折扣

回调示例（开发 mock，非微信官方协议）：

```bash
curl -X POST http://localhost:8080/api/payments/callback/mock \
  -H 'Content-Type: application/json' \
  -H 'X-Pay-Secret: xiaowei-pay-dev-secret' \
  -d '{"orderNo":"O1730123456789","tradeNo":"WX123","status":"success"}'
```

充值订单回调（`WR` 前缀）：

```bash
curl -X POST http://localhost:8080/api/payments/callback/mock \
  -H 'Content-Type: application/json' \
  -H 'X-Pay-Secret: xiaowei-pay-dev-secret' \
  -d '{"orderNo":"WR1730123456789","tradeNo":"WX124","status":"success"}'
```

## AI 运营商配置

管理端 **AI 配置** 支持选择运营商（OpenAI / DeepSeek / 通义 / Moonshot / 智谱 / Azure / 自定义），配置写入表 `ai_runtime_config`，重启后仍生效。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/ai-config` | 读取配置（含运营商预设列表） |
| PUT | `/admin/ai-config` | 保存 provider、baseUrl、apiKey、模型路由等 |
| POST | `/admin/ai-config/test` | 连通性测试（发一条「OK」探针） |
| POST | `/admin/ai-config/test-all` | 测试全部模型并更新健康状态 |
| POST | `/admin/ai-config/probe-all` | 探测可用性 |
| GET | `/admin/ai-config/pool-status` | 实时空闲槽 / 有效全局并发 |
| POST | `/jobs/{id}/retry` | 用户重试失败任务 |

模型线程池：**轮询 / 最少繁忙**、**任务绑定接入点**、**故障自动切换**、健康 fail 节点自动跳过。

`application.yml` 可设默认值：

```yaml
xiaowei:
  ai:
    provider: mock          # mock | openai | deepseek | qwen | ...
    base-url: ""            # 留空则用运营商预设
    model-name: gpt-4o-mini
    temperature: 0.7
```

关闭 Mock（选择真实运营商并填写 API Key）后，标题润色、提纲、生成任务等将走 OpenAI 兼容 `POST /v1/chat/completions`。

## 配置

| 变量 | 说明 |
|------|------|
| `SPRING_PROFILES_ACTIVE` | 默认 `dev,mysql`；`dev` 与 `mysql` 均使用 MySQL |
| `MYSQL_HOST` | 默认 `localhost` |
| `MYSQL_PORT` | 默认 `3306` |
| `MYSQL_DB` | 默认 `xiaowei` |
| `MYSQL_USER` | 默认 `root` |
| `MYSQL_PASSWORD` | 在 `xiaowei-server/.env` 中配置 |
| `JWT_SECRET` | JWT 密钥（生产必改） |
| `PAY_CALLBACK_SECRET` | 支付回调鉴权密钥（默认 `xiaowei-pay-dev-secret`） |
| `CORS_ALLOWED_ORIGINS` | 生产跨域白名单（逗号分隔；同域反代可留空） |

## MySQL

```bash
SPRING_PROFILES_ACTIVE=mysql mvn spring-boot:run
```

或使用项目根目录 `docker-compose.yml` 一键启动全套服务。

## IntelliJ 运行配置

**错误示例（不要这样写）：**

```text
-Dspring.profiles.active= .env.server
```

这会把 Spring Profile 设成字面量 `.env.server`，不会按预期加载 MySQL 配置。

**正确做法：**

1. 右上角运行配置选 **XiaoweiApplication (MySQL)**（项目 `.run/` 目录已提供）
2. 或 **Active profiles** 填 `dev,mysql` / `mysql`（不是 `.env.server`）
3. **工作目录**：`xiaowei-server`
4. **JDK**：17（你已用 ms-17.0.19 即可）

若报 `Unsupported Database: MySQL 8.0`，在 `pom.xml` 中需有 `flyway-mysql` 依赖，然后 **Maven Reload** 再启动。

启动成功且连 MySQL 时，日志应为：

```text
The following 1 profile is active: "mysql"
jdbc:mysql://localhost:3306/xiaowei
```

## 生产部署 Checklist

管理后台 **生产部署向导**（`/admin/deploy`）提供：

- 运行时就绪检测（AI Mock、JWT、支付、模型池 Key 等）
- 一键关闭 AI Mock（需先配置模型池 API Key）
- 可复制后端/前端 `.env` 模板与 Nginx 片段

### 后端

```bash
cd xiaowei-server
export SPRING_PROFILES_ACTIVE=prod,mysql
export JWT_SECRET=<至少32字符随机串>
export PAY_CALLBACK_SECRET=<支付回调密钥>
export PAYMENT_MOCK_ENABLED=false
# 可选：微信 Native 支付
export WECHAT_PAY_ENABLED=true
export WECHAT_APP_ID=...
export WECHAT_MCH_ID=...
export WECHAT_API_V3_KEY=...
export WECHAT_SERIAL_NO=...
export WECHAT_PRIVATE_KEY_PEM="-----BEGIN PRIVATE KEY-----..."
export WECHAT_NOTIFY_URL=https://your-domain/api/payments/callback/wechat
# 可选：支付宝当面付
export ALIPAY_PAY_ENABLED=true
export ALIPAY_APP_ID=...
export ALIPAY_PRIVATE_KEY_PEM="-----BEGIN PRIVATE KEY-----..."
export ALIPAY_PUBLIC_KEY_PEM="-----BEGIN PUBLIC KEY-----..."
export ALIPAY_NOTIFY_URL=https://your-domain/api/payments/callback/alipay
mvn -DskipTests package
java -jar target/xiaowei-server-1.0.0-SNAPSHOT.jar
```

- `application-prod.yml` 默认关闭 AI Mock（`xiaowei.ai.mock=false`）
- 管理后台 **AI 模型池** 配置真实 LLM 端点
- 为各产品 SKU 在 **AI 产品 / 定价** 中维护价格（Flyway V12 已种子部分 SKU）

### 前端

```bash
# 根目录 .env.production 已含 VITE_USE_API=true
npm run build
# 将 dist/ 部署到 Nginx，/api 反代到后端 8080
```

### 管理端

```bash
cd xiaowei-admin && npm run build
# 部署到 /admin/ 路径
```

### 新能力（API）

| 路径 | 说明 |
|------|------|
| `GET /schools` | 学校模板列表 |
| `GET /files/jobs/{jobId}/deliveries` | 快捷任务交付文件 |
| `POST /wallet/redeem-gift` | 礼包码兑换 |
| `GET /admin/gift-codes` | 管理端礼包码 |
| `GET/PUT /admin/referral-settings` | 分享邀请奖励金额与说明（用户端实时生效） |
| `GET /admin/channels/commission-stats` | 渠道佣金统计 |

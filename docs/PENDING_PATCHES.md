# 待应用补丁（环境写入受限时手动合并）

**状态：上述补丁已于 2026-06 合入主分支。** 保留本文档供对照；新补丁请追加条目。

## 1. AdminService — 使用 DashboardStatsService

已合入。

## 2. OrderController — lookup 限流

已合入；`application.yml` 含 `xiaowei.rate-limit.lookup-max`。

## 3. AiModelTokenStatRepository — aggregateTotals

已合入。

## 4. AiModelUsageService.summary — 用 aggregate + top 20

已合入。

## 5. ProductController — quote 需登录

已合入。

## 6. OrderService — 游客 lookup sanitize

已合入。

## 7. 前端 OrderList — 使用 src/utils/orderStatus.ts

已合入。

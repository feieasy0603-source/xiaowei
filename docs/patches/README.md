# 一键合并补丁（在项目根目录执行）

```bash
cd /Users/easyeasy/Desktop/xiaowei

# 后端
cp docs/patches/OrderController.java xiaowei-server/src/main/java/com/xiaowei/api/OrderController.java
cp docs/patches/AiModelTokenStatRepository.java xiaowei-server/src/main/java/com/xiaowei/domain/repository/AiModelTokenStatRepository.java

# AdminService dashboard 见 docs/PENDING_PATCHES.md
# AiModelUsageService summary 见 docs/patches/AiModelUsageService-summary.snippet
# OrderService sanitize 见 docs/patches/OrderService-sanitize.snippet
# ProductController quote 见 docs/patches/ProductController-quote.snippet

# application.yml 追加:
# xiaowei.rate-limit.lookup-max: 30
```

IDEA Rebuild 后验证：游客 lookup 超 30 次/分钟返回 429；管理端看板正常。

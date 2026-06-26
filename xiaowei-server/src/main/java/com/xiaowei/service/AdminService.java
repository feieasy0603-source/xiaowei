package com.xiaowei.service;

import com.xiaowei.common.BusinessException;
import com.xiaowei.common.PageResult;
import com.xiaowei.domain.entity.*;
import com.xiaowei.domain.repository.*;
import com.xiaowei.integration.payment.WechatPayClient;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final JobRepository jobRepository;
    private final ProductRepository productRepository;
    private final ChannelRepository channelRepository;
    private final WalletLogRepository walletLogRepository;
    private final WalletRechargeOrderRepository walletRechargeOrderRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final PaperRepository paperRepository;
    private final WalletService walletService;
    private final OrderService orderService;
    private final JobService jobService;
    private final AiModelUsageService aiModelUsageService;
    private final AiConfigService aiConfigService;
    private final DashboardStatsService dashboardStatsService;
    private final WalletRechargeService walletRechargeService;
    private final WechatPayClient wechatPayClient;

    public Map<String, Object> dashboard() {
        Map<String, Object> m = new HashMap<>(dashboardStatsService.snapshot());
        m.put("aiTokenStats", aiModelUsageService.summary());
        m.put("aiPoolEnabledCount", aiConfigService.enabledEndpoints().size());
        m.put("aiPoolStrategy", aiConfigService.getConfig().getOrDefault("poolStrategy", "round_robin"));
        return m;
    }

    public PageResult<Map<String, Object>> listJobs(
            String status,
            String taskType,
            String productId,
            String jobNo,
            Long userId,
            String userPhone,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size
    ) {
        Page<Job> p = jobRepository.search(
                emptyToNull(status),
                emptyToNull(taskType),
                emptyToNull(productId),
                emptyToNull(jobNo),
                userId,
                emptyToNull(userPhone),
                createdFrom,
                createdTo,
                pageRequest(page, size));
        List<Map<String, Object>> items = mapJobs(p.getContent());
        return new PageResult<>(items, p.getTotalElements(), page, size);
    }

    public Map<String, Object> jobStats(int days) {
        int d = Math.min(Math.max(days, 1), 90);
        Instant since = Instant.now().minus(d, ChronoUnit.DAYS);

        long total = jobRepository.countByCreatedAtGreaterThanEqual(since);
        long success = jobRepository.countByCreatedAtGreaterThanEqualAndStatus(since, "success");
        long failed = jobRepository.countByCreatedAtGreaterThanEqualAndStatus(since, "failed");
        long running = jobRepository.countByCreatedAtGreaterThanEqualAndStatus(since, "running");
        long cancelled = jobRepository.countByCreatedAtGreaterThanEqualAndStatus(since, "cancelled");

        Double avgDb = jobRepository.avgSuccessDurationSecondsSince(since);
        double avgDurationSec = avgDb != null ? avgDb : 0;

        List<Map<String, Object>> topErrors = jobRepository.topFailureReasonsSince(since).stream()
                .map(row -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("reason", row[0]);
                    m.put("count", ((Number) row[1]).longValue());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> m = new HashMap<>();
        m.put("days", d);
        m.put("total", total);
        m.put("success", success);
        m.put("failed", failed);
        m.put("running", running);
        m.put("cancelled", cancelled);
        m.put("successRate", total > 0
                ? BigDecimal.valueOf(success * 100.0 / total).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        m.put("avgDurationSec", BigDecimal.valueOf(avgDurationSec).setScale(1, RoundingMode.HALF_UP));
        m.put("topErrors", topErrors);
        return m;
    }

    @Transactional
    public Map<String, Object> batchRetryJobs(List<Long> ids) {
        int ok = 0;
        List<String> errors = new ArrayList<>();
        for (Long id : ids) {
            try {
                retryJob(id);
                ok++;
            } catch (Exception e) {
                errors.add(id + ": " + e.getMessage());
            }
        }
        Map<String, Object> m = new HashMap<>();
        m.put("success", ok);
        m.put("total", ids.size());
        m.put("errors", errors);
        return m;
    }

    @Transactional
    public void retryJob(Long id) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new BusinessException("任务不存在"));
        if ("running".equals(job.getStatus()) || "success".equals(job.getStatus())) {
            throw new BusinessException("运行中或已成功的任务无需重试");
        }
        if ("pending".equals(job.getStatus())) {
            jobService.scheduleJob(id);
            return;
        }
        if (!List.of("failed", "cancelled").contains(job.getStatus())) {
            throw new BusinessException("仅失败、已取消或卡住的排队任务可重试");
        }
        job.setStatus("pending");
        job.setProgress(0);
        job.setErrorMsg(null);
        job.setResultJson(null);
        job.setFinishedAt(null);
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);
        jobService.scheduleJob(id);
    }

    public Map<String, Object> getJob(Long id) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new BusinessException("任务不存在"));
        return jobToMap(job);
    }

    public PageResult<Map<String, Object>> listPapers(String title, String productId, int page, int size) {
        Page<Paper> p = paperRepository.search(emptyToNull(title), emptyToNull(productId), pageRequest(page, size));
        List<Map<String, Object>> items = mapPapers(p.getContent());
        return new PageResult<>(items, p.getTotalElements(), page, size);
    }

    public Map<String, Object> getPaper(String id) {
        Paper paper = paperRepository.findById(id).orElseThrow(() -> new BusinessException("草稿不存在"));
        return paperToMap(paper);
    }

    @Transactional
    public Product createProduct(Map<String, Object> body) {
        String id = body.get("id") != null ? String.valueOf(body.get("id")).trim() : "";
        if (id.isBlank()) {
            throw new BusinessException("产品 ID 不能为空");
        }
        if (productRepository.existsById(id)) {
            throw new BusinessException("产品 ID 已存在: " + id);
        }
        String label = body.get("label") != null ? String.valueOf(body.get("label")).trim() : "";
        if (label.isBlank()) {
            throw new BusinessException("产品名称不能为空");
        }
        String taskType = body.get("taskType") != null ? String.valueOf(body.get("taskType")) : "paper_generate";
        String formVariant = body.get("formVariant") != null ? String.valueOf(body.get("formVariant")) : "graduation";
        String processVariant = body.get("processVariant") != null ? String.valueOf(body.get("processVariant")) : "standard";
        String flowType = body.get("flowType") != null ? String.valueOf(body.get("flowType")) : "both";
        Product p = new Product();
        p.setId(id);
        p.setLabel(label);
        p.setTaskType(taskType);
        p.setFormVariant(formVariant);
        p.setProcessVariant(processVariant);
        p.setFlowType(flowType);
        p.setEnabled(body.get("enabled") == null || Boolean.TRUE.equals(body.get("enabled")));
        p.setShowFaq(body.get("showFaq") == null || Boolean.TRUE.equals(body.get("showFaq")));
        p.setCenterTitle(Boolean.TRUE.equals(body.get("centerTitle")));
        p.setSortOrder(body.get("sortOrder") != null ? ((Number) body.get("sortOrder")).intValue() : 99);
        p.setSubmitLabel(body.get("submitLabel") != null ? String.valueOf(body.get("submitLabel")) : "立即生成");
        p.setTitleFieldLabel(body.get("titleFieldLabel") != null
                ? String.valueOf(body.get("titleFieldLabel")) : "提交论文标题");
        p.setCreatedAt(Instant.now());
        return productRepository.save(p);
    }

    @Transactional
    public Product updateProduct(String id, Map<String, Object> body) {
        Product p = productRepository.findById(id).orElseThrow(() -> new BusinessException("产品不存在"));
        if (body.get("label") != null) p.setLabel(String.valueOf(body.get("label")));
        if (body.get("enabled") != null) p.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        if (body.get("banner") != null) p.setBanner(String.valueOf(body.get("banner")));
        if (body.get("taskType") != null) p.setTaskType(String.valueOf(body.get("taskType")));
        if (body.get("flowType") != null) p.setFlowType(String.valueOf(body.get("flowType")));
        if (body.get("submitLabel") != null) p.setSubmitLabel(String.valueOf(body.get("submitLabel")));
        if (body.get("sortOrder") != null) p.setSortOrder(((Number) body.get("sortOrder")).intValue());
        if (body.get("processVariant") != null) p.setProcessVariant(String.valueOf(body.get("processVariant")));
        if (body.get("formVariant") != null) p.setFormVariant(String.valueOf(body.get("formVariant")));
        if (body.get("titleFieldLabel") != null) p.setTitleFieldLabel(String.valueOf(body.get("titleFieldLabel")));
        if (body.get("titlePlaceholder") != null) p.setTitlePlaceholder(String.valueOf(body.get("titlePlaceholder")));
        if (body.get("proLinkText") != null) p.setProLinkText(String.valueOf(body.get("proLinkText")));
        if (body.get("agreementText") != null) p.setAgreementText(String.valueOf(body.get("agreementText")));
        if (body.get("showFaq") != null) p.setShowFaq(Boolean.TRUE.equals(body.get("showFaq")));
        if (body.get("centerTitle") != null) p.setCenterTitle(Boolean.TRUE.equals(body.get("centerTitle")));
        if (body.get("configJson") != null) p.setConfigJson(String.valueOf(body.get("configJson")));
        return productRepository.save(p);
    }

    public PageResult<Map<String, Object>> listUsers(String phone, String status, int page, int size) {
        Page<User> p = userRepository.search(emptyToNull(phone), emptyToNull(status), pageRequest(page, size));
        List<Map<String, Object>> items = p.getContent().stream().map(this::userToMap).collect(Collectors.toList());
        return new PageResult<>(items, p.getTotalElements(), page, size);
    }

    public Map<String, Object> getUser(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new BusinessException("用户不存在"));
        Map<String, Object> m = userToMap(user);
        m.put("wxOpenId", user.getWxOpenId());
        m.put("createdAt", user.getCreatedAt());
        m.put("updatedAt", user.getUpdatedAt());
        return m;
    }

    @Transactional
    public Map<String, Object> updateUser(Long id, Map<String, Object> body) {
        User user = userRepository.findById(id).orElseThrow(() -> new BusinessException("用户不存在"));
        if (body.get("nickname") != null) {
            user.setNickname(String.valueOf(body.get("nickname")));
        }
        if (body.get("status") != null) {
            String status = String.valueOf(body.get("status"));
            if (!List.of("active", "disabled").contains(status)) {
                throw new BusinessException("状态仅支持 active / disabled");
            }
            user.setStatus(status);
        }
        if (body.get("vipLevel") != null) {
            user.setVipLevel(((Number) body.get("vipLevel")).intValue());
        }
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        return userToMap(user);
    }

    @Transactional
    public Map<String, Object> rechargeUser(Long userId, BigDecimal amount, String remark, String adminRef) {
        return walletService.adminRecharge(userId, amount, remark, adminRef);
    }

    @Transactional
    public Map<String, Object> deductUser(Long userId, BigDecimal amount, String remark, String adminRef) {
        return walletService.adminDeduct(userId, amount, remark, adminRef);
    }

    public PageResult<Map<String, Object>> listWalletLogs(Long userId, int page, int size) {
        userRepository.findById(userId).orElseThrow(() -> new BusinessException("用户不存在"));
        Page<WalletLog> p = walletLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest(page, size));
        List<Map<String, Object>> items = p.getContent().stream().map(this::walletLogToMap).collect(Collectors.toList());
        return new PageResult<>(items, p.getTotalElements(), page, size);
    }

    public PageResult<Map<String, Object>> listWalletRecharges(
            String payStatus,
            String orderNo,
            Long userId,
            int page,
            int size
    ) {
        Page<WalletRechargeOrder> p = walletRechargeOrderRepository.search(
                emptyToNull(payStatus),
                emptyToNull(orderNo),
                userId,
                pageRequest(page, size));
        List<Map<String, Object>> items = p.getContent().stream()
                .map(this::walletRechargeToMap)
                .collect(Collectors.toList());
        return new PageResult<>(items, p.getTotalElements(), page, size);
    }

    public Map<String, Object> confirmWalletRecharge(Long id, String adminRef) {
        return walletRechargeService.adminConfirmPaid(id, adminRef);
    }

    public Map<String, Object> cancelWalletRecharge(Long id) {
        return walletRechargeService.adminCancel(id);
    }

    private Map<String, Object> walletRechargeToMap(WalletRechargeOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("orderNo", o.getOrderNo());
        m.put("userId", o.getUserId());
        m.put("amount", o.getAmount());
        m.put("payStatus", o.getPayStatus());
        m.put("paidAt", o.getPaidAt());
        m.put("createdAt", o.getCreatedAt());
        return m;
    }

    public PageResult<Map<String, Object>> listPaymentRecords(
            Long userId,
            Long orderId,
            String payMethod,
            String status,
            String tradeNo,
            int page,
            int size
    ) {
        Page<PaymentRecord> p = paymentRecordRepository.search(
                userId,
                orderId,
                emptyToNull(payMethod),
                emptyToNull(status),
                emptyToNull(tradeNo),
                pageRequest(page, size));
        List<Map<String, Object>> items = p.getContent().stream()
                .map(this::paymentRecordToMap)
                .collect(Collectors.toList());
        return new PageResult<>(items, p.getTotalElements(), page, size);
    }

    private Map<String, Object> paymentRecordToMap(PaymentRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("userId", r.getUserId());
        m.put("orderId", r.getOrderId());
        m.put("amount", r.getAmount());
        m.put("payMethod", r.getPayMethod());
        m.put("tradeNo", r.getTradeNo());
        m.put("status", r.getStatus());
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    public Map<String, Object> getOrder(Long id) {
        Order order = orderRepository.findById(id).orElseThrow(() -> new BusinessException("订单不存在"));
        return orderToMap(order);
    }

    public PageResult<Map<String, Object>> listOrders(
            String payStatus,
            String orderNo,
            Long userId,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size
    ) {
        Page<Order> p = orderRepository.search(
                emptyToNull(payStatus),
                emptyToNull(orderNo),
                userId,
                createdFrom,
                createdTo,
                pageRequest(page, size));
        List<Map<String, Object>> items = mapOrders(p.getContent());
        return new PageResult<>(items, p.getTotalElements(), page, size);
    }

    @Transactional
    public Map<String, Object> markOrderPaid(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new BusinessException("订单不存在"));
        if ("paid".equals(order.getPayStatus())) {
            throw new BusinessException("订单已支付");
        }
        return orderService.completePayment(orderId, "admin", "ADMIN_" + order.getOrderNo());
    }

    @Transactional
    public Map<String, Object> refundOrder(Long orderId, String remark) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new BusinessException("订单不存在"));
        if (!"paid".equals(order.getPayStatus())) {
            throw new BusinessException("仅已支付订单可退款");
        }
        String refundRemark = remark != null && !remark.isBlank()
                ? remark
                : "订单退款 " + order.getOrderNo();
        if (order.getPayMethod() != null && order.getPayMethod().equalsIgnoreCase("wechat")) {
            int fen = order.getAmount().multiply(BigDecimal.valueOf(100)).intValue();
            String outRefundNo = "RF" + order.getOrderNo();
            WechatPayClient.RefundResult refundResult = wechatPayClient.createRefund(
                    order.getOrderNo(), outRefundNo, fen, fen, refundRemark);
            recordRefundPayment(order, "wechat", refundResult.refundId());
        } else if ("balance".equals(order.getPayMethod())) {
            walletService.adminRecharge(
                    order.getUserId(),
                    order.getAmount(),
                    refundRemark,
                    "order_refund:" + order.getId()
            );
            recordRefundPayment(order, "balance", "REFUND_BAL_" + order.getOrderNo());
        }
        jobRepository.findByOrderId(order.getId()).ifPresent(job -> {
            if (List.of("pending", "running").contains(job.getStatus())) {
                jobService.cancelJob(job.getId());
            }
        });
        order.setPayStatus("refunded");
        orderRepository.save(order);
        return orderToMap(order);
    }

    @Transactional
    public Channel updateChannel(Long id, Map<String, Object> body) {
        Channel ch = channelRepository.findById(id).orElseThrow(() -> new BusinessException("渠道不存在"));
        if (body.get("name") != null) ch.setName(String.valueOf(body.get("name")));
        if (body.get("commissionRate") != null) {
            ch.setCommissionRate(new BigDecimal(String.valueOf(body.get("commissionRate"))));
        }
        if (body.get("enabled") != null) ch.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        return channelRepository.save(ch);
    }

    private Map<String, Object> userToMap(User u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("phone", u.getPhone());
        m.put("nickname", u.getNickname());
        m.put("balance", u.getBalance());
        m.put("vipLevel", u.getVipLevel());
        m.put("status", u.getStatus());
        m.put("createdAt", u.getCreatedAt());
        return m;
    }

    private Map<String, Object> walletLogToMap(WalletLog log) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", log.getId());
        m.put("userId", log.getUserId());
        m.put("type", log.getType());
        m.put("amount", log.getAmount());
        m.put("balanceAfter", log.getBalanceAfter());
        m.put("refType", log.getRefType());
        m.put("refId", log.getRefId());
        m.put("remark", log.getRemark());
        m.put("createdAt", log.getCreatedAt());
        return m;
    }

    private Map<String, Object> jobToMap(Job j) {
        Set<Long> userIds = j.getUserId() != null ? Set.of(j.getUserId()) : Set.of();
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<String, Product> products = productRepository.findAllById(Set.of(j.getProductId())).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        Map<Long, Order> orders = j.getOrderId() != null
                ? orderRepository.findAllById(Set.of(j.getOrderId())).stream()
                .collect(Collectors.toMap(Order::getId, o -> o))
                : Map.of();
        return jobToMap(j, users, products, orders);
    }

    private Map<String, Object> jobToMap(
            Job j,
            Map<Long, User> users,
            Map<String, Product> products,
            Map<Long, Order> orders
    ) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", j.getId());
        m.put("jobNo", j.getJobNo());
        m.put("userId", j.getUserId());
        if (j.getUserId() != null) {
            User u = users.get(j.getUserId());
            if (u != null) {
                m.put("userPhone", u.getPhone());
                m.put("userNickname", u.getNickname());
            }
        }
        m.put("productId", j.getProductId());
        Product p = products.get(j.getProductId());
        if (p != null) {
            m.put("productLabel", p.getLabel());
            m.put("flowType", p.getFlowType());
        }
        m.put("paperId", j.getPaperId());
        m.put("orderId", j.getOrderId());
        if (j.getOrderId() != null) {
            Order o = orders.get(j.getOrderId());
            if (o != null) {
                m.put("orderNo", o.getOrderNo());
            }
        }
        m.put("taskType", j.getTaskType());
        m.put("status", j.getStatus());
        m.put("progress", j.getProgress());
        m.put("payloadJson", j.getPayloadJson());
        m.put("resultJson", j.getResultJson());
        m.put("errorMsg", j.getErrorMsg());
        m.put("createdAt", j.getCreatedAt());
        m.put("updatedAt", j.getUpdatedAt());
        m.put("finishedAt", j.getFinishedAt());
        return m;
    }

    private Map<String, Object> paperToMap(Paper paper) {
        Set<Long> userIds = paper.getUserId() != null ? Set.of(paper.getUserId()) : Set.of();
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<String, Product> products = productRepository.findAllById(Set.of(paper.getProductId())).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        return paperToMap(paper, users, products);
    }

    private Map<String, Object> paperToMap(Paper paper, Map<Long, User> users, Map<String, Product> products) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", paper.getId());
        m.put("userId", paper.getUserId());
        m.put("productId", paper.getProductId());
        m.put("title", paper.getTitle());
        m.put("maxVisitedStep", paper.getMaxVisitedStep());
        m.put("createdAt", paper.getCreatedAt());
        m.put("updatedAt", paper.getUpdatedAt());
        Product p = products.get(paper.getProductId());
        if (p != null) {
            m.put("productLabel", p.getLabel());
        }
        if (paper.getUserId() != null) {
            User u = users.get(paper.getUserId());
            if (u != null) {
                m.put("userPhone", u.getPhone());
            }
        }
        m.put("hasPreview", paper.getDraftJson() != null && paper.getDraftJson().contains("\"preview\""));
        return m;
    }

    private Map<String, Object> orderToMap(Order o) {
        Map<Long, User> users = userRepository.findAllById(Set.of(o.getUserId())).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<String, Product> products = productRepository.findAllById(Set.of(o.getProductId())).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        Job job = jobRepository.findByOrderId(o.getId()).orElse(null);
        return orderToMap(o, users, products, job);
    }

    private Map<String, Object> orderToMap(
            Order o,
            Map<Long, User> users,
            Map<String, Product> products,
            Job job
    ) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", o.getId());
        m.put("orderNo", o.getOrderNo());
        m.put("userId", o.getUserId());
        User u = users.get(o.getUserId());
        if (u != null) {
            m.put("userPhone", u.getPhone());
            m.put("userNickname", u.getNickname());
        }
        m.put("productId", o.getProductId());
        Product p = products.get(o.getProductId());
        if (p != null) {
            m.put("productLabel", p.getLabel());
        }
        m.put("paperId", o.getPaperId());
        m.put("channelId", o.getChannelId());
        m.put("amount", o.getAmount());
        m.put("payStatus", o.getPayStatus());
        m.put("payMethod", o.getPayMethod());
        m.put("paidAt", o.getPaidAt());
        m.put("createdAt", o.getCreatedAt());
        m.put("quoteDegree", o.getQuoteDegree());
        m.put("quoteWordCount", o.getQuoteWordCount());
        m.put("quoteModelType", o.getQuoteModelType());
        if (job != null) {
            m.put("jobId", job.getId());
            m.put("jobNo", job.getJobNo());
            m.put("jobStatus", job.getStatus());
            m.put("jobProgress", job.getProgress());
        }
        return m;
    }

    private PageRequest pageRequest(int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        return PageRequest.of(p - 1, s);
    }

    public List<Map<String, Object>> channelCommissionStats() {
        List<Object[]> rows = orderRepository.aggregatePaidByChannel();
        Set<Long> channelIds = new HashSet<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                channelIds.add(((Number) row[0]).longValue());
            }
        }
        Map<Long, Channel> channels = channelRepository.findAllById(channelIds).stream()
                .collect(Collectors.toMap(Channel::getId, c -> c));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] row : rows) {
            Long channelId = row[0] != null ? ((Number) row[0]).longValue() : null;
            long orderCount = row[1] != null ? ((Number) row[1]).longValue() : 0;
            BigDecimal totalAmount = row[2] != null ? new BigDecimal(String.valueOf(row[2])) : BigDecimal.ZERO;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("channelId", channelId);
            if (channelId != null) {
                Channel ch = channels.get(channelId);
                if (ch != null) {
                    m.put("channelName", ch.getName());
                    m.put("dCode", ch.getDCode());
                    m.put("commissionRate", ch.getCommissionRate());
                    if (ch.getCommissionRate() != null) {
                        m.put("commissionAmount", totalAmount.multiply(ch.getCommissionRate())
                                .setScale(2, RoundingMode.HALF_UP));
                    }
                }
            }
            m.put("orderCount", orderCount);
            m.put("totalAmount", totalAmount);
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> mapJobs(List<Job> jobs) {
        if (jobs.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new HashSet<>();
        Set<String> productIds = new HashSet<>();
        Set<Long> orderIds = new HashSet<>();
        for (Job j : jobs) {
            if (j.getUserId() != null) {
                userIds.add(j.getUserId());
            }
            productIds.add(j.getProductId());
            if (j.getOrderId() != null) {
                orderIds.add(j.getOrderId());
            }
        }
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<String, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        Map<Long, Order> orders = orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, o -> o));
        return jobs.stream()
                .map(j -> jobToMap(j, users, products, orders))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> mapOrders(List<Order> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new HashSet<>();
        Set<String> productIds = new HashSet<>();
        for (Order o : orders) {
            userIds.add(o.getUserId());
            productIds.add(o.getProductId());
        }
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<String, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        Set<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toSet());
        Map<Long, Job> jobsByOrder = jobRepository.findByOrderIdIn(orderIds).stream()
                .filter(j -> j.getOrderId() != null)
                .collect(Collectors.toMap(Job::getOrderId, j -> j, (a, b) -> a));
        return orders.stream()
                .map(o -> orderToMap(o, users, products, jobsByOrder.get(o.getId())))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> mapPapers(List<Paper> papers) {
        if (papers.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new HashSet<>();
        Set<String> productIds = new HashSet<>();
        for (Paper paper : papers) {
            if (paper.getUserId() != null) {
                userIds.add(paper.getUserId());
            }
            productIds.add(paper.getProductId());
        }
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<String, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        return papers.stream()
                .map(paper -> paperToMap(paper, users, products))
                .collect(Collectors.toList());
    }

    private String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private void recordRefundPayment(Order order, String payMethod, String tradeNo) {
        if (tradeNo == null || tradeNo.isBlank()) {
            return;
        }
        if (paymentRecordRepository.findByTradeNo(tradeNo).isPresent()) {
            return;
        }
        PaymentRecord record = new PaymentRecord();
        record.setUserId(order.getUserId());
        record.setOrderId(order.getId());
        record.setAmount(order.getAmount());
        record.setPayMethod(payMethod);
        record.setTradeNo(tradeNo);
        record.setStatus("refunded");
        record.setCreatedAt(Instant.now());
        paymentRecordRepository.save(record);
    }
}

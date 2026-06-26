package com.xiaowei.service;

import com.xiaowei.common.BusinessException;
import com.xiaowei.common.PageResult;
import com.xiaowei.domain.entity.Channel;
import com.xiaowei.domain.entity.Order;
import com.xiaowei.domain.entity.Product;
import com.xiaowei.domain.entity.Job;
import com.xiaowei.domain.entity.JobFile;
import com.xiaowei.domain.entity.PaperFile;
import com.xiaowei.domain.entity.PaymentRecord;
import com.xiaowei.domain.repository.ChannelRepository;
import com.xiaowei.domain.repository.PaymentRecordRepository;
import com.xiaowei.domain.repository.JobFileRepository;
import com.xiaowei.domain.repository.JobRepository;
import com.xiaowei.domain.repository.OrderRepository;
import com.xiaowei.domain.repository.PaperFileRepository;
import com.xiaowei.domain.repository.ProductRepository;
import com.xiaowei.util.BusinessIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ChannelRepository channelRepository;
    private final WalletService walletService;
    private final ProductPriceService productPriceService;
    private final OrderFulfillmentService orderFulfillmentService;
    private final JobRepository jobRepository;
    private final JobFileRepository jobFileRepository;
    private final PaperFileRepository paperFileRepository;
    private final VipQuotaService vipQuotaService;
    private final PaymentRecordRepository paymentRecordRepository;

    @Transactional
    public Map<String, Object> createOrder(Long userId, String productId, String paperId, String dCode) {
        return createOrder(userId, productId, paperId, dCode, null, null, null);
    }

    @Transactional
    public Map<String, Object> createOrder(
            Long userId,
            String productId,
            String paperId,
            String dCode,
            String degree,
            Integer wordCount,
            String modelType
    ) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException("产品不存在"));
        if (!Boolean.TRUE.equals(product.getEnabled())) {
            throw new BusinessException("产品已下架");
        }
        BigDecimal basePrice = productPriceService.quote(productId, degree, wordCount, modelType);
        Map<String, Object> quotaQuote = vipQuotaService.quoteForOrder(userId, product, basePrice);
        BigDecimal amount = (BigDecimal) quotaQuote.get("finalAmount");
        Long channelId = null;
        boolean channelInvalid = false;
        if (dCode != null && !dCode.isBlank()) {
            Channel ch = channelRepository.findActiveByDCode(dCode).orElse(null);
            if (ch != null) {
                channelId = ch.getId();
            } else {
                channelInvalid = true;
            }
        }
        Optional<Order> reusable = findReusableUnpaidOrder(userId, productId, paperId);
        if (reusable.isPresent()) {
            Order order = reusable.get();
            order.setAmount(amount);
            order.setQuoteDegree(degree);
            order.setQuoteWordCount(wordCount);
            order.setQuoteModelType(modelType);
            orderRepository.save(order);
            Map<String, Object> dto = toDto(order, product.getLabel());
            dto.put("quota", quotaQuote);
            dto.put("reused", true);
            if (channelInvalid) {
                dto.put("channelInvalid", true);
            }
            return dto;
        }
        Order order = new Order();
        order.setOrderNo(BusinessIdGenerator.orderNo());
        order.setUserId(userId);
        order.setProductId(productId);
        order.setPaperId(paperId);
        order.setChannelId(channelId);
        order.setAmount(amount);
        order.setPayStatus("unpaid");
        order.setQuoteDegree(degree);
        order.setQuoteWordCount(wordCount);
        order.setQuoteModelType(modelType);
        orderRepository.save(order);
        Map<String, Object> dto = toDto(order, product.getLabel());
        dto.put("quota", quotaQuote);
        if (channelInvalid) {
            dto.put("channelInvalid", true);
        }
        return dto;
    }

    private Optional<Order> findReusableUnpaidOrder(Long userId, String productId, String paperId) {
        if (paperId != null && !paperId.isBlank()) {
            return orderRepository.findFirstByUserIdAndProductIdAndPaperIdAndPayStatusOrderByCreatedAtDesc(
                    userId, productId, paperId, "unpaid");
        }
        return orderRepository.findFirstByUserIdAndProductIdAndPaperIdIsNullAndPayStatusOrderByCreatedAtDesc(
                userId, productId, "unpaid");
    }

    @Transactional
    public Map<String, Object> payWithBalance(Long userId, Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException("订单不存在"));
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作");
        }
        if ("paid".equals(order.getPayStatus())) {
            return toDtoForOrder(order);
        }
        refreshOrderAmount(order);
        if (order.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            walletService.deduct(userId, order.getAmount(), "order", order.getOrderNo());
        }
        return completePayment(orderId, order.getAmount().compareTo(BigDecimal.ZERO) == 0 ? "vip_quota" : "balance",
                order.getAmount().compareTo(BigDecimal.ZERO) == 0 ? "VIP_FREE_" + order.getOrderNo() : "BAL_" + order.getOrderNo());
    }

    @Transactional
    public Map<String, Object> completePayment(Long orderId, String payMethod, String tradeNo) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException("订单不存在"));
        if (!"paid".equals(order.getPayStatus())) {
            if (order.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                Product product = productRepository.findById(order.getProductId())
                        .orElseThrow(() -> new BusinessException("产品不存在"));
                boolean previewQuotaAlreadyUsed = order.getPaperId() != null
                        && !order.getPaperId().isBlank()
                        && jobRepository
                        .findFirstByPaperIdAndUserIdAndOrderIdIsNullOrderByCreatedAtDesc(
                                order.getPaperId(), order.getUserId())
                        .isPresent();
                if (!previewQuotaAlreadyUsed
                        && vipQuotaService.hasFreeQuotaRemaining(order.getUserId(), product.getTaskType())) {
                    vipQuotaService.consumeFreeQuota(order.getUserId(), product.getTaskType());
                }
            }
            order.setPayStatus("paid");
            order.setPayMethod(payMethod);
            order.setPaidAt(Instant.now());
            orderRepository.save(order);
            recordPaymentIfAbsent(order, payMethod, tradeNo);
            orderFulfillmentService.fulfillPaidOrder(order);
        }
        return toDtoForOrder(order);
    }

    private void recordPaymentIfAbsent(Order order, String payMethod, String tradeNo) {
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
        record.setStatus("success");
        record.setCreatedAt(Instant.now());
        paymentRecordRepository.save(record);
    }

    public Map<String, Object> toDtoForOrder(Order order) {
        String label = productRepository.findById(order.getProductId())
                .map(Product::getLabel).orElse(order.getProductId());
        return toDto(order, label);
    }

    public Map<String, Object> getOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("订单不存在"));
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权查看");
        }
        return toDtoForOrder(order);
    }

    /** 游客凭订单号或任务号查询（无需登录） */
    public Map<String, Object> lookupPublic(String orderNo, String jobNo) {
        String o = blankToNull(orderNo);
        String j = blankToNull(jobNo);
        if (o != null && j != null) {
            throw new BusinessException("请只填写订单号或任务号其中之一");
        }
        if (o != null) {
            return lookupByOrderNo(o);
        }
        if (j != null) {
            return lookupByJobNo(j);
        }
        throw new BusinessException("请输入订单号或任务号");
    }

    /** 游客凭订单号查询进度与交付文件（无需登录） */
    public Map<String, Object> lookupByOrderNo(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo.trim())
                .orElseThrow(() -> new BusinessException("订单不存在"));
        Map<String, Object> dto = toDtoForOrder(order);
        jobRepository.findByOrderId(order.getId()).ifPresent(job ->
                dto.put("deliveries", listPublicDeliveries(order, job)));
        if (!dto.containsKey("deliveries")) {
            dto.put("deliveries", List.of());
        }
        return sanitizePublicLookup(dto);
    }

    /** 游客凭任务号查询；若任务已关联订单则返回订单视图 */
    public Map<String, Object> lookupByJobNo(String jobNo) {
        Job job = jobRepository.findByJobNo(jobNo.trim())
                .orElseThrow(() -> new BusinessException("任务不存在"));
        if (job.getOrderId() != null) {
            Order order = orderRepository.findById(job.getOrderId())
                    .orElseThrow(() -> new BusinessException("关联订单不存在"));
            return lookupByOrderNo(order.getOrderNo());
        }
        String label = productRepository.findById(job.getProductId())
                .map(Product::getLabel).orElse(job.getProductId());
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", null);
        dto.put("orderNo", "");
        dto.put("productId", job.getProductId());
        dto.put("productLabel", label);
        dto.put("paperId", job.getPaperId());
        dto.put("amount", BigDecimal.ZERO);
        dto.put("payStatus", "job_only");
        dto.put("createdAt", job.getCreatedAt());
        dto.put("jobId", job.getId());
        dto.put("jobNo", job.getJobNo());
        dto.put("jobStatus", job.getStatus());
        dto.put("jobProgress", job.getProgress());
        dto.put("jobOnly", true);
        dto.put("deliveries", listPublicDeliveries(null, job));
        return sanitizePublicLookup(dto);
    }

    private Map<String, Object> sanitizePublicLookup(Map<String, Object> dto) {
        Map<String, Object> safe = new HashMap<>(dto);
        safe.remove("userId");
        safe.remove("channelId");
        return safe;
    }

    public Job requireByJobNo(String jobNo) {
        if (jobNo == null || jobNo.isBlank()) {
            throw new BusinessException("请输入任务号");
        }
        return jobRepository.findByJobNo(jobNo.trim())
                .orElseThrow(() -> new BusinessException("任务不存在"));
    }

    public Order requireByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new BusinessException("请输入订单号");
        }
        return orderRepository.findByOrderNo(orderNo.trim())
                .orElseThrow(() -> new BusinessException("订单不存在"));
    }

    public PaperFile requirePublicPaperFile(Order order, Long fileId) {
        if (!"paid".equals(order.getPayStatus())) {
            throw new BusinessException("订单未支付，暂不可下载");
        }
        PaperFile file = paperFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException("交付文件不存在"));
        if (order.getPaperId() == null || !order.getPaperId().equals(file.getPaperId())) {
            throw new BusinessException("文件与订单不匹配");
        }
        Job job = jobRepository.findByOrderId(order.getId()).orElse(null);
        if (job != null && file.getJobId() != null && !file.getJobId().equals(job.getId())) {
            throw new BusinessException("文件与订单不匹配");
        }
        return file;
    }

    public JobFile requirePublicJobFile(Order order, Long fileId) {
        if (!"paid".equals(order.getPayStatus())) {
            throw new BusinessException("订单未支付，暂不可下载");
        }
        Job job = jobRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new BusinessException("任务不存在"));
        return requirePublicJobFileForJob(job, fileId, true);
    }

    public JobFile requirePublicJobFileByJobNo(String jobNo, Long fileId) {
        Job job = requireByJobNo(jobNo);
        boolean requirePaidOrder = job.getOrderId() != null;
        if (requirePaidOrder) {
            Order order = orderRepository.findById(job.getOrderId())
                    .orElseThrow(() -> new BusinessException("关联订单不存在"));
            if (!"paid".equals(order.getPayStatus())) {
                throw new BusinessException("订单未支付，暂不可下载");
            }
        } else {
            assertJobReadyForDownload(job);
        }
        return requirePublicJobFileForJob(job, fileId, requirePaidOrder);
    }

    public PaperFile requirePublicPaperFileByJobNo(String jobNo, Long fileId) {
        Job job = requireByJobNo(jobNo);
        if (job.getOrderId() != null) {
            Order order = orderRepository.findById(job.getOrderId())
                    .orElseThrow(() -> new BusinessException("关联订单不存在"));
            if (!"paid".equals(order.getPayStatus())) {
                throw new BusinessException("订单未支付，暂不可下载");
            }
            return requirePublicPaperFile(order, fileId);
        }
        assertJobReadyForDownload(job);
        PaperFile file = paperFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException("交付文件不存在"));
        if (job.getPaperId() == null || !job.getPaperId().equals(file.getPaperId())) {
            throw new BusinessException("文件与任务不匹配");
        }
        if (file.getJobId() != null && !file.getJobId().equals(job.getId())) {
            throw new BusinessException("文件与任务不匹配");
        }
        return file;
    }

    private JobFile requirePublicJobFileForJob(Job job, Long fileId, boolean orderLinked) {
        if (!orderLinked) {
            assertJobReadyForDownload(job);
        }
        JobFile file = jobFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException("交付文件不存在"));
        if (!job.getId().equals(file.getJobId())) {
            throw new BusinessException("文件与任务不匹配");
        }
        return file;
    }

    private void assertJobReadyForDownload(Job job) {
        if (!"success".equals(job.getStatus())) {
            throw new BusinessException("任务未完成，暂不可下载");
        }
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    public Map<String, Object> quoteForUser(
            Long userId,
            String productId,
            String degree,
            Integer wordCount,
            String modelType
    ) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException("产品不存在"));
        BigDecimal basePrice = productPriceService.quote(productId, degree, wordCount, modelType);
        Map<String, Object> quotaQuote = vipQuotaService.quoteForOrder(userId, product, basePrice);
        Map<String, Object> m = new HashMap<>(quotaQuote);
        m.put("productId", productId);
        m.put("productLabel", product.getLabel());
        m.put("price", quotaQuote.get("finalAmount"));
        return m;
    }

    public PageResult<Map<String, Object>> listByUser(Long userId, int page, int size) {
        Page<Order> p = orderRepository.findByUserIdOrderByCreatedAtDesc(
                userId, pageRequest(page, size));
        Map<String, String> productLabels = loadProductLabels(
                p.getContent().stream().map(Order::getProductId).collect(Collectors.toSet()));
        List<Map<String, Object>> items = p.getContent().stream()
                .map(o -> toDto(o, productLabels.getOrDefault(o.getProductId(), o.getProductId())))
                .collect(Collectors.toList());
        return new PageResult<>(items, p.getTotalElements(), page, size);
    }

    private Map<String, String> loadProductLabels(java.util.Set<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> labels = new HashMap<>();
        productRepository.findAllById(productIds).forEach(p -> labels.put(p.getId(), p.getLabel()));
        return labels;
    }

    private PageRequest pageRequest(int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        return PageRequest.of(p - 1, s);
    }

    private Map<String, Object> toDto(Order o, String productLabel) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", o.getId());
        m.put("orderNo", o.getOrderNo());
        m.put("productId", o.getProductId());
        m.put("productLabel", productLabel);
        m.put("paperId", o.getPaperId());
        m.put("amount", o.getAmount());
        m.put("payStatus", o.getPayStatus());
        m.put("payMethod", o.getPayMethod());
        m.put("paidAt", o.getPaidAt());
        m.put("createdAt", o.getCreatedAt());
        attachJobInfo(m, o.getId());
        return m;
    }

    private void refreshOrderAmount(Order order) {
        Product product = productRepository.findById(order.getProductId())
                .orElseThrow(() -> new BusinessException("产品不存在"));
        BigDecimal basePrice = productPriceService.quote(
                order.getProductId(),
                order.getQuoteDegree(),
                order.getQuoteWordCount(),
                order.getQuoteModelType());
        Map<String, Object> quotaQuote = vipQuotaService.quoteForOrder(order.getUserId(), product, basePrice);
        order.setAmount((BigDecimal) quotaQuote.get("finalAmount"));
        orderRepository.save(order);
    }

    private void attachJobInfo(Map<String, Object> dto, Long orderId) {
        jobRepository.findByOrderId(orderId).ifPresent(j -> {
            dto.put("jobId", j.getId());
            dto.put("jobNo", j.getJobNo());
            dto.put("jobStatus", j.getStatus());
            dto.put("jobProgress", j.getProgress());
        });
    }

    private List<Map<String, Object>> listPublicDeliveries(Order order, Job job) {
        List<Map<String, Object>> list = new ArrayList<>();
        String orderNo = order != null ? order.getOrderNo() : null;
        String jobNo = job.getJobNo();
        String paperId = order != null && order.getPaperId() != null && !order.getPaperId().isBlank()
                ? order.getPaperId()
                : job.getPaperId();
        if (paperId != null && !paperId.isBlank()) {
            for (PaperFile f : paperFileRepository.findByPaperIdOrderByCreatedAtDesc(paperId)) {
                if (f.getJobId() != null && !f.getJobId().equals(job.getId())) {
                    continue;
                }
                list.add(publicDeliveryDto(orderNo, jobNo, f.getId(), "paper", f.getFileType(), f.getFileName()));
            }
        }
        for (JobFile f : jobFileRepository.findByJobIdOrderByCreatedAtDesc(job.getId())) {
            list.add(publicDeliveryDto(orderNo, jobNo, f.getId(), "job", f.getFileType(), f.getFileName()));
        }
        return list;
    }

    private Map<String, Object> publicDeliveryDto(
            String orderNo,
            String jobNo,
            Long fileId,
            String source,
            String fileType,
            String fileName
    ) {
        StringBuilder downloadUrl = new StringBuilder("/api/files/public/download?");
        if (orderNo != null && !orderNo.isBlank()) {
            downloadUrl.append("orderNo=")
                    .append(URLEncoder.encode(orderNo, StandardCharsets.UTF_8));
        } else if (jobNo != null && !jobNo.isBlank()) {
            downloadUrl.append("jobNo=")
                    .append(URLEncoder.encode(jobNo, StandardCharsets.UTF_8));
        }
        downloadUrl.append("&fileId=").append(fileId).append("&source=").append(source);
        Map<String, Object> m = new HashMap<>();
        m.put("id", fileId);
        m.put("source", source);
        m.put("fileType", fileType);
        m.put("fileName", fileName);
        m.put("downloadUrl", downloadUrl.toString());
        return m;
    }
}

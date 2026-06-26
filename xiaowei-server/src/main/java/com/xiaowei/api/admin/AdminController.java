package com.xiaowei.api.admin;

import com.xiaowei.common.ApiResponse;
import com.xiaowei.common.PageResult;
import com.xiaowei.domain.entity.*;
import com.xiaowei.domain.repository.*;
import com.xiaowei.service.AdminService;
import com.xiaowei.integration.AiGateway;
import com.xiaowei.service.AiConfigService;
import com.xiaowei.service.AiModelUsageService;
import com.xiaowei.service.JobService;
import com.xiaowei.service.FileStorageService;
import com.xiaowei.service.PaperFileService;
import com.xiaowei.service.ProductPriceService;
import com.xiaowei.service.DeployReadinessService;
import com.xiaowei.service.GiftCodeService;
import com.xiaowei.service.ReferralSettingsService;
import com.xiaowei.service.SiteBrandingService;
import com.xiaowei.service.SupportSettingsService;
import com.xiaowei.service.SchoolAdminService;
import com.xiaowei.service.VipQuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ProductRepository productRepository;
    private final ChannelRepository channelRepository;
    private final LiteratureRefRepository literatureRefRepository;
    private final OutlineTemplateRepository outlineTemplateRepository;
    private final JobRepository jobRepository;
    private final JobService jobService;
    private final ProductPriceService productPriceService;
    private final AiConfigService aiConfigService;
    private final AiModelUsageService aiModelUsageService;
    private final AiGateway aiGateway;
    private final PaperFileService paperFileService;
    private final FileStorageService fileStorageService;
    private final VipQuotaService vipQuotaService;
    private final GiftCodeService giftCodeService;
    private final ReferralSettingsService referralSettingsService;
    private final SupportSettingsService supportSettingsService;
    private final SiteBrandingService siteBrandingService;
    private final DeployReadinessService deployReadinessService;
    private final SchoolAdminService schoolAdminService;

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.ok(adminService.dashboard());
    }

    @GetMapping("/products")
    public ApiResponse<List<Product>> products() {
        return ApiResponse.ok(productRepository.findAll());
    }

    @PostMapping("/products")
    public ApiResponse<Product> createProduct(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(adminService.createProduct(body));
    }

    @PutMapping("/products/{id}")
    public ApiResponse<Product> updateProduct(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(adminService.updateProduct(id, body));
    }

    @GetMapping("/channels")
    public ApiResponse<List<Channel>> channels() {
        return ApiResponse.ok(channelRepository.findAll());
    }

    @PostMapping("/channels")
    public ApiResponse<Channel> createChannel(@RequestBody Channel channel) {
        return ApiResponse.ok(channelRepository.save(channel));
    }

    @PutMapping("/channels/{id}")
    public ApiResponse<Channel> updateChannel(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(adminService.updateChannel(id, body));
    }

    @GetMapping("/orders")
    public ApiResponse<PageResult<Map<String, Object>>> orders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String payStatus,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo
    ) {
        return ApiResponse.ok(adminService.listOrders(
                payStatus,
                orderNo,
                userId,
                parseInstant(createdFrom),
                parseInstantEndOfDay(createdTo),
                page,
                size));
    }

    @GetMapping("/wallet-recharges")
    public ApiResponse<PageResult<Map<String, Object>>> walletRecharges(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String payStatus,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Long userId
    ) {
        return ApiResponse.ok(adminService.listWalletRecharges(
                payStatus, orderNo, userId, page, size));
    }

    @PostMapping("/wallet-recharges/{id}/confirm")
    public ApiResponse<Map<String, Object>> confirmWalletRecharge(@PathVariable Long id) {
        return ApiResponse.ok(adminService.confirmWalletRecharge(id, currentAdminRef()));
    }

    @PostMapping("/wallet-recharges/{id}/cancel")
    public ApiResponse<Map<String, Object>> cancelWalletRecharge(@PathVariable Long id) {
        return ApiResponse.ok(adminService.cancelWalletRecharge(id));
    }

    @GetMapping("/payment-records")
    public ApiResponse<PageResult<Map<String, Object>>> paymentRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String payMethod,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tradeNo
    ) {
        return ApiResponse.ok(adminService.listPaymentRecords(
                userId, orderId, payMethod, status, tradeNo, page, size));
    }

    @PostMapping("/orders/{id}/mark-paid")
    public ApiResponse<Map<String, Object>> markOrderPaid(@PathVariable Long id) {
        return ApiResponse.ok(adminService.markOrderPaid(id));
    }

    @PostMapping("/orders/{id}/refund")
    public ApiResponse<Map<String, Object>> refundOrder(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String remark = body != null ? body.get("remark") : null;
        return ApiResponse.ok(adminService.refundOrder(id, remark));
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<Map<String, Object>>> users(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(adminService.listUsers(phone, status, page, size));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<Map<String, Object>> userDetail(@PathVariable Long id) {
        return ApiResponse.ok(adminService.getUser(id));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<Map<String, Object>> updateUser(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        return ApiResponse.ok(adminService.updateUser(id, body));
    }

    @PostMapping("/users/{id}/recharge")
    public ApiResponse<Map<String, Object>> rechargeUser(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        String remark = body.get("remark") != null ? String.valueOf(body.get("remark")) : "管理员充值";
        return ApiResponse.ok(adminService.rechargeUser(id, amount, remark, currentAdminRef()));
    }

    @PostMapping("/users/{id}/deduct")
    public ApiResponse<Map<String, Object>> deductUser(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        String remark = body.get("remark") != null ? String.valueOf(body.get("remark")) : "管理员扣款";
        return ApiResponse.ok(adminService.deductUser(id, amount, remark, currentAdminRef()));
    }

    @GetMapping("/users/{id}/wallet-logs")
    public ApiResponse<PageResult<Map<String, Object>>> walletLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(adminService.listWalletLogs(id, page, size));
    }

    @GetMapping("/jobs")
    public ApiResponse<PageResult<Map<String, Object>>> jobs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String jobNo,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userPhone,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo
    ) {
        return ApiResponse.ok(adminService.listJobs(
                status, taskType, productId, jobNo, userId, userPhone,
                parseInstant(createdFrom), parseInstant(createdTo), page, size));
    }

    @GetMapping("/jobs/stats")
    public ApiResponse<Map<String, Object>> jobStats(
            @RequestParam(defaultValue = "7") int days
    ) {
        return ApiResponse.ok(adminService.jobStats(days));
    }

    @GetMapping("/jobs/{id}")
    public ApiResponse<Map<String, Object>> jobDetail(@PathVariable Long id) {
        return ApiResponse.ok(adminService.getJob(id));
    }

    @GetMapping("/papers")
    public ApiResponse<PageResult<Map<String, Object>>> papers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String productId
    ) {
        return ApiResponse.ok(adminService.listPapers(title, productId, page, size));
    }

    @GetMapping("/papers/{id}")
    public ApiResponse<Map<String, Object>> paperDetail(@PathVariable String id) {
        return ApiResponse.ok(adminService.getPaper(id));
    }

    @GetMapping("/papers/{id}/files")
    public ApiResponse<List<Map<String, Object>>> paperFiles(@PathVariable String id) {
        return ApiResponse.ok(paperFileService.listByPaper(id));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<Map<String, Object>> orderDetail(@PathVariable Long id) {
        return ApiResponse.ok(adminService.getOrder(id));
    }

    @PostMapping("/jobs/{id}/retry")
    public ApiResponse<Void> retryJob(@PathVariable Long id) {
        adminService.retryJob(id);
        jobService.scheduleJob(id);
        return ApiResponse.ok();
    }

    @PostMapping("/jobs/batch-retry")
    public ApiResponse<Map<String, Object>> batchRetry(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.getOrDefault("ids", List.of());
        Map<String, Object> result = adminService.batchRetryJobs(ids);
        for (Long id : ids) {
            try {
                if (jobRepository.findById(id).map(j -> "pending".equals(j.getStatus())).orElse(false)) {
                    jobService.scheduleJob(id);
                }
            } catch (Exception ignored) {
            }
        }
        return ApiResponse.ok(result);
    }

    @PostMapping("/jobs/{id}/cancel")
    public ApiResponse<Void> cancelJob(@PathVariable Long id) {
        jobService.cancelJob(id);
        return ApiResponse.ok();
    }

    @GetMapping("/ai-config")
    public ApiResponse<Map<String, Object>> aiConfig() {
        return ApiResponse.ok(aiConfigService.getConfigForAdmin());
    }

    @PutMapping("/ai-config")
    public ApiResponse<Map<String, Object>> updateAiConfig(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(aiConfigService.updateConfig(body));
    }

    @PostMapping("/ai-config/test")
    public ApiResponse<Map<String, Object>> testAiConfig() {
        return ApiResponse.ok(aiGateway.testConnection());
    }

    @PostMapping("/ai-config/test-all")
    public ApiResponse<Map<String, Object>> testAllAiModels() {
        return ApiResponse.ok(aiGateway.testAllModels());
    }

    @PostMapping("/ai-config/probe-all")
    public ApiResponse<Map<String, Object>> probeAllAiModels() {
        return ApiResponse.ok(aiGateway.probeAllModels());
    }

    @GetMapping("/ai-config/token-stats")
    public ApiResponse<Map<String, Object>> aiTokenStats() {
        return ApiResponse.ok(aiModelUsageService.summary());
    }

    @GetMapping("/ai-config/pool-status")
    public ApiResponse<Map<String, Object>> aiPoolStatus() {
        return ApiResponse.ok(aiConfigService.poolRuntimeStatus());
    }

    @PostMapping("/ai-config/reset")
    public ApiResponse<Map<String, Object>> resetAiConfig() {
        return ApiResponse.ok(aiConfigService.resetToRecommended());
    }

    @GetMapping("/deploy/readiness")
    public ApiResponse<Map<String, Object>> deployReadiness() {
        return ApiResponse.ok(deployReadinessService.readiness());
    }

    @PostMapping("/deploy/disable-ai-mock")
    public ApiResponse<Map<String, Object>> disableAiMock() {
        return ApiResponse.ok(deployReadinessService.applyProductionAiMode());
    }

    @PostMapping("/deploy/disable-payment-mock")
    public ApiResponse<Map<String, Object>> disablePaymentMock() {
        return ApiResponse.ok(deployReadinessService.applyDisablePaymentMock());
    }

    @GetMapping("/literature")
    public ApiResponse<PageResult<LiteratureRef>> literature(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled
    ) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        var pageable = org.springframework.data.domain.PageRequest.of(p - 1, s);
        var result = literatureRefRepository.adminSearchPage(
                keyword != null ? keyword : "",
                enabled,
                pageable);
        return ApiResponse.ok(new PageResult<>(result.getContent(), result.getTotalElements(), p, s));
    }

    @PostMapping("/literature")
    public ApiResponse<LiteratureRef> saveLiterature(@RequestBody LiteratureRef ref) {
        return ApiResponse.ok(literatureRefRepository.save(ref));
    }

    @PostMapping("/literature/batch")
    public ApiResponse<Map<String, Object>> batchLiterature(@RequestBody List<LiteratureRef> rows) {
        if (rows == null || rows.isEmpty()) {
            return ApiResponse.fail(400, "导入列表不能为空");
        }
        int count = 0;
        for (LiteratureRef row : rows) {
            if (row.getTitle() == null || row.getTitle().isBlank()) {
                continue;
            }
            if (row.getGbtCitation() == null || row.getGbtCitation().isBlank()) {
                continue;
            }
            if (row.getLang() == null || row.getLang().isBlank()) {
                row.setLang("zh");
            }
            if (row.getEnabled() == null) {
                row.setEnabled(true);
            }
            literatureRefRepository.save(row);
            count++;
        }
        return ApiResponse.ok(Map.of("imported", count, "total", rows.size()));
    }

    @DeleteMapping("/literature/{id}")
    public ApiResponse<Void> deleteLiterature(@PathVariable Long id) {
        literatureRefRepository.deleteById(id);
        return ApiResponse.ok();
    }

    @GetMapping("/outline-templates")
    public ApiResponse<PageResult<OutlineTemplate>> templates(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String degree,
            @RequestParam(required = false) Boolean enabled
    ) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        var pageable = org.springframework.data.domain.PageRequest.of(p - 1, s);
        var result = outlineTemplateRepository.adminSearchPage(
                category != null ? category : "",
                degree != null ? degree : "",
                enabled,
                pageable);
        return ApiResponse.ok(new PageResult<>(result.getContent(), result.getTotalElements(), p, s));
    }

    @PostMapping("/outline-templates")
    public ApiResponse<OutlineTemplate> saveTemplate(@RequestBody OutlineTemplate t) {
        return ApiResponse.ok(outlineTemplateRepository.save(t));
    }

    @DeleteMapping("/outline-templates/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
        outlineTemplateRepository.deleteById(id);
        return ApiResponse.ok();
    }

    @GetMapping("/product-prices")
    public ApiResponse<List<Map<String, Object>>> productPrices(
            @RequestParam(required = false) String productId
    ) {
        return ApiResponse.ok(productPriceService.listAll(productId));
    }

    @PostMapping("/product-prices")
    public ApiResponse<Map<String, Object>> saveProductPrice(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(productPriceService.save(body));
    }

    @DeleteMapping("/product-prices/{id}")
    public ApiResponse<Void> deleteProductPrice(@PathVariable Long id) {
        productPriceService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/paper-files/{id}/download")
    public ResponseEntity<InputStreamResource> downloadPaperFile(@PathVariable Long id) throws IOException {
        var file = paperFileService.requireFile(id);
        Path path = fileStorageService.resolve(file.getStorageKey());
        InputStreamResource resource = new InputStreamResource(Files.newInputStream(path));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(path))
                .body(resource);
    }

    @GetMapping("/vip-quotas")
    public ApiResponse<List<Map<String, Object>>> vipQuotas() {
        return ApiResponse.ok(vipQuotaService.listConfigs());
    }

    @PostMapping("/vip-quotas")
    public ApiResponse<Map<String, Object>> saveVipQuota(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(vipQuotaService.saveConfig(body));
    }

    @DeleteMapping("/vip-quotas/{id}")
    public ApiResponse<Void> deleteVipQuota(@PathVariable Long id) {
        vipQuotaService.deleteConfig(id);
        return ApiResponse.ok();
    }

    @GetMapping("/users/{id}/quota")
    public ApiResponse<List<Map<String, Object>>> userQuota(@PathVariable Long id) {
        return ApiResponse.ok(vipQuotaService.getUserQuotaSummary(id));
    }

    @PostMapping("/users/{id}/reset-quota")
    public ApiResponse<Map<String, Object>> resetUserQuota(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String taskType = body != null ? body.get("taskType") : null;
        return ApiResponse.ok(vipQuotaService.resetDailyUsage(id, taskType));
    }

    @GetMapping("/channels/commission-stats")
    public ApiResponse<List<Map<String, Object>>> channelCommissionStats() {
        return ApiResponse.ok(adminService.channelCommissionStats());
    }

    @GetMapping("/branding-settings")
    public ApiResponse<Map<String, Object>> brandingSettings() {
        return ApiResponse.ok(siteBrandingService.getForAdmin());
    }

    @PutMapping("/branding-settings")
    public ApiResponse<Map<String, Object>> updateBrandingSettings(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(siteBrandingService.updateFromAdmin(body));
    }

    @PostMapping("/branding-settings/upload-logo")
    public ApiResponse<Map<String, Object>> uploadBrandingLogo(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(siteBrandingService.uploadLogo(file));
    }

    @PostMapping("/branding-settings/upload-favicon")
    public ApiResponse<Map<String, Object>> uploadBrandingFavicon(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(siteBrandingService.uploadFavicon(file));
    }

    @GetMapping("/support-settings")
    public ApiResponse<Map<String, Object>> supportSettings() {
        return ApiResponse.ok(supportSettingsService.getForAdmin());
    }

    @PutMapping("/support-settings")
    public ApiResponse<Map<String, Object>> updateSupportSettings(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(supportSettingsService.updateFromAdmin(body));
    }

    @GetMapping("/referral-settings")
    public ApiResponse<Map<String, Object>> referralSettings() {
        return ApiResponse.ok(referralSettingsService.getForAdmin());
    }

    @PutMapping("/referral-settings")
    public ApiResponse<Map<String, Object>> updateReferralSettings(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(referralSettingsService.updateFromAdmin(body));
    }

    @GetMapping("/gift-codes")
    public ApiResponse<List<GiftCode>> giftCodes() {
        return ApiResponse.ok(giftCodeService.listAll());
    }

    @PostMapping("/gift-codes")
    public ApiResponse<GiftCode> createGiftCode(@RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        Instant expiresAt = parseInstant(body.get("expiresAt") != null ? String.valueOf(body.get("expiresAt")) : null);
        return ApiResponse.ok(giftCodeService.create(amount, expiresAt));
    }

    @DeleteMapping("/gift-codes/{id}")
    public ApiResponse<Void> deleteGiftCode(@PathVariable Long id) {
        giftCodeService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/schools")
    public ApiResponse<List<Map<String, Object>>> adminSchools() {
        return ApiResponse.ok(schoolAdminService.listAll().stream()
                .map(schoolAdminService::toDto)
                .toList());
    }

    @PostMapping("/schools")
    public ApiResponse<Map<String, Object>> createSchool(@RequestBody Map<String, Object> body) {
        String id = body.get("id") != null ? String.valueOf(body.get("id")) : null;
        String name = String.valueOf(body.get("name"));
        int sortOrder = body.get("sortOrder") != null
                ? ((Number) body.get("sortOrder")).intValue() : 50;
        return ApiResponse.ok(schoolAdminService.toDto(schoolAdminService.create(id, name, sortOrder)));
    }

    @PutMapping("/schools/{id}")
    public ApiResponse<Map<String, Object>> updateSchool(
            @PathVariable String id,
            @RequestBody Map<String, Object> body
    ) {
        return ApiResponse.ok(schoolAdminService.toDto(schoolAdminService.update(id, body)));
    }

    @DeleteMapping("/schools/{id}")
    public ApiResponse<Void> disableSchool(@PathVariable String id) {
        schoolAdminService.delete(id);
        return ApiResponse.ok();
    }

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }

    /** 日期筛选结束日：若为 yyyy-MM-dd 则取当日 23:59:59 UTC 近似 */
    private Instant parseInstantEndOfDay(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.length() == 10 && raw.charAt(4) == '-' && raw.charAt(7) == '-') {
            Instant start = parseInstant(raw + "T00:00:00Z");
            return start != null ? start.plus(1, java.time.temporal.ChronoUnit.DAYS).minusMillis(1) : null;
        }
        return parseInstant(raw);
    }

    private String currentAdminRef() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "admin";
    }
}

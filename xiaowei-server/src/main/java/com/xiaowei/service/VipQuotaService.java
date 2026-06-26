package com.xiaowei.service;

import com.xiaowei.common.BusinessException;
import com.xiaowei.domain.entity.Product;
import com.xiaowei.domain.entity.User;
import com.xiaowei.domain.entity.UserDailyQuota;
import com.xiaowei.domain.entity.VipQuotaConfig;
import com.xiaowei.domain.repository.UserDailyQuotaRepository;
import com.xiaowei.domain.repository.UserRepository;
import com.xiaowei.domain.repository.VipQuotaConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VipQuotaService {

    private final UserRepository userRepository;
    private final VipQuotaConfigRepository configRepository;
    private final UserDailyQuotaRepository usageRepository;

    public List<Map<String, Object>> listConfigs() {
        return configRepository.findAllByOrderByVipLevelAscTaskTypeAsc().stream()
                .map(this::configToMap)
                .toList();
    }

    /** 对外只读：已启用的 VIP 配额规则 */
    public List<Map<String, Object>> listPublicRules() {
        return configRepository.findByEnabledTrueOrderByVipLevelAscTaskTypeAsc().stream()
                .map(cfg -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("vipLevel", cfg.getVipLevel());
                    m.put("taskType", cfg.getTaskType());
                    m.put("dailyFree", cfg.getDailyFree());
                    m.put("discountPercent", cfg.getDiscountPercent());
                    return m;
                })
                .toList();
    }

    @Transactional
    public Map<String, Object> saveConfig(Map<String, Object> body) {
        VipQuotaConfig row;
        if (body.get("id") != null) {
            Long id = Long.valueOf(String.valueOf(body.get("id")));
            row = configRepository.findById(id).orElseThrow(() -> new BusinessException("配置不存在"));
        } else {
            row = new VipQuotaConfig();
        }
        row.setVipLevel(((Number) body.get("vipLevel")).intValue());
        row.setTaskType(String.valueOf(body.get("taskType")));
        row.setDailyFree(((Number) body.get("dailyFree")).intValue());
        row.setDiscountPercent(((Number) body.get("discountPercent")).intValue());
        if (body.get("enabled") != null) {
            row.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        }
        return configToMap(configRepository.save(row));
    }

    @Transactional
    public void deleteConfig(Long id) {
        configRepository.deleteById(id);
    }

    public List<Map<String, Object>> getUserQuotaSummary(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        int vipLevel = user.getVipLevel() != null ? user.getVipLevel() : 0;
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> list = new ArrayList<>();
        for (VipQuotaConfig cfg : configRepository.findByEnabledTrueOrderByVipLevelAscTaskTypeAsc()) {
            if (!cfg.getVipLevel().equals(vipLevel)) continue;
            list.add(buildQuotaItem(userId, today, cfg));
        }
        return list;
    }

    public Map<String, Object> quoteForOrder(Long userId, Product product, BigDecimal basePrice) {
        VipQuotaConfig cfg = resolveConfig(userId, product.getTaskType());
        LocalDate today = LocalDate.now();
        int used = getUsedCount(userId, today, product.getTaskType());
        int dailyFree = cfg.getDailyFree();
        int remaining = Math.max(0, dailyFree - used);

        BigDecimal original = basePrice.setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalAmount;
        boolean willUseFree = remaining > 0;
        if (willUseFree) {
            finalAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            int discount = Math.min(100, Math.max(0, cfg.getDiscountPercent()));
            finalAmount = original.multiply(BigDecimal.valueOf(100 - discount))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        Map<String, Object> m = new HashMap<>();
        m.put("taskType", product.getTaskType());
        m.put("vipLevel", resolveVipLevel(userId));
        m.put("originalAmount", original);
        m.put("finalAmount", finalAmount);
        m.put("discountPercent", cfg.getDiscountPercent());
        m.put("dailyFree", dailyFree);
        m.put("usedToday", used);
        m.put("freeRemaining", remaining);
        m.put("willUseFreeQuota", willUseFree);
        return m;
    }

    @Transactional
    public void consumeFreeQuota(Long userId, String taskType) {
        VipQuotaConfig cfg = resolveConfig(userId, taskType);
        LocalDate today = LocalDate.now();
        UserDailyQuota usage = usageRepository.findForUpdate(userId, today, taskType)
                .orElseGet(() -> {
                    UserDailyQuota q = new UserDailyQuota();
                    q.setUserId(userId);
                    q.setUsageDate(today);
                    q.setTaskType(taskType);
                    q.setUsedCount(0);
                    return q;
                });
        if (usage.getUsedCount() >= cfg.getDailyFree()) {
            throw new BusinessException("今日 VIP 免费次数已用完");
        }
        usage.setUsedCount(usage.getUsedCount() + 1);
        usageRepository.save(usage);
    }

    @Transactional
    public boolean tryConsumeFreeForJob(Long userId, String taskType) {
        VipQuotaConfig cfg = resolveConfig(userId, taskType);
        if (cfg.getDailyFree() <= 0) {
            return false;
        }
        LocalDate today = LocalDate.now();
        int used = getUsedCount(userId, today, taskType);
        if (used >= cfg.getDailyFree()) {
            return false;
        }
        consumeFreeQuota(userId, taskType);
        return true;
    }

    public void validateFreeQuotaAvailable(Long userId, String taskType) {
        if (!hasFreeQuotaRemaining(userId, taskType)) {
            throw new BusinessException("今日 VIP 免费次数已用完，请支付后使用");
        }
    }

    public boolean hasFreeQuotaRemaining(Long userId, String taskType) {
        if (userId == null) return false;
        VipQuotaConfig cfg = resolveConfig(userId, taskType);
        if (cfg.getDailyFree() <= 0) return false;
        int used = getUsedCount(userId, LocalDate.now(), taskType);
        return used < cfg.getDailyFree();
    }

    /** 管理端：重置用户当日 VIP 用量（可选指定 taskType，否则重置今日全部记录） */
    @Transactional
    public Map<String, Object> resetDailyUsage(Long userId, String taskType) {
        userRepository.findById(userId).orElseThrow(() -> new BusinessException("用户不存在"));
        LocalDate today = LocalDate.now();
        int resetCount = 0;
        if (taskType != null && !taskType.isBlank()) {
            var row = usageRepository.findByUserIdAndUsageDateAndTaskType(userId, today, taskType);
            if (row.isPresent() && row.get().getUsedCount() != null && row.get().getUsedCount() > 0) {
                row.get().setUsedCount(0);
                usageRepository.save(row.get());
                resetCount = 1;
            }
        } else {
            for (UserDailyQuota q : usageRepository.findByUserIdAndUsageDate(userId, today)) {
                if (q.getUsedCount() != null && q.getUsedCount() > 0) {
                    q.setUsedCount(0);
                    usageRepository.save(q);
                    resetCount++;
                }
            }
        }
        Map<String, Object> m = new HashMap<>();
        m.put("userId", userId);
        m.put("resetCount", resetCount);
        m.put("message", resetCount > 0 ? "已重置今日用量" : "今日暂无用量记录");
        return m;
    }

    /** 预览/任务失败时退还 1 次当日免费额度（不低于 0） */
    @Transactional
    public void refundFreeQuotaOnce(Long userId, String taskType) {
        if (userId == null || taskType == null || taskType.isBlank()) return;
        LocalDate today = LocalDate.now();
        usageRepository.findForUpdate(userId, today, taskType).ifPresent(usage -> {
            if (usage.getUsedCount() != null && usage.getUsedCount() > 0) {
                usage.setUsedCount(usage.getUsedCount() - 1);
                usageRepository.save(usage);
            }
        });
    }

    private Map<String, Object> buildQuotaItem(Long userId, LocalDate day, VipQuotaConfig cfg) {
        int used = getUsedCount(userId, day, cfg.getTaskType());
        Map<String, Object> m = new HashMap<>();
        m.put("taskType", cfg.getTaskType());
        m.put("dailyFree", cfg.getDailyFree());
        m.put("usedToday", used);
        m.put("freeRemaining", Math.max(0, cfg.getDailyFree() - used));
        m.put("discountPercent", cfg.getDiscountPercent());
        return m;
    }

    private int getUsedCount(Long userId, LocalDate day, String taskType) {
        return usageRepository.findByUserIdAndUsageDateAndTaskType(userId, day, taskType)
                .map(UserDailyQuota::getUsedCount)
                .orElse(0);
    }

    private VipQuotaConfig resolveConfig(Long userId, String taskType) {
        int vipLevel = resolveVipLevel(userId);
        return configRepository.findByVipLevelAndTaskTypeAndEnabledTrue(vipLevel, taskType)
                .orElseGet(() -> defaultConfig(vipLevel, taskType));
    }

    private int resolveVipLevel(Long userId) {
        if (userId == null) return 0;
        return userRepository.findById(userId).map(User::getVipLevel).orElse(0);
    }

    private VipQuotaConfig defaultConfig(int vipLevel, String taskType) {
        VipQuotaConfig c = new VipQuotaConfig();
        c.setVipLevel(vipLevel);
        c.setTaskType(taskType);
        int dailyFree = 0;
        if ("paper_generate".equals(taskType) && vipLevel == 0) {
            dailyFree = 1;
        }
        c.setDailyFree(dailyFree);
        c.setDiscountPercent(0);
        c.setEnabled(true);
        return c;
    }

    private Map<String, Object> configToMap(VipQuotaConfig c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("vipLevel", c.getVipLevel());
        m.put("taskType", c.getTaskType());
        m.put("dailyFree", c.getDailyFree());
        m.put("discountPercent", c.getDiscountPercent());
        m.put("enabled", c.getEnabled());
        return m;
    }
}

package com.xiaowei.service;

import com.xiaowei.common.BusinessException;
import com.xiaowei.domain.entity.User;
import com.xiaowei.domain.entity.VipQuotaConfig;
import com.xiaowei.domain.repository.UserRepository;
import com.xiaowei.domain.repository.VipQuotaConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VipService {

    private static final Map<Integer, BigDecimal> PLAN_PRICES = Map.of(
            1, new BigDecimal("99"),
            2, new BigDecimal("199")
    );

    private static final Map<Integer, String> PLAN_NAMES = Map.of(
            1, "基础会员",
            2, "高级会员"
    );

    private final UserRepository userRepository;
    private final WalletService walletService;
    private final VipQuotaConfigRepository vipQuotaConfigRepository;

    public List<Map<String, Object>> listPlans() {
        List<Integer> levels = vipQuotaConfigRepository.findAll().stream()
                .map(VipQuotaConfig::getVipLevel)
                .filter(l -> l != null && l > 0)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        if (levels.isEmpty()) {
            levels = List.of(1, 2);
        }
        List<Map<String, Object>> plans = new ArrayList<>();
        for (Integer level : levels) {
            Map<String, Object> m = new HashMap<>();
            m.put("level", level);
            m.put("name", PLAN_NAMES.getOrDefault(level, "VIP" + level));
            m.put("price", PLAN_PRICES.getOrDefault(level, new BigDecimal("99")));
            m.put("benefits", buildBenefits(level));
            plans.add(m);
        }
        return plans;
    }

    @Transactional
    public Map<String, Object> purchaseWithBalance(Long userId, int level) {
        if (level <= 0) {
            throw new BusinessException("无效的 VIP 等级");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        int current = user.getVipLevel() != null ? user.getVipLevel() : 0;
        if (current >= level) {
            throw new BusinessException("当前 VIP 等级已不低于所选等级");
        }
        BigDecimal price = PLAN_PRICES.getOrDefault(level, new BigDecimal("99"));
        walletService.deduct(userId, price, "vip_purchase", "VIP" + level);
        user.setVipLevel(level);
        user.setUpdatedAt(java.time.Instant.now());
        userRepository.save(user);
        Map<String, Object> m = walletService.getWallet(userId);
        m.put("vipLevel", level);
        return m;
    }

    private List<String> buildBenefits(int level) {
        List<String> benefits = new ArrayList<>();
        vipQuotaConfigRepository.findAll().stream()
                .filter(c -> c.getVipLevel() != null && c.getVipLevel() == level && Boolean.TRUE.equals(c.getEnabled()))
                .forEach(c -> benefits.add(String.format(
                        "%s：每日免费 %d 次，折扣 %d%%",
                        c.getTaskType(),
                        c.getDailyFree() != null ? c.getDailyFree() : 0,
                        c.getDiscountPercent() != null ? c.getDiscountPercent() : 0
                )));
        if (benefits.isEmpty()) {
            benefits.add("专属 VIP 标识与优先客服");
        }
        return benefits;
    }
}

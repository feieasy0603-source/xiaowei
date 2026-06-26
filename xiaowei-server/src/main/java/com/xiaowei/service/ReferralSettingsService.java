package com.xiaowei.service;

import com.xiaowei.common.BusinessException;
import com.xiaowei.domain.entity.ReferralSettings;
import com.xiaowei.domain.repository.ReferralSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReferralSettingsService {

    private static final long SETTINGS_ID = 1L;

    private final ReferralSettingsRepository repository;

    @Value("${xiaowei.referral.inviter-reward:5.00}")
    private BigDecimal defaultInviterReward;

    @Value("${xiaowei.referral.invitee-reward:2.00}")
    private BigDecimal defaultInviteeReward;

    public record ResolvedReferralRewards(
            BigDecimal inviterReward,
            BigDecimal inviteeReward,
            boolean enabled,
            String rulesText
    ) {}

    public ResolvedReferralRewards resolve() {
        ReferralSettings row = repository.findById(SETTINGS_ID).orElse(null);
        if (row == null) {
            return new ResolvedReferralRewards(
                    defaultInviterReward,
                    defaultInviteeReward,
                    true,
                    null);
        }
        return new ResolvedReferralRewards(
                row.getInviterReward() != null ? row.getInviterReward() : defaultInviterReward,
                row.getInviteeReward() != null ? row.getInviteeReward() : defaultInviteeReward,
                row.getEnabled() == null || row.getEnabled(),
                blankToNull(row.getRulesText()));
    }

    public Map<String, Object> getForAdmin() {
        ReferralSettings row = ensureRow();
        Map<String, Object> m = new HashMap<>();
        m.put("inviterReward", row.getInviterReward());
        m.put("inviteeReward", row.getInviteeReward());
        m.put("rulesText", row.getRulesText());
        m.put("enabled", row.getEnabled());
        m.put("updatedAt", row.getUpdatedAt() != null ? row.getUpdatedAt().toString() : null);
        m.put("defaultInviterReward", defaultInviterReward);
        m.put("defaultInviteeReward", defaultInviteeReward);
        return m;
    }

    @Transactional
    public Map<String, Object> updateFromAdmin(Map<String, Object> body) {
        ReferralSettings row = ensureRow();
        if (body.get("inviterReward") != null) {
            row.setInviterReward(parseAmount(body.get("inviterReward"), "邀请人奖励"));
        }
        if (body.get("inviteeReward") != null) {
            row.setInviteeReward(parseAmount(body.get("inviteeReward"), "被邀请人奖励"));
        }
        if (body.get("enabled") != null) {
            row.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        }
        if (body.containsKey("rulesText")) {
            Object raw = body.get("rulesText");
            String text = raw == null ? null : String.valueOf(raw).trim();
            row.setRulesText(blankToNull(text));
        }
        row.setUpdatedAt(Instant.now());
        repository.save(row);
        return getForAdmin();
    }

    private ReferralSettings ensureRow() {
        return repository.findById(SETTINGS_ID).orElseGet(() -> {
            ReferralSettings s = new ReferralSettings();
            s.setId(SETTINGS_ID);
            s.setInviterReward(defaultInviterReward);
            s.setInviteeReward(defaultInviteeReward);
            s.setEnabled(true);
            s.setUpdatedAt(Instant.now());
            return repository.save(s);
        });
    }

    private BigDecimal parseAmount(Object raw, String label) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(String.valueOf(raw)).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (Exception e) {
            throw new BusinessException(label + "格式无效");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(label + "不能为负数");
        }
        if (amount.compareTo(new BigDecimal("99999")) > 0) {
            throw new BusinessException(label + "不能超过 99999");
        }
        return amount;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

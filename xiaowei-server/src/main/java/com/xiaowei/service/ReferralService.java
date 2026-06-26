package com.xiaowei.service;

import com.xiaowei.common.BusinessException;
import com.xiaowei.domain.entity.User;
import com.xiaowei.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReferralService {

    private final UserRepository userRepository;
    private final WalletService walletService;
    private final ReferralSettingsService referralSettingsService;

    @Value("${xiaowei.referral.frontend-base-url:}")
    private String frontendBaseUrl;

    private boolean isShareLinkRelative() {
        return frontendBaseUrl == null || frontendBaseUrl.isBlank();
    }

    private ReferralSettingsService.ResolvedReferralRewards rewards() {
        return referralSettingsService.resolve();
    }

    public String ensureReferralCode(User user) {
        if (user.getReferralCode() != null && !user.getReferralCode().isBlank()) {
            return user.getReferralCode();
        }
        String code;
        int tries = 0;
        do {
            code = "U" + Long.toHexString(user.getId() != null ? user.getId() : 0L)
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 4);
            code = code.toUpperCase().substring(0, Math.min(12, code.length()));
            tries++;
        } while (userRepository.findByReferralCode(code).isPresent() && tries < 8);
        user.setReferralCode(code);
        userRepository.save(user);
        return code;
    }

    public Map<String, Object> shareInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        String code = ensureReferralCode(user);
        long invited = userRepository.countByInvitedByUserId(userId);
        String base = resolveFrontendBase();
        String link = base + "#/iw/intelligentWriting/0?ref=" + code;

        Map<String, Object> m = new HashMap<>();
        m.put("referralCode", code);
        m.put("shareLink", link);
        m.put("invitedCount", invited);
        var cfg = rewards();
        m.put("inviterReward", cfg.inviterReward());
        m.put("inviteeReward", cfg.inviteeReward());
        m.put("enabled", cfg.enabled());
        m.put("rules", buildRulesText(cfg));
        m.put("shareLinkRelative", isShareLinkRelative());
        return m;
    }

    @Transactional
    public Map<String, Object> applyInviteOnRegister(User newUser, String inviteCode) {
        Map<String, Object> result = new HashMap<>();
        result.put("inviteApplied", false);
        result.put("inviteInvalid", false);
        if (inviteCode == null || inviteCode.isBlank()) {
            return result;
        }
        String code = inviteCode.trim().toUpperCase();
        User inviter = userRepository.findByReferralCode(code).orElse(null);
        if (inviter == null) {
            result.put("inviteInvalid", true);
            return result;
        }
        if (inviter.getId().equals(newUser.getId())) {
            result.put("inviteInvalid", true);
            return result;
        }
        newUser.setInvitedByUserId(inviter.getId());
        userRepository.save(newUser);

        var cfg = rewards();
        result.put("inviteApplied", true);
        result.put("inviteRewardEnabled", cfg.enabled());
        if (!cfg.enabled()) {
            return result;
        }
        if (cfg.inviteeReward().compareTo(BigDecimal.ZERO) > 0) {
            walletService.reward(newUser.getId(), cfg.inviteeReward(), "referral_invitee",
                    String.valueOf(inviter.getId()), "好友邀请注册奖励");
            result.put("inviteeReward", cfg.inviteeReward());
        }
        if (cfg.inviterReward().compareTo(BigDecimal.ZERO) > 0) {
            walletService.reward(inviter.getId(), cfg.inviterReward(), "referral_inviter",
                    String.valueOf(newUser.getId()), "邀请好友注册奖励");
        }
        return result;
    }

    private String resolveFrontendBase() {
        if (frontendBaseUrl != null && !frontendBaseUrl.isBlank()) {
            String u = frontendBaseUrl.trim();
            return u.endsWith("/") ? u : u + "/";
        }
        return "/";
    }

    private String buildRulesText(ReferralSettingsService.ResolvedReferralRewards cfg) {
        if (cfg.rulesText() != null) {
            return cfg.rulesText();
        }
        if (!cfg.enabled()) {
            return "分享活动暂未开启奖励发放，您仍可分享链接邀请好友注册。";
        }
        return String.format(
                "分享专属链接，好友通过链接注册成功后：您获得 ¥%s 余额奖励，好友获得 ¥%s 注册礼。同一好友仅计一次；请勿刷号，违规将取消奖励。",
                cfg.inviterReward().stripTrailingZeros().toPlainString(),
                cfg.inviteeReward().stripTrailingZeros().toPlainString());
    }
}

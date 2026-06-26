package com.xiaowei.service;

import com.xiaowei.common.BusinessException;
import com.xiaowei.domain.entity.GiftCode;
import com.xiaowei.domain.repository.GiftCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GiftCodeService {

    private final GiftCodeRepository giftCodeRepository;
    private final WalletService walletService;

    @Transactional
    public Map<String, Object> redeem(Long userId, String codeRaw) {
        String code = codeRaw == null ? "" : codeRaw.trim();
        if (code.isBlank()) {
            throw new BusinessException("请输入礼包码");
        }
        GiftCode gift = giftCodeRepository.findByCodeIgnoreCaseForUpdate(code)
                .orElseThrow(() -> new BusinessException("礼包码无效"));
        if (gift.getUsedBy() != null) {
            throw new BusinessException("礼包码已被使用");
        }
        if (gift.getExpiresAt() != null && gift.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("礼包码已过期");
        }
        gift.setUsedBy(userId);
        gift.setUsedAt(Instant.now());
        giftCodeRepository.save(gift);
        Map<String, Object> wallet = walletService.reward(
                userId,
                gift.getAmount(),
                "gift_code",
                String.valueOf(gift.getId()),
                "礼包码兑换 ¥" + gift.getAmount());
        Map<String, Object> m = new HashMap<>(wallet);
        m.put("giftAmount", gift.getAmount());
        m.put("message", "礼包码兑换成功，已充值 ¥" + gift.getAmount());
        return m;
    }

    public List<GiftCode> listAll() {
        return giftCodeRepository.findAll();
    }

    @Transactional
    public GiftCode create(BigDecimal amount, Instant expiresAt) {
        GiftCode g = new GiftCode();
        g.setCode("GIFT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        g.setAmount(amount);
        g.setExpiresAt(expiresAt);
        return giftCodeRepository.save(g);
    }

    @Transactional
    public void delete(Long id) {
        GiftCode g = giftCodeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("礼包码不存在"));
        if (g.getUsedBy() != null) {
            throw new BusinessException("已使用的礼包码不可删除");
        }
        giftCodeRepository.delete(g);
    }
}

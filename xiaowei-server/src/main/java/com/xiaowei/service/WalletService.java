package com.xiaowei.service;

import com.xiaowei.common.BusinessException;
import com.xiaowei.domain.entity.User;
import com.xiaowei.domain.entity.WalletLog;
import com.xiaowei.domain.repository.UserRepository;
import com.xiaowei.domain.repository.WalletLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final UserRepository userRepository;
    private final WalletLogRepository walletLogRepository;

    public Map<String, Object> getWallet(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        Map<String, Object> m = new HashMap<>();
        m.put("balance", user.getBalance());
        m.put("vipLevel", user.getVipLevel());
        return m;
    }

    @Transactional
    public Map<String, Object> recharge(Long userId, BigDecimal amount) {
        return applyChange(userId, amount, "recharge", "user_recharge", null, "用户自助充值");
    }

    @Transactional
    public Map<String, Object> reward(Long userId, BigDecimal amount, String refType, String refId, String remark) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return getWallet(userId);
        }
        return applyChange(userId, amount, "recharge", refType, refId, remark);
    }

    public Map<String, Object> listLogs(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size)));
        Page<WalletLog> logs = walletLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        List<Map<String, Object>> items = logs.getContent().stream().map(log -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", log.getId());
            m.put("type", log.getType());
            m.put("amount", log.getAmount());
            m.put("balanceAfter", log.getBalanceAfter());
            m.put("refType", log.getRefType());
            m.put("refId", log.getRefId());
            m.put("remark", log.getRemark());
            m.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("page", page);
        result.put("size", size);
        result.put("total", logs.getTotalElements());
        return result;
    }

    @Transactional
    public Map<String, Object> adminRecharge(Long userId, BigDecimal amount, String remark, String adminRef) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("充值金额须大于 0");
        }
        return applyChange(userId, amount, "recharge", "admin_recharge", adminRef, remark);
    }

    @Transactional
    public Map<String, Object> adminDeduct(Long userId, BigDecimal amount, String remark, String adminRef) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("扣款金额须大于 0");
        }
        return applyChange(userId, amount.negate(), "deduct", "admin_deduct", adminRef, remark);
    }

    @Transactional
    public void deduct(Long userId, BigDecimal amount, String refType, String refId) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("扣款金额须大于 0");
        }
        applyChange(userId, amount.negate(), "deduct", refType, refId, null);
    }

    private Map<String, Object> applyChange(
            Long userId,
            BigDecimal delta,
            String type,
            String refType,
            String refId,
            String remark
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        BigDecimal next = user.getBalance().add(delta);
        if (next.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("余额不足");
        }
        user.setBalance(next);
        user.setUpdatedAt(java.time.Instant.now());
        userRepository.save(user);

        WalletLog log = new WalletLog();
        log.setUserId(userId);
        log.setType(type);
        log.setAmount(delta.abs());
        log.setBalanceAfter(next);
        log.setRefType(refType);
        log.setRefId(refId);
        log.setRemark(remark);
        walletLogRepository.save(log);

        return getWallet(userId);
    }
}

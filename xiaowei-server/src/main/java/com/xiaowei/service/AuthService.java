package com.xiaowei.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaowei.common.BusinessException;
import com.xiaowei.domain.entity.AdminUser;
import com.xiaowei.domain.entity.User;
import com.xiaowei.domain.repository.AdminUserRepository;
import com.xiaowei.domain.repository.UserRepository;
import com.xiaowei.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ReferralService referralService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public Map<String, Object> register(String phone, String password, String nickname, String inviteCode) {
        if (userRepository.findByPhone(phone).isPresent()) {
            throw new BusinessException("该手机号已注册");
        }
        User user = new User();
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(resolveNickname(phone, nickname));
        user.setBalance(new BigDecimal("100.00"));
        user.setVipLevel(0);
        User saved = userRepository.save(user);
        referralService.ensureReferralCode(saved);
        Map<String, Object> result = tokenForUser(saved);
        result.putAll(referralService.applyInviteOnRegister(saved, inviteCode));
        return result;
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword, String confirmPassword) {
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            throw new BusinessException("两次输入的新密码不一致");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException("当前密码错误");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException("新密码不能与当前密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    @Transactional
    public Map<String, Object> login(String phone, String password) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new BusinessException("用户不存在，请先注册"));
        if ("disabled".equals(user.getStatus())) {
            throw new BusinessException("账号已禁用");
        }
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException("密码错误");
        }
        return tokenForUser(user);
    }

    @Value("${xiaowei.auth.demo-enabled:false}")
    private boolean demoEnabled;

    @Transactional
    public Map<String, Object> demoLogin() {
        if (!demoEnabled) {
            throw new BusinessException("演示登录未开放");
        }
        User user = userRepository.findByPhone("demo")
                .orElseGet(() -> registerDemo());
        if (user.getVipLevel() == null || user.getVipLevel() < 1) {
            user.setVipLevel(1);
            userRepository.save(user);
        }
        if (user.getBalance() == null || user.getBalance().compareTo(new BigDecimal("10")) < 0) {
            user.setBalance(new BigDecimal("100.00"));
            userRepository.save(user);
        }
        return tokenForUser(user);
    }

    @Transactional
    public Map<String, Object> adminLogin(String username, String password) {
        AdminUser admin = adminUserRepository.findByUsernameAndEnabledTrue(username)
                .orElseThrow(() -> new BusinessException("管理员不存在"));
        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new BusinessException("密码错误");
        }
        String token = jwtService.generate("admin:" + admin.getId(), Map.of(
                "type", "ADMIN",
                "role", admin.getRole(),
                "adminId", admin.getId()
        ));
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("username", admin.getUsername());
        result.put("nickname", admin.getNickname());
        result.put("role", admin.getRole());
        return result;
    }

    public Map<String, Object> me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        referralService.ensureReferralCode(user);
        return userToMap(user);
    }

    public Map<String, Object> shareInfo(Long userId) {
        return referralService.shareInfo(userId);
    }

    @Transactional
    public Map<String, Object> updateProfile(Long userId, Map<String, Object> body) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (body.get("nickname") != null) {
            String nick = String.valueOf(body.get("nickname")).trim();
            if (!nick.isBlank()) {
                user.setNickname(nick);
            }
        }
        if (body.get("preferences") instanceof Map<?, ?> prefs) {
            try {
                user.setPreferencesJson(objectMapper.writeValueAsString(prefs));
            } catch (Exception e) {
                throw new BusinessException("偏好设置格式无效");
            }
        }
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        return userToMap(user);
    }

    private Map<String, Object> userToMap(User user) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", user.getId());
        m.put("phone", user.getPhone());
        m.put("nickname", user.getNickname());
        m.put("balance", user.getBalance());
        m.put("vipLevel", user.getVipLevel());
        if (user.getPreferencesJson() != null && !user.getPreferencesJson().isBlank()) {
            try {
                m.put("preferences", objectMapper.readValue(
                        user.getPreferencesJson(), new TypeReference<Map<String, Object>>() {}));
            } catch (Exception ignored) {
                m.put("preferences", Map.of());
            }
        } else {
            m.put("preferences", Map.of());
        }
        return m;
    }

    private User registerDemo() {
        User user = new User();
        user.setPhone("demo");
        user.setPasswordHash(passwordEncoder.encode("demo123"));
        user.setNickname("演示用户");
        user.setBalance(new BigDecimal("100.00"));
        user.setVipLevel(1);
        return userRepository.save(user);
    }

    private String resolveNickname(String phone, String nickname) {
        if (nickname != null && !nickname.isBlank()) {
            return nickname.trim();
        }
        return "用户" + phone.substring(Math.max(0, phone.length() - 4));
    }

    private Map<String, Object> tokenForUser(User user) {
        String token = jwtService.generate("user:" + user.getId(), Map.of(
                "type", "USER",
                "role", "USER",
                "userId", user.getId()
        ));
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("phone", user.getPhone());
        result.put("nickname", user.getNickname());
        result.put("balance", user.getBalance());
        result.put("vipLevel", user.getVipLevel() != null ? user.getVipLevel() : 0);
        return result;
    }
}

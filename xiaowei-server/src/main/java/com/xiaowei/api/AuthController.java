package com.xiaowei.api;

import com.xiaowei.api.dto.ChangePasswordRequest;
import com.xiaowei.api.dto.LoginRequest;
import com.xiaowei.api.dto.RegisterRequest;
import com.xiaowei.common.BusinessException;
import com.xiaowei.common.ApiResponse;
import com.xiaowei.security.SecurityUtils;
import com.xiaowei.service.AuthService;
import com.xiaowei.service.RequestRateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RequestRateLimiterService rateLimiter;

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(
            HttpServletRequest request,
            @RequestBody @Valid RegisterRequest body) {
        rateLimiter.check("auth:register:" + clientIp(request), 5, 60);
        return ApiResponse.ok(authService.register(
                body.getPhone(), body.getPassword(), body.getNickname(), body.getInviteCode()));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(
            HttpServletRequest request,
            @RequestBody @Valid LoginRequest body) {
        rateLimiter.check("auth:login:ip:" + clientIp(request), 20, 60);
        rateLimiter.check("auth:login:phone:" + body.getPhone(), 8, 60);
        return ApiResponse.ok(authService.login(body.getPhone(), body.getPassword()));
    }

    @PostMapping("/demo")
    public ApiResponse<Map<String, Object>> demo() {
        return ApiResponse.ok(authService.demoLogin());
    }

    @PostMapping("/admin/login")
    public ApiResponse<Map<String, Object>> adminLogin(
            HttpServletRequest request,
            @RequestBody Map<String, String> body) {
        String username = body.get("username");
        rateLimiter.check("auth:admin:ip:" + clientIp(request), 10, 60);
        rateLimiter.check("auth:admin:user:" + username, 5, 60);
        return ApiResponse.ok(authService.adminLogin(body.get("username"), body.get("password")));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        return ApiResponse.ok(authService.me(uid));
    }

    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@RequestBody Map<String, Object> body) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        return ApiResponse.ok(authService.updateProfile(uid, body));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@RequestBody @Valid ChangePasswordRequest body) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        if (!body.isNewPasswordConfirmed()) {
            throw new BusinessException("两次输入的新密码不一致");
        }
        authService.changePassword(uid, body.getOldPassword(), body.getNewPassword(), body.getConfirmPassword());
        return ApiResponse.ok();
    }

    @GetMapping("/share-info")
    public ApiResponse<Map<String, Object>> shareInfo() {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        return ApiResponse.ok(authService.shareInfo(uid));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }
}

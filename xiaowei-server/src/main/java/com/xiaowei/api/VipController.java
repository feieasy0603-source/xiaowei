package com.xiaowei.api;

import com.xiaowei.common.ApiResponse;
import com.xiaowei.security.SecurityUtils;
import com.xiaowei.service.VipQuotaService;
import com.xiaowei.service.VipService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/vip")
@RequiredArgsConstructor
public class VipController {

    private final VipService vipService;
    private final VipQuotaService vipQuotaService;

    @GetMapping("/rules")
    public ApiResponse<List<Map<String, Object>>> rules() {
        return ApiResponse.ok(vipQuotaService.listPublicRules());
    }

    @GetMapping("/plans")
    public ApiResponse<List<Map<String, Object>>> plans() {
        return ApiResponse.ok(vipService.listPlans());
    }

    @PostMapping("/purchase")
    public ApiResponse<Map<String, Object>> purchase(@RequestBody Map<String, Object> body) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        int level = ((Number) body.get("level")).intValue();
        return ApiResponse.ok(vipService.purchaseWithBalance(uid, level));
    }
}

package com.xiaowei.api;

import com.xiaowei.common.ApiResponse;
import com.xiaowei.security.SecurityUtils;
import com.xiaowei.service.PaperService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/papers")
@RequiredArgsConstructor
public class PaperController {

    private final PaperService paperService;

    @GetMapping("/mine")
    public ApiResponse<List<Map<String, Object>>> mine(
            @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(paperService.listByUser(requireUserId(), limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String id) {
        return ApiResponse.ok(paperService.get(id, requireUserId()));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, String> body) {
        String productId = body.getOrDefault("productId", "graduation");
        return ApiResponse.ok(paperService.create(requireUserId(), productId));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> save(@PathVariable String id, @RequestBody Map<String, Object> draft) {
        return ApiResponse.ok(paperService.save(id, draft, requireUserId()));
    }

    private Long requireUserId() {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) {
            throw new com.xiaowei.common.BusinessException("请先登录");
        }
        return uid;
    }
}

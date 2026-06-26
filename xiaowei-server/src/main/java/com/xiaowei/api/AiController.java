package com.xiaowei.api;

import com.xiaowei.common.ApiResponse;
import com.xiaowei.common.BusinessException;
import com.xiaowei.security.SecurityUtils;
import com.xiaowei.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping("/polish-title")
    public ApiResponse<Map<String, Object>> polishTitle(@RequestBody Map<String, String> body) {
        requireLogin();
        return ApiResponse.ok(aiService.polishTitle(body.get("title")));
    }

    @PostMapping("/recommend-titles")
    public ApiResponse<Map<String, Object>> recommendTitles(@RequestBody Map<String, String> body) {
        requireLogin();
        return ApiResponse.ok(aiService.recommendTitles(
                body.getOrDefault("keyword", ""),
                body.get("productId")));
    }

    @PostMapping("/outline/search")
    public ApiResponse<List<Map<String, Object>>> searchOutline(@RequestBody Map<String, String> body) {
        requireLogin();
        return ApiResponse.ok(aiService.searchOutline(
                body.get("title"), body.getOrDefault("degree", "本科")));
    }

    @PostMapping("/outline/generate")
    public ApiResponse<Map<String, Object>> generateOutline(@RequestBody Map<String, Object> body) {
        requireLogin();
        int depth = body.get("depth") != null ? ((Number) body.get("depth")).intValue() : 2;
        return ApiResponse.ok(aiService.generateOutline(String.valueOf(body.get("title")), depth));
    }

    private void requireLogin() {
        if (SecurityUtils.currentUserId() == null) {
            throw new BusinessException("请先登录");
        }
    }

    @GetMapping("/literature/search")
    public ApiResponse<List<Map<String, Object>>> searchLiterature(@RequestParam String keyword) {
        requireLogin();
        return ApiResponse.ok(aiService.searchLiterature(keyword));
    }

    @PostMapping("/parse-proposal")
    public ApiResponse<Map<String, Object>> parseProposal(@RequestParam("file") MultipartFile file) {
        requireLogin();
        return ApiResponse.ok(aiService.parseProposal(file));
    }
}

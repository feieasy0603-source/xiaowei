package com.xiaowei.api;

import com.xiaowei.common.ApiResponse;
import com.xiaowei.security.SecurityUtils;
import com.xiaowei.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long uid = requireUserId();
        String productId = String.valueOf(body.get("productId"));
        String paperId = body.get("paperId") != null ? String.valueOf(body.get("paperId")) : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = body.containsKey("payload")
                ? (Map<String, Object>) body.get("payload")
                : body;
        if (paperId != null) payload.put("paperId", paperId);
        return ApiResponse.ok(jobService.createJob(uid, productId, paperId, payload));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        return ApiResponse.ok(jobService.getJob(id, requireUserId()));
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long id) {
        jobService.assertJobAccess(id, requireUserId());
        return jobService.stream(id);
    }

    @PostMapping("/{id}/retry")
    public ApiResponse<Map<String, Object>> retry(@PathVariable Long id) {
        return ApiResponse.ok(jobService.retryJobForUser(id, requireUserId()));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable Long id) {
        return ApiResponse.ok(jobService.cancelJobForUser(id, requireUserId()));
    }

    @GetMapping("/by-paper/{paperId}")
    public ApiResponse<Map<String, Object>> byPaper(@PathVariable String paperId) {
        return ApiResponse.ok(jobService.getPreviewJobForPaper(paperId, requireUserId()));
    }

    private Long requireUserId() {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) {
            throw new com.xiaowei.common.BusinessException("请先登录");
        }
        return uid;
    }
}

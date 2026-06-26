package com.xiaowei.api;

import com.xiaowei.common.ApiResponse;
import com.xiaowei.domain.entity.Job;
import com.xiaowei.domain.entity.JobFile;
import com.xiaowei.domain.entity.Order;
import com.xiaowei.domain.entity.Paper;
import com.xiaowei.domain.entity.PaperFile;
import com.xiaowei.domain.repository.PaperRepository;
import com.xiaowei.security.SecurityUtils;
import com.xiaowei.service.FileStorageService;
import com.xiaowei.service.JobFileService;
import com.xiaowei.service.OrderService;
import com.xiaowei.service.PaperFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;
    private final PaperFileService paperFileService;
    private final JobFileService jobFileService;
    private final PaperRepository paperRepository;
    private final OrderService orderService;

    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (SecurityUtils.currentUserId() == null) {
            throw new com.xiaowei.common.BusinessException("请先登录");
        }
        return ApiResponse.ok(fileStorageService.upload(file));
    }

    @GetMapping("/papers/{paperId}/deliveries")
    public ApiResponse<List<Map<String, Object>>> listDeliveries(@PathVariable String paperId) {
        assertPaperAccess(paperId);
        return ApiResponse.ok(paperFileService.listByPaper(paperId));
    }

    @GetMapping("/jobs/{jobId}/deliveries")
    public ApiResponse<List<Map<String, Object>>> listJobDeliveries(@PathVariable Long jobId) {
        Long uid = requireUserId();
        return ApiResponse.ok(jobFileService.listByJob(jobId, uid));
    }

    @GetMapping("/delivery/{fileId}/download")
    public ResponseEntity<InputStreamResource> downloadDelivery(@PathVariable Long fileId) throws IOException {
        var file = paperFileService.requireFile(fileId);
        assertPaperAccess(file.getPaperId());
        return streamDownload(file.getFileName(), file.getStorageKey());
    }

    @GetMapping("/job-delivery/{fileId}/download")
    public ResponseEntity<InputStreamResource> downloadJobDelivery(@PathVariable Long fileId) throws IOException {
        Long uid = requireUserId();
        JobFile file = jobFileService.requireFile(fileId);
        jobFileService.assertFileAccess(file, uid);
        return streamDownload(file.getFileName(), file.getStorageKey());
    }

    /** 游客凭订单号或任务号下载交付文件 */
    @GetMapping("/public/download")
    public ResponseEntity<InputStreamResource> publicDownload(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String jobNo,
            @RequestParam Long fileId,
            @RequestParam String source
    ) throws IOException {
        boolean hasOrder = orderNo != null && !orderNo.isBlank();
        boolean hasJob = jobNo != null && !jobNo.isBlank();
        if (hasOrder == hasJob) {
            throw new com.xiaowei.common.BusinessException("请提供订单号或任务号");
        }
        if (hasOrder) {
            Order order = orderService.requireByOrderNo(orderNo);
            if ("job".equalsIgnoreCase(source)) {
                JobFile file = orderService.requirePublicJobFile(order, fileId);
                return streamDownload(file.getFileName(), file.getStorageKey());
            }
            if ("paper".equalsIgnoreCase(source)) {
                PaperFile file = orderService.requirePublicPaperFile(order, fileId);
                return streamDownload(file.getFileName(), file.getStorageKey());
            }
            throw new com.xiaowei.common.BusinessException("无效的文件来源");
        }
        if ("job".equalsIgnoreCase(source)) {
            JobFile file = orderService.requirePublicJobFileByJobNo(jobNo, fileId);
            return streamDownload(file.getFileName(), file.getStorageKey());
        }
        if ("paper".equalsIgnoreCase(source)) {
            PaperFile file = orderService.requirePublicPaperFileByJobNo(jobNo, fileId);
            return streamDownload(file.getFileName(), file.getStorageKey());
        }
        throw new com.xiaowei.common.BusinessException("无效的文件来源");
    }

    private ResponseEntity<InputStreamResource> streamDownload(String fileName, String storageKey)
            throws IOException {
        Path path = fileStorageService.resolve(storageKey);
        InputStreamResource resource = new InputStreamResource(Files.newInputStream(path));
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(path))
                .body(resource);
    }

    private Long requireUserId() {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) {
            throw new com.xiaowei.common.BusinessException("请先登录");
        }
        return uid;
    }

    private void assertPaperAccess(String paperId) {
        Long uid = requireUserId();
        if (paperId == null || paperId.isBlank()) {
            return;
        }
        Paper paper = paperRepository.findById(paperId).orElse(null);
        if (paper != null && paper.getUserId() != null && !paper.getUserId().equals(uid)) {
            throw new com.xiaowei.common.BusinessException("无权下载");
        }
    }
}

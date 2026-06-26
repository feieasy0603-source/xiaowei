package com.xiaowei.service;

import com.xiaowei.domain.entity.Job;
import com.xiaowei.domain.entity.Order;
import com.xiaowei.domain.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderFulfillmentService {

    private final JobRepository jobRepository;
    private final JobService jobService;
    private final PaperService paperService;

    public Map<String, Object> fulfillPaidOrder(Order order) {
        return jobRepository.findByOrderId(order.getId())
                .map(j -> {
                    if ("pending".equals(j.getStatus()) || "failed".equals(j.getStatus())) {
                        jobService.scheduleJob(j.getId());
                    }
                    return jobService.getJob(j.getId(), order.getUserId());
                })
                .orElseGet(() -> jobService.createJobForOrder(order, buildPayload(order)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildPayload(Order order) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("source", "order_paid");
        if (order.getPaperId() == null || order.getPaperId().isBlank()) {
            return payload;
        }
        payload.put("paperId", order.getPaperId());
        attachPreviewReuse(order, payload);
        try {
            Map<String, Object> draft = paperService.getInternal(order.getPaperId());
            if (draft.get("title") != null) {
                payload.put("title", String.valueOf(draft.get("title")));
            }
            PaperDraftHelper.mergeDraftIntoPayload(draft, payload);
        } catch (Exception ignored) {
            /* 草稿缺失时仍创建任务 */
        }
        return payload;
    }

    private void attachPreviewReuse(Order order, Map<String, Object> payload) {
        if (order.getPaperId() == null || order.getPaperId().isBlank()) {
            return;
        }
        Optional<Job> preview = jobRepository
                .findFirstByPaperIdAndUserIdAndOrderIdIsNullAndStatusOrderByFinishedAtDesc(
                        order.getPaperId(), order.getUserId(), "success");
        preview.ifPresent(job -> {
            payload.put("skipAi", true);
            payload.put("sourceJobId", job.getId());
        });
    }
}

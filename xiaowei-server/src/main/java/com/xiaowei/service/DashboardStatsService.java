package com.xiaowei.service;

import com.xiaowei.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/** 管理端看板统计：聚合查询，避免多次全表 count。 */
@Service
@RequiredArgsConstructor
public class DashboardStatsService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final JobRepository jobRepository;
    private final PaperRepository paperRepository;
    private final ProductRepository productRepository;

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new HashMap<>();
        m.put("userCount", userRepository.count());
        m.put("orderCount", orderRepository.count());
        m.put("jobCount", jobRepository.count());
        m.put("paperCount", paperRepository.count());
        m.put("productCount", productRepository.count());
        m.put("totalBalance", userRepository.sumBalance());
        m.put("paidOrderAmount", orderRepository.sumPaidAmount());
        m.put("paidOrderCount", orderRepository.countByPayStatus("paid"));
        m.put("unpaidOrderCount", orderRepository.countByPayStatus("unpaid"));
        m.put("jobsPending", jobRepository.countByStatus("pending"));
        m.put("jobsRunning", jobRepository.countByStatus("running"));
        m.put("jobsSuccess", jobRepository.countByStatus("success"));
        m.put("jobsFailed", jobRepository.countByStatus("failed"));

        Map<String, Long> byTaskType = new HashMap<>();
        for (Object[] row : jobRepository.countGroupByTaskType()) {
            byTaskType.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        m.put("jobsByTaskType", byTaskType);
        return m;
    }
}

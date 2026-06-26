package com.xiaowei.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Data
@Entity
@Table(name = "user_daily_quota")
public class UserDailyQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "task_type", nullable = false)
    private String taskType;

    @Column(name = "used_count", nullable = false)
    private Integer usedCount = 0;
}

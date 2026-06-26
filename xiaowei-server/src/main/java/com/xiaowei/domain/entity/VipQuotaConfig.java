package com.xiaowei.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "vip_quota_config")
public class VipQuotaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vip_level", nullable = false)
    private Integer vipLevel;

    @Column(name = "task_type", nullable = false)
    private String taskType;

    @Column(name = "daily_free", nullable = false)
    private Integer dailyFree = 0;

    @Column(name = "discount_percent", nullable = false)
    private Integer discountPercent = 0;

    private Boolean enabled = true;
}

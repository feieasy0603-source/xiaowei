package com.xiaowei.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Entity
@Table(name = "referral_settings")
public class ReferralSettings {

    @Id
    private Long id = 1L;

    @Column(nullable = false)
    private BigDecimal inviterReward = new BigDecimal("5.00");

    @Column(nullable = false)
    private BigDecimal inviteeReward = new BigDecimal("2.00");

    @Column(columnDefinition = "TEXT")
    private String rulesText;

    private Boolean enabled = true;

    private Instant updatedAt = Instant.now();
}

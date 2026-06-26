package com.xiaowei.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String phone;
    private String passwordHash;
    private String wxOpenId;
    private String nickname;

    @Column(nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    private Integer vipLevel = 0;
    private String status = "active";

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    /** JSON：degree、wordCount、model、schoolId 等写作偏好 */
    @Column(columnDefinition = "TEXT")
    private String preferencesJson;

    @Column(length = 16)
    private String referralCode;

    private Long invitedByUserId;
}

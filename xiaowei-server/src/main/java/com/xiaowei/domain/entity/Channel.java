package com.xiaowei.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Entity
@Table(name = "channels")
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "d_code", nullable = false, unique = true)
    private String dCode;

    @Column(nullable = false)
    private String name;

    private BigDecimal commissionRate = BigDecimal.ZERO;
    private Boolean enabled = true;
    private Instant createdAt = Instant.now();
}

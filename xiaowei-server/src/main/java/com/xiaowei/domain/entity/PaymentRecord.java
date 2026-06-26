package com.xiaowei.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Entity
@Table(name = "payment_records")
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "pay_method", nullable = false)
    private String payMethod;

    @Column(name = "trade_no")
    private String tradeNo;

    @Column(nullable = false)
    private String status;

    private Instant createdAt = Instant.now();
}

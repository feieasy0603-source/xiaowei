package com.xiaowei.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderNo;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String productId;

    private String paperId;
    private Long channelId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String payStatus = "unpaid";

    private String payMethod;
    private Instant paidAt;
    private Instant createdAt = Instant.now();

    /** 下单时报价参数，支付前可重新计价 */
    private String quoteDegree;
    private Integer quoteWordCount;
    private String quoteModelType;
}

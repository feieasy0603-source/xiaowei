package com.xiaowei.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "product_prices")
public class ProductPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private String productId;

    private String degree;

    @Column(name = "word_count")
    private Integer wordCount;

    @Column(name = "model_type")
    private String modelType;

    @Column(nullable = false)
    private BigDecimal price;
}

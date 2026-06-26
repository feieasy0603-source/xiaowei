package com.xiaowei.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "products")
public class Product {

    @Id
    private String id;

    @Column(nullable = false)
    private String label;

    private String icon;
    private String badge;
    private String banner;

    @Column(nullable = false)
    private String processVariant;

    @Column(nullable = false)
    private String formVariant;

    @Column(nullable = false)
    private String taskType;

    @Column(nullable = false)
    private String flowType = "both";

    private String titleFieldLabel;
    private String titlePlaceholder;
    private String proLinkText;
    private String submitLabel;

    @Column(columnDefinition = "TEXT")
    private String agreementText;

    private Boolean showFaq = true;
    private Boolean centerTitle = false;
    private Integer sortOrder = 0;
    private Boolean enabled = true;

    @Column(columnDefinition = "TEXT")
    private String configJson;

    private Instant createdAt = Instant.now();
}

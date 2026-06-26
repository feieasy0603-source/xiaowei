package com.xiaowei.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String jobNo;

    private Long userId;

    @Column(nullable = false)
    private String productId;

    private String paperId;
    private Long orderId;

    @Column(nullable = false)
    private String taskType;

    @Column(nullable = false)
    private String status = "pending";

    private Integer progress = 0;

    @Column(columnDefinition = "LONGTEXT")
    private String payloadJson;

    @Column(columnDefinition = "LONGTEXT")
    private String resultJson;

    @Column(columnDefinition = "LONGTEXT")
    private String errorMsg;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private Instant finishedAt;
}

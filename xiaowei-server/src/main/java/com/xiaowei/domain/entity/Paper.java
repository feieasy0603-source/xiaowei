package com.xiaowei.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "papers")
public class Paper {

    @Id
    private String id;

    private Long userId;
    private String productId;
    private String title;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String draftJson;

    private Integer maxVisitedStep = 0;

    /** 草稿乐观锁版本，每次成功保存 +1 */
    private Long version = 1L;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}

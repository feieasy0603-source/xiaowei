package com.xiaowei.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "schools")
public class School {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "sort_order")
    private int sortOrder;

    private boolean enabled = true;

    private Instant createdAt = Instant.now();
}

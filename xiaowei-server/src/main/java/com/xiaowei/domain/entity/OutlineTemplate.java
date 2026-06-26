package com.xiaowei.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "outline_templates")
public class OutlineTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String category;
    private String degree;
    private Integer depth = 2;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String outlineJson;

    private Boolean enabled = true;
}

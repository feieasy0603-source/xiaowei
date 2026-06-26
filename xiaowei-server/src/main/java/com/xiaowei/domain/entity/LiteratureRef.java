package com.xiaowei.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "literature_refs")
public class LiteratureRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String authors;
    private String source;

    /** H2/MySQL 中 year 为保留字，列名使用 pub_year */
    @Column(name = "pub_year")
    private Integer year;

    private String lang;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String gbtCitation;

    private String keywords;
    private Boolean enabled = true;
}

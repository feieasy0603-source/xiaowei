package com.xiaowei.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "support_settings")
public class SupportSettings {

    @Id
    private Long id = 1L;

    private Boolean enabled = true;

    private String title = "在线客服";

    private String workHours = "工作日 9:00–18:00";

    private String phone;

    private String email;

    private String wechatId;

    private String qq;

    private String externalUrl;

    @Column(columnDefinition = "TEXT")
    private String note;

    private Instant updatedAt = Instant.now();
}

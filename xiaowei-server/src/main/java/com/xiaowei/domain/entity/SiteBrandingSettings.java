package com.xiaowei.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "site_branding_settings")
public class SiteBrandingSettings {

    @Id
    private Long id = 1L;

    private String siteTitle = "小微智能写作";

    private String slogan = "一站式论文辅助平台";

    private String documentTitle = "小微智能 AI 论文写作";

    /** 无 logo 图片时顶栏方块内文字 */
    private String logoText = "AI";

    /** 存储 key 或 http(s) 外链 */
    @Column(length = 512)
    private String logoUrl;

    @Column(length = 512)
    private String faviconUrl;

    private Instant updatedAt = Instant.now();
}

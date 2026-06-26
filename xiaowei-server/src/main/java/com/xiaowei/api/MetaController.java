package com.xiaowei.api;

import com.xiaowei.common.ApiResponse;
import com.xiaowei.service.SiteBrandingService;
import com.xiaowei.service.SupportSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/meta")
@RequiredArgsConstructor
public class MetaController {

    private final SupportSettingsService supportSettingsService;
    private final SiteBrandingService siteBrandingService;

    @GetMapping("/branding")
    public ApiResponse<Map<String, Object>> branding() {
        return ApiResponse.ok(siteBrandingService.getPublic());
    }

    @GetMapping("/support")
    public ApiResponse<Map<String, Object>> support() {
        return ApiResponse.ok(supportSettingsService.getPublic());
    }

    @GetMapping("/form-options")
    public ApiResponse<Map<String, Object>> formOptions() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("categories", CATEGORIES);
        m.put("degrees", DEGREES);
        m.put("paperTypes", PAPER_TYPES);
        return ApiResponse.ok(m);
    }

    private static final List<String> DEGREES = List.of("专科", "本科", "硕士", "博士");

    private static final List<String> PAPER_TYPES = List.of(
            "毕业论文", "课程论文", "开题报告", "文献综述", "实训报告");

    private static final List<String> CATEGORIES = List.of(
            "教育经管", "计算机科学", "文学艺术", "法学政治",
            "畜牧业电器电力、机械",
            "电子信息科学技术",
            "建筑工程、轻工业化工",
            "传媒、科普能源环保",
            "矿业、旅游",
            "党建法律、水利、食品",
            "医药卫生交通运输");
}

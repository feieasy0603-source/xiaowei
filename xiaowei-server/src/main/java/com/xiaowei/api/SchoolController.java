package com.xiaowei.api;

import com.xiaowei.common.ApiResponse;
import com.xiaowei.domain.entity.School;
import com.xiaowei.domain.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/schools")
@RequiredArgsConstructor
public class SchoolController {

    private final SchoolRepository schoolRepository;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        List<Map<String, Object>> list = schoolRepository.findByEnabledTrueOrderBySortOrderAscNameAsc()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ApiResponse.ok(list);
    }

    private Map<String, Object> toDto(School s) {
        return Map.of("id", s.getId(), "name", s.getName());
    }
}

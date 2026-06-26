package com.xiaowei.service;

import com.xiaowei.common.BusinessException;
import com.xiaowei.domain.entity.School;
import com.xiaowei.domain.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SchoolAdminService {

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_-]{2,32}$");

    private final SchoolRepository schoolRepository;

    public List<School> listAll() {
        return schoolRepository.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .toList();
    }

    @Transactional
    public School create(String id, String name, int sortOrder) {
        String sid = normalizeId(id, name);
        if (schoolRepository.existsById(sid)) {
            throw new BusinessException("学校 ID 已存在: " + sid);
        }
        School s = new School();
        s.setId(sid);
        s.setName(requireName(name));
        s.setSortOrder(sortOrder);
        s.setEnabled(true);
        s.setCreatedAt(Instant.now());
        return schoolRepository.save(s);
    }

    @Transactional
    public School update(String id, Map<String, Object> body) {
        School s = schoolRepository.findById(id)
                .orElseThrow(() -> new BusinessException("学校不存在"));
        if (body.get("name") != null) {
            s.setName(requireName(String.valueOf(body.get("name"))));
        }
        if (body.get("sortOrder") != null) {
            s.setSortOrder(((Number) body.get("sortOrder")).intValue());
        }
        if (body.get("enabled") != null) {
            s.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        }
        return schoolRepository.save(s);
    }

    @Transactional
    public void delete(String id) {
        if ("other".equals(id)) {
            throw new BusinessException("不能删除「其他高校」占位项");
        }
        School s = schoolRepository.findById(id)
                .orElseThrow(() -> new BusinessException("学校不存在"));
        s.setEnabled(false);
        schoolRepository.save(s);
    }

    public Map<String, Object> toDto(School s) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getName());
        m.put("sortOrder", s.getSortOrder());
        m.put("enabled", s.isEnabled());
        m.put("createdAt", s.getCreatedAt());
        return m;
    }

    private String requireName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.length() < 2 || n.length() > 128) {
            throw new BusinessException("学校名称须 2-128 字");
        }
        return n;
    }

    private String normalizeId(String id, String name) {
        if (id != null && !id.isBlank()) {
            String s = id.trim().toLowerCase();
            if (!ID_PATTERN.matcher(s).matches()) {
                throw new BusinessException("ID 仅允许小写字母、数字、下划线，2-32 位");
            }
            return s;
        }
        String slug = name == null ? "" : name.trim()
                .replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "")
                .toLowerCase();
        if (slug.isBlank()) {
            slug = "school_" + System.currentTimeMillis();
        }
        if (slug.length() > 28) {
            slug = slug.substring(0, 28);
        }
        String base = slug;
        int i = 1;
        while (schoolRepository.existsById(slug)) {
            slug = base + "_" + (i++);
        }
        return slug;
    }
}

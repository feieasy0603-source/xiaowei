package com.xiaowei.service;

import com.xiaowei.domain.entity.SupportSettings;
import com.xiaowei.domain.repository.SupportSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SupportSettingsService {

    private static final long SETTINGS_ID = 1L;

    private final SupportSettingsRepository repository;

    @Value("${xiaowei.support.phone:}")
    private String defaultPhone;

    @Value("${xiaowei.support.wechat-id:}")
    private String defaultWechatId;

    public Map<String, Object> getPublic() {
        SupportSettings row = ensureRow();
        return toPublicMap(row);
    }

    public Map<String, Object> getForAdmin() {
        SupportSettings row = ensureRow();
        Map<String, Object> m = toPublicMap(row);
        m.put("updatedAt", row.getUpdatedAt() != null ? row.getUpdatedAt().toString() : null);
        return m;
    }

    @Transactional
    public Map<String, Object> updateFromAdmin(Map<String, Object> body) {
        SupportSettings row = ensureRow();
        if (body.get("enabled") != null) {
            row.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        }
        setIfPresent(body, "title", row::setTitle);
        setIfPresent(body, "workHours", row::setWorkHours);
        setNullableString(body, "phone", row::setPhone);
        setNullableString(body, "email", row::setEmail);
        setNullableString(body, "wechatId", row::setWechatId);
        setNullableString(body, "qq", row::setQq);
        setNullableString(body, "externalUrl", row::setExternalUrl);
        setNullableString(body, "note", row::setNote);
        row.setUpdatedAt(Instant.now());
        repository.save(row);
        return getForAdmin();
    }

    private SupportSettings ensureRow() {
        return repository.findById(SETTINGS_ID).orElseGet(() -> {
            SupportSettings s = new SupportSettings();
            s.setId(SETTINGS_ID);
            s.setEnabled(true);
            s.setTitle("在线客服");
            s.setWorkHours("工作日 9:00–18:00");
            if (defaultPhone != null && !defaultPhone.isBlank()) {
                s.setPhone(defaultPhone.trim());
            }
            if (defaultWechatId != null && !defaultWechatId.isBlank()) {
                s.setWechatId(defaultWechatId.trim());
            }
            s.setNote("如需帮助请通过下方方式联系我们。");
            s.setUpdatedAt(Instant.now());
            return repository.save(s);
        });
    }

    private Map<String, Object> toPublicMap(SupportSettings row) {
        Map<String, Object> m = new HashMap<>();
        m.put("enabled", row.getEnabled() == null || row.getEnabled());
        m.put("title", blankOr(row.getTitle(), "在线客服"));
        m.put("workHours", blankOr(row.getWorkHours(), "工作日 9:00–18:00"));
        m.put("phone", blankToNull(row.getPhone()));
        m.put("email", blankToNull(row.getEmail()));
        m.put("wechatId", blankToNull(row.getWechatId()));
        m.put("qq", blankToNull(row.getQq()));
        m.put("externalUrl", blankToNull(row.getExternalUrl()));
        m.put("note", blankToNull(row.getNote()));
        boolean hasChannel = m.get("phone") != null || m.get("email") != null
                || m.get("wechatId") != null || m.get("qq") != null || m.get("externalUrl") != null;
        m.put("hasChannel", hasChannel);
        return m;
    }

    private static void setIfPresent(Map<String, Object> body, String key, java.util.function.Consumer<String> setter) {
        if (body.get(key) != null) {
            String v = String.valueOf(body.get(key)).trim();
            if (!v.isBlank()) {
                setter.accept(v);
            }
        }
    }

    private static void setNullableString(Map<String, Object> body, String key, java.util.function.Consumer<String> setter) {
        if (!body.containsKey(key)) {
            return;
        }
        Object raw = body.get(key);
        if (raw == null) {
            setter.accept(null);
            return;
        }
        String v = String.valueOf(raw).trim();
        setter.accept(v.isBlank() ? null : v);
    }

    private static String blankOr(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s.trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

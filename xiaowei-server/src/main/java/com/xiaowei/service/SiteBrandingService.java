package com.xiaowei.service;

import com.xiaowei.domain.entity.SiteBrandingSettings;
import com.xiaowei.domain.repository.SiteBrandingSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SiteBrandingService {

    private static final long SETTINGS_ID = 1L;
    private static final Set<String> LOGO_EXT = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg");
    private static final Set<String> FAVICON_EXT = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico");

    private final SiteBrandingSettingsRepository repository;
    private final FileStorageService fileStorageService;

    public Map<String, Object> getPublic() {
        return toPublicMap(ensureRow());
    }

    public Map<String, Object> getForAdmin() {
        SiteBrandingSettings row = ensureRow();
        Map<String, Object> m = toPublicMap(row);
        m.put("updatedAt", row.getUpdatedAt() != null ? row.getUpdatedAt().toString() : null);
        return m;
    }

    @Transactional
    public Map<String, Object> updateFromAdmin(Map<String, Object> body) {
        SiteBrandingSettings row = ensureRow();
        setIfPresent(body, "siteTitle", row::setSiteTitle);
        setIfPresent(body, "slogan", row::setSlogan);
        setIfPresent(body, "documentTitle", row::setDocumentTitle);
        setIfPresent(body, "logoText", row::setLogoText);
        setNullableString(body, "logoUrl", row::setLogoUrl);
        setNullableString(body, "faviconUrl", row::setFaviconUrl);
        row.setUpdatedAt(Instant.now());
        repository.save(row);
        return getForAdmin();
    }

    @Transactional
    public Map<String, Object> uploadLogo(MultipartFile file) {
        String key = fileStorageService.saveBrandingAsset("logo", file, LOGO_EXT);
        SiteBrandingSettings row = ensureRow();
        row.setLogoUrl(key);
        row.setUpdatedAt(Instant.now());
        repository.save(row);
        return buildUploadResult(row, "logoUrl", key);
    }

    @Transactional
    public Map<String, Object> uploadFavicon(MultipartFile file) {
        String key = fileStorageService.saveBrandingAsset("favicon", file, FAVICON_EXT);
        SiteBrandingSettings row = ensureRow();
        row.setFaviconUrl(key);
        row.setUpdatedAt(Instant.now());
        repository.save(row);
        return buildUploadResult(row, "faviconUrl", key);
    }

    private Map<String, Object> buildUploadResult(SiteBrandingSettings row, String field, String key) {
        Map<String, Object> m = getForAdmin();
        m.put("uploadedField", field);
        m.put("storageKey", key);
        m.put("publicUrl", publicAssetUrl(key));
        return m;
    }

    private SiteBrandingSettings ensureRow() {
        return repository.findById(SETTINGS_ID).orElseGet(() -> {
            SiteBrandingSettings s = new SiteBrandingSettings();
            s.setId(SETTINGS_ID);
            s.setUpdatedAt(Instant.now());
            return repository.save(s);
        });
    }

    private Map<String, Object> toPublicMap(SiteBrandingSettings row) {
        Map<String, Object> m = new HashMap<>();
        m.put("siteTitle", blankOr(row.getSiteTitle(), "小微智能写作"));
        m.put("slogan", blankOr(row.getSlogan(), "一站式论文辅助平台"));
        m.put("documentTitle", blankOr(row.getDocumentTitle(), "小微智能 AI 论文写作"));
        m.put("logoText", blankOr(row.getLogoText(), "AI"));
        m.put("logoUrl", resolvePublicUrl(row.getLogoUrl()));
        m.put("faviconUrl", resolvePublicUrl(row.getFaviconUrl()));
        m.put("logoStorageKey", blankToNull(row.getLogoUrl()));
        m.put("faviconStorageKey", blankToNull(row.getFaviconUrl()));
        return m;
    }

    public String resolvePublicUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        if (v.startsWith("http://") || v.startsWith("https://") || v.startsWith("/")) {
            return v;
        }
        return "/files/download/" + v;
    }

    public String publicAssetUrl(String storageKey) {
        return resolvePublicUrl(storageKey);
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
        if (v.isBlank()) {
            setter.accept(null);
            return;
        }
        if (v.startsWith("http://") || v.startsWith("https://") || v.startsWith("/")) {
            setter.accept(v);
            return;
        }
        setter.accept(v);
    }

    private static String blankOr(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s.trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

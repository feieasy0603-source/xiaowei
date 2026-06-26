package com.xiaowei.service;

import com.xiaowei.common.BusinessException;
import com.xiaowei.integration.ai.ProposalTextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private final ProposalTextExtractor proposalTextExtractor;
    private final Path root;
    private final long maxUploadBytes;

    public FileStorageService(
            ProposalTextExtractor proposalTextExtractor,
            @Value("${xiaowei.storage.local-dir}") String dir,
            @Value("${xiaowei.storage.max-upload-bytes:20971520}") long maxUploadBytes) throws IOException {
        this.proposalTextExtractor = proposalTextExtractor;
        this.root = resolveStorageRoot(dir);
        this.maxUploadBytes = maxUploadBytes;
        Files.createDirectories(root);
        log.info("文件存储根目录: {}", root);
    }

    public Path rootPath() {
        return root;
    }

    /**
     * 相对路径默认相对于 user.dir；IDEA 工作区在父目录 xiaowei 时，文件实际在 xiaowei-server/data/uploads。
     */
    static Path resolveStorageRoot(String dir) {
        String trimmed = dir == null || dir.isBlank() ? "./data/uploads" : dir.trim();
        Path configured = Path.of(trimmed);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path moduleBase = "xiaowei-server".equals(String.valueOf(cwd.getFileName()))
                ? cwd
                : cwd.resolve("xiaowei-server");
        Path underModule = moduleBase.resolve(configured).normalize();
        if (Files.exists(underModule) || Files.exists(moduleBase)) {
            return underModule.toAbsolutePath().normalize();
        }
        return cwd.resolve(configured).toAbsolutePath().normalize();
    }

    public Map<String, Object> saveBytes(String fileName, byte[] data) {
        String key = "delivery/" + UUID.randomUUID() + "_" + sanitizeFileName(fileName);
        try {
            Path target = root.resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, data);
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("storageKey", key);
            m.put("fileName", fileName);
            m.put("size", (long) data.length);
            return m;
        } catch (IOException e) {
            throw new BusinessException("文件保存失败");
        }
    }

    public Map<String, Object> saveText(String fileName, String content) {
        return saveBytes(fileName, content.getBytes(StandardCharsets.UTF_8));
    }

    public Path resolve(String storageKey) {
        String key = normalizeStorageKey(storageKey);
        for (Path base : candidateRoots()) {
            Path baseNorm = base.toAbsolutePath().normalize();
            Path target = baseNorm.resolve(key).normalize();
            if (!isUnderRoot(target, baseNorm)) {
                continue;
            }
            if (Files.isRegularFile(target)) {
                return target;
            }
        }
        throw new BusinessException("文件不存在");
    }

    private List<Path> candidateRoots() {
        Set<Path> set = new LinkedHashSet<>();
        set.add(root.toAbsolutePath().normalize());
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path moduleBase = "xiaowei-server".equals(String.valueOf(cwd.getFileName()))
                ? cwd
                : cwd.resolve("xiaowei-server");
        set.add(moduleBase.resolve("data/uploads").toAbsolutePath().normalize());
        set.add(cwd.resolve("data/uploads").toAbsolutePath().normalize());
        return new ArrayList<>(set);
    }

    private static String normalizeStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new BusinessException("文件不存在");
        }
        String key = storageKey.replace('\\', '/').trim();
        int deliveryIdx = key.indexOf("delivery/");
        if (deliveryIdx > 0) {
            key = key.substring(deliveryIdx);
        }
        while (key.startsWith("/")) {
            key = key.substring(1);
        }
        if (key.contains("..")) {
            throw new BusinessException("非法路径");
        }
        return key;
    }

    private static boolean isUnderRoot(Path file, Path base) {
        Path absFile = file.toAbsolutePath().normalize();
        Path absBase = base.toAbsolutePath().normalize();
        if (absFile.startsWith(absBase)) {
            return true;
        }
        try {
            return absFile.toRealPath().startsWith(absBase.toRealPath());
        } catch (IOException e) {
            return false;
        }
    }

    private static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "file.txt";
        }
        String name = fileName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.replace("..", "").trim();
    }

    public Map<String, Object> upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("文件为空");
        }
        validateUploadSize(file);
        String key = UUID.randomUUID() + "_" + sanitizeFileName(file.getOriginalFilename());
        return saveToKey(key, file);
    }

    /** 站点品牌资源，固定路径便于覆盖更新 */
    public String saveBrandingAsset(String kind, MultipartFile file, Set<String> allowedExt) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件为空");
        }
        validateUploadSize(file);
        String original = sanitizeFileName(file.getOriginalFilename());
        String ext = extensionOf(original);
        if (!allowedExt.contains(ext)) {
            throw new BusinessException("不支持的文件类型: " + ext);
        }
        String key = "branding/site-" + kind + ext;
        saveToKey(key, file);
        return key;
    }

    private Map<String, Object> saveToKey(String key, MultipartFile file) {
        try {
            Path target = root.resolve(key);
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("fileId", key);
            m.put("fileName", file.getOriginalFilename());
            m.put("size", file.getSize());
            m.put("downloadUrl", "/files/download/" + key);
            return m;
        } catch (IOException e) {
            throw new BusinessException("上传失败");
        }
    }

    private void validateUploadSize(MultipartFile file) {
        if (maxUploadBytes > 0 && file.getSize() > maxUploadBytes) {
            throw new BusinessException("文件过大，最大支持 " + (maxUploadBytes / 1024 / 1024) + "MB");
        }
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return fileName.substring(dot).toLowerCase();
    }

    /** 读取用户上传文件的文本摘要，供改稿/降重等任务使用 */
    public String readTextPreview(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return "";
        }
        try {
            Path path = resolve(storageKey);
            String name = path.getFileName().toString().toLowerCase();
            if (name.endsWith(".txt")) {
                String text = Files.readString(path, StandardCharsets.UTF_8);
                return truncate(text, 12000);
            }
            if (name.endsWith(".docx") || name.endsWith(".pdf")) {
                String text = proposalTextExtractor.extractFromBytes(name, Files.readAllBytes(path));
                return truncate(text, 12000);
            }
            if (name.endsWith(".doc")) {
                return "【已上传】" + path.getFileName() + "（.doc 请另存为 docx 后上传）";
            }
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length > 0 && bytes.length < 500_000) {
                return truncate(new String(bytes, StandardCharsets.UTF_8), 12000);
            }
            return "【已上传】" + path.getFileName();
        } catch (Exception e) {
            throw new BusinessException("读取文件失败: " + e.getMessage());
        }
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        String t = text.trim();
        if (t.length() <= max) return t;
        return t.substring(0, max);
    }

    private String extractDocxText(byte[] data) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(data))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    sb.append(xml.replaceAll("</w:p>", "\n").replaceAll("<[^>]+>", " ").trim());
                }
            }
        }
        return sb.toString();
    }
}

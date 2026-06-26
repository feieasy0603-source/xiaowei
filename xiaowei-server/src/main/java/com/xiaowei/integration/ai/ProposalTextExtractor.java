package com.xiaowei.integration.ai;

import com.xiaowei.common.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 从开题报告文件中提取纯文本（支持 txt、docx、pdf） */
@Component
public class ProposalTextExtractor {

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件为空");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        try {
            return extractFromBytes(name, file.getBytes());
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException("文件读取失败");
        }
    }

    /** 从已落盘文件按扩展名提取文本（改稿/降重/AIGC 等任务） */
    public String extractFromBytes(String fileName, byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            throw new BusinessException("文件为空");
        }
        String name = fileName == null ? "" : fileName.toLowerCase();
        if (name.endsWith(".txt")) {
            return new String(data, StandardCharsets.UTF_8).trim();
        }
        if (name.endsWith(".docx")) {
            return extractDocx(data);
        }
        if (name.endsWith(".pdf")) {
            return extractPdf(data);
        }
        if (name.endsWith(".doc")) {
            throw new BusinessException("暂不支持 .doc 格式，请另存为 .docx 或 .txt 后上传");
        }
        throw new BusinessException("仅支持 docx、txt、pdf 格式");
    }

    private String extractPdf(byte[] data) throws IOException {
        try (PDDocument doc = Loader.loadPDF(data)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc).trim();
            if (text.length() < 20) {
                throw new BusinessException("未能从 PDF 中提取到有效文本，扫描件请改用 docx 或粘贴到研究思路");
            }
            return text.length() > 8000 ? text.substring(0, 8000) : text;
        }
    }

    private String extractDocx(byte[] data) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    sb.append(stripXml(xml));
                }
            }
        }
        String text = sb.toString().trim();
        if (text.length() < 20) {
            throw new BusinessException("未能从 docx 中提取到有效文本");
        }
        return text;
    }

    private String stripXml(String xml) {
        return xml
                .replaceAll("</w:p>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n{2,}", "\n")
                .trim();
    }
}

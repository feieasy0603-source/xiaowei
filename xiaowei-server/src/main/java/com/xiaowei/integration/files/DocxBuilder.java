package com.xiaowei.integration.files;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class DocxBuilder {

    public byte[] buildFromText(String title, String body) throws IOException {
        return buildFromText(title, body, null);
    }

    public byte[] buildFromText(String title, String body, String schoolFormatLabel) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            addHeading(doc, title);
            if (schoolFormatLabel != null && !schoolFormatLabel.isBlank()) {
                addCenterSubtitle(doc, schoolFormatLabel);
            }
            for (String para : body.split("\n\n")) {
                addBody(doc, para.replace('\n', ' ').trim());
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    public byte[] buildFromPreview(String title, Map<String, Object> preview) throws IOException {
        return buildFromPreview(title, preview, null);
    }

    @SuppressWarnings("unchecked")
    public byte[] buildFromPreview(String title, Map<String, Object> preview, String schoolFormatLabel) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            addHeading(doc, title);
            if (schoolFormatLabel != null && !schoolFormatLabel.isBlank()) {
                addCenterSubtitle(doc, schoolFormatLabel);
            }
            Object abstractZh = preview.get("abstractZh");
            if (abstractZh != null && !String.valueOf(abstractZh).isBlank()) {
                addSubHeading(doc, "摘要");
                addBody(doc, String.valueOf(abstractZh));
            }
            Object abstractEn = preview.get("abstractEn");
            if (abstractEn != null && !String.valueOf(abstractEn).isBlank()) {
                addSubHeading(doc, "ABSTRACT");
                addBody(doc, String.valueOf(abstractEn));
            }
            if (preview.get("sections") instanceof List<?> sections) {
                for (Object secObj : sections) {
                    if (!(secObj instanceof Map<?, ?> sec)) continue;
                    Object st = sec.get("title");
                    Object sc = sec.get("content");
                    if (st != null) addSubHeading(doc, String.valueOf(st));
                    if (sc != null) addBody(doc, String.valueOf(sc));
                }
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    private void addHeading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setFontSize(16);
        run.setText(text);
    }

    private void addCenterSubtitle(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun();
        run.setFontSize(11);
        run.setText(text);
    }

    private void addSubHeading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setFontSize(14);
        run.setText(text);
    }

    private void addBody(XWPFDocument doc, String text) {
        if (text == null || text.isBlank()) return;
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.BOTH);
        XWPFRun run = p.createRun();
        run.setFontSize(12);
        run.setText(text);
    }
}

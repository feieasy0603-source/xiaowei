package com.xiaowei.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaowei.domain.entity.OutlineTemplate;
import com.xiaowei.domain.repository.LiteratureRefRepository;
import com.xiaowei.domain.repository.OutlineTemplateRepository;
import com.xiaowei.integration.AiGateway;
import com.xiaowei.integration.literature.OpenAlexClient;
import com.xiaowei.integration.ai.ProposalTextExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiService {

    private final AiGateway aiGateway;
    private final OutlineTemplateRepository outlineTemplateRepository;
    private final LiteratureRefRepository literatureRefRepository;
    private final OpenAlexClient openAlexClient;
    private final ProposalTextExtractor proposalTextExtractor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${xiaowei.literature.openalex-enabled:true}")
    private boolean openAlexEnabled;

    @Value("${xiaowei.literature.openalex-min-local:8}")
    private int openAlexMinLocal;

    public Map<String, Object> polishTitle(String title) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", aiGateway.polishTitle(title));
        return m;
    }

    public Map<String, Object> recommendTitles(String keyword, String productId) {
        Map<String, Object> m = new HashMap<>();
        m.put("titles", aiGateway.recommendTitles(keyword, productId));
        return m;
    }

    public List<Map<String, Object>> searchOutline(String title, String degree) {
        List<OutlineTemplate> templates = outlineTemplateRepository.searchByTitle(title);
        if (templates.isEmpty()) {
            return aiGateway.searchOutline(title, degree);
        }
        return templates.stream().map(t -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", t.getId());
            m.put("title", t.getTitle());
            m.put("degree", t.getDegree());
            m.put("depth", t.getDepth());
            m.put("outlineJson", t.getOutlineJson());
            return m;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> generateOutline(String title, int depth) {
        Map<String, Object> m = new HashMap<>();
        m.put("outlineText", aiGateway.generateOutlineText(title, depth));
        return m;
    }

    public List<Map<String, Object>> searchLiterature(String keyword) {
        String kw = keyword != null ? keyword.trim() : "";
        if (kw.length() < 2) {
            return List.of();
        }
        var page = org.springframework.data.domain.PageRequest.of(0, 80);
        List<Map<String, Object>> local = literatureRefRepository.search(kw, page).stream().map(l -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", String.valueOf(l.getId()));
            m.put("title", l.getTitle());
            m.put("authors", l.getAuthors());
            m.put("source", l.getSource());
            m.put("year", l.getYear());
            m.put("lang", l.getLang());
            m.put("gbtCitation", l.getGbtCitation());
            m.put("external", false);
            return m;
        }).collect(Collectors.toList());

        if (!openAlexEnabled || local.size() >= openAlexMinLocal) {
            return local;
        }
        List<Map<String, Object>> merged = new ArrayList<>(local);
        java.util.Set<String> seen = local.stream()
                .map(x -> normalizeTitle(String.valueOf(x.get("title"))))
                .collect(Collectors.toSet());
        for (Map<String, Object> ext : openAlexClient.search(kw, 25)) {
            String key = normalizeTitle(String.valueOf(ext.get("title")));
            if (key.isBlank() || seen.contains(key)) {
                continue;
            }
            seen.add(key);
            merged.add(ext);
            if (merged.size() >= 80) {
                break;
            }
        }
        return merged;
    }

    private static String normalizeTitle(String title) {
        return title == null ? "" : title.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    public Map<String, Object> parseProposal(MultipartFile file) {
        String text = proposalTextExtractor.extract(file);
        String summary = aiGateway.parseProposal(text);
        Map<String, Object> m = new HashMap<>();
        m.put("summary", summary);
        m.put("textLength", text.length());
        return m;
    }
}

package com.xiaowei.integration.literature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAlex 免费文献检索（本地库不足时补充）。
 */
@Slf4j
@Component
public class OpenAlexClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${xiaowei.literature.openalex-enabled:true}")
    private boolean enabled;

    public List<Map<String, Object>> search(String keyword, int limit) {
        if (!enabled || keyword == null || keyword.isBlank()) {
            return List.of();
        }
        try {
            String q = URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8);
            int perPage = Math.min(Math.max(limit, 1), 25);
            URI uri = URI.create(
                    "https://api.openalex.org/works?search=" + q + "&per_page=" + perPage);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("User-Agent", "xiaowei-literature/1.0 (mailto:support@example.com)")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("OpenAlex 检索失败 {}: {}", response.statusCode(), response.body());
                return List.of();
            }
            JsonNode results = objectMapper.readTree(response.body()).path("results");
            List<Map<String, Object>> list = new ArrayList<>();
            if (!results.isArray()) {
                return list;
            }
            for (JsonNode w : results) {
                Map<String, Object> m = mapWork(w);
                if (m != null) {
                    list.add(m);
                }
            }
            return list;
        } catch (Exception e) {
            log.warn("OpenAlex 检索异常: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> mapWork(JsonNode w) {
        String title = w.path("title").asText("").trim();
        if (title.isBlank()) {
            return null;
        }
        String authors = extractAuthors(w.path("authorships"));
        Integer year = w.path("publication_year").isInt() ? w.path("publication_year").asInt() : null;
        String source = w.path("primary_location").path("source").path("display_name").asText("");
        String lang = detectLang(title);
        String id = w.path("id").asText("");
        String shortId = id.contains("/") ? id.substring(id.lastIndexOf('/') + 1) : id;

        Map<String, Object> m = new HashMap<>();
        m.put("id", "oa:" + shortId);
        m.put("title", title);
        m.put("authors", authors.isBlank() ? "佚名" : authors);
        m.put("source", source.isBlank() ? "OpenAlex" : source);
        m.put("year", year != null ? year : 0);
        m.put("lang", lang);
        m.put("gbtCitation", buildGbtCitation(authors, title, source, year, lang));
        m.put("external", true);
        return m;
    }

    private static String extractAuthors(JsonNode authorships) {
        if (!authorships.isArray()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (JsonNode a : authorships) {
            String name = a.path("author").path("display_name").asText("").trim();
            if (!name.isBlank()) {
                names.add(name);
            }
            if (names.size() >= 3) {
                break;
            }
        }
        return String.join(", ", names);
    }

    private static String detectLang(String title) {
        for (int i = 0; i < title.length(); i++) {
            if (Character.UnicodeScript.of(title.charAt(i)) == Character.UnicodeScript.HAN) {
                return "zh";
            }
        }
        return "en";
    }

    private static String buildGbtCitation(
            String authors,
            String title,
            String source,
            Integer year,
            String lang
    ) {
        String authorPart = authors.isBlank() ? "佚名" : authors;
        String yearPart = year != null && year > 0 ? String.valueOf(year) : "n.d.";
        if ("zh".equals(lang)) {
            return authorPart + ". " + title + "[J]. "
                    + (source.isBlank() ? "OpenAlex" : source) + ", " + yearPart + ".";
        }
        return authorPart + ". " + title + "[J]. "
                + (source.isBlank() ? "OpenAlex" : source) + ", " + yearPart + ".";
    }
}

package com.xiaowei.service;

import com.xiaowei.common.BusinessException;
import com.xiaowei.domain.entity.ProductPrice;
import com.xiaowei.domain.repository.ProductPriceRepository;
import com.xiaowei.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductPriceService {

    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("29.90");

    private final ProductPriceRepository productPriceRepository;
    private final ProductRepository productRepository;

    public List<Map<String, Object>> listByProduct(String productId) {
        return productPriceRepository.findByProductIdOrderByWordCountAsc(productId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> listAll(String productId) {
        if (productId != null && !productId.isBlank()) {
            return listByProduct(productId);
        }
        return productPriceRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> save(Map<String, Object> body) {
        ProductPrice row = new ProductPrice();
        if (body.get("id") != null) {
            Long id = Long.valueOf(String.valueOf(body.get("id")));
            row = productPriceRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("价格规则不存在"));
        }
        String productId = String.valueOf(body.get("productId"));
        productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException("产品不存在"));
        row.setProductId(productId);
        if (body.containsKey("degree")) {
            Object d = body.get("degree");
            row.setDegree(d == null || String.valueOf(d).isBlank() ? null : String.valueOf(d));
        }
        if (body.containsKey("wordCount")) {
            Object w = body.get("wordCount");
            row.setWordCount(w == null || String.valueOf(w).isBlank() ? null : Integer.valueOf(String.valueOf(w)));
        }
        if (body.containsKey("modelType")) {
            Object m = body.get("modelType");
            row.setModelType(m == null || String.valueOf(m).isBlank() ? null : String.valueOf(m));
        }
        row.setPrice(new BigDecimal(String.valueOf(body.get("price"))));
        return toDto(productPriceRepository.save(row));
    }

    @Transactional
    public void delete(Long id) {
        productPriceRepository.deleteById(id);
    }

    public BigDecimal quote(String productId, String degree, Integer wordCount, String modelType) {
        String deg = normalize(degree);
        String model = normalize(modelType);
        int words = wordCount != null && wordCount > 0 ? wordCount : 8000;

        List<ProductPrice> tier = productPriceRepository.findBestQuoteTier(
                productId, deg, model, words, PageRequest.of(0, 1));
        if (!tier.isEmpty()) {
            return tier.get(0).getPrice();
        }

        List<ProductPrice> rows = productPriceRepository.findByProductIdOrderByWordCountAsc(productId);
        if (rows.isEmpty()) {
            return DEFAULT_PRICE;
        }
        return rows.stream()
                .filter(r -> matches(r.getDegree(), deg))
                .filter(r -> matches(r.getModelType(), model))
                .filter(r -> r.getWordCount() == null || r.getWordCount() <= words)
                .max(Comparator.comparingInt(r -> r.getWordCount() != null ? r.getWordCount() : 0))
                .map(ProductPrice::getPrice)
                .orElse(DEFAULT_PRICE);
    }

    public Map<String, Object> quoteDto(String productId, String degree, Integer wordCount, String modelType) {
        BigDecimal price = quote(productId, degree, wordCount, modelType);
        Map<String, Object> m = new HashMap<>();
        m.put("productId", productId);
        m.put("degree", degree);
        m.put("wordCount", wordCount);
        m.put("modelType", modelType);
        m.put("price", price);
        return m;
    }

    private boolean matches(String rule, String request) {
        if (rule == null || rule.isBlank()) return true;
        if (request == null || request.isBlank()) return true;
        return rule.equals(request);
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim();
    }

    private Map<String, Object> toDto(ProductPrice p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("productId", p.getProductId());
        m.put("degree", p.getDegree());
        m.put("wordCount", p.getWordCount());
        m.put("modelType", p.getModelType());
        m.put("price", p.getPrice());
        return m;
    }
}

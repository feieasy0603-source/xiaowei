package com.xiaowei.service;

import com.xiaowei.common.BusinessException;
import com.xiaowei.domain.entity.Product;
import com.xiaowei.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public List<Map<String, Object>> listProducts() {
        return productRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getProduct(String id) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException("产品不存在"));
        return toDto(p);
    }

    private Map<String, Object> toDto(Product p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("label", p.getLabel());
        m.put("icon", p.getIcon());
        m.put("badge", p.getBadge());
        m.put("banner", p.getBanner());
        m.put("processVariant", p.getProcessVariant());
        m.put("formVariant", p.getFormVariant());
        m.put("taskType", p.getTaskType());
        m.put("flowType", p.getFlowType());
        m.put("titleFieldLabel", p.getTitleFieldLabel());
        m.put("titlePlaceholder", p.getTitlePlaceholder());
        m.put("proLinkText", p.getProLinkText());
        m.put("submitLabel", p.getSubmitLabel());
        m.put("agreementText", p.getAgreementText());
        m.put("showFaq", p.getShowFaq());
        m.put("centerTitle", p.getCenterTitle());
        return m;
    }
}

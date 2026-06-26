package com.xiaowei.service;

import com.xiaowei.domain.entity.Channel;
import com.xiaowei.domain.repository.ChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelRepository channelRepository;

    public Map<String, Object> resolve(String dCode) {
        return channelRepository.findActiveByDCode(dCode)
                .map(this::toDto)
                .orElseGet(() -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("dCode", dCode);
                    m.put("valid", false);
                    return m;
                });
    }

    private Map<String, Object> toDto(Channel c) {
        Map<String, Object> m = new HashMap<>();
        m.put("dCode", c.getDCode());
        m.put("name", c.getName());
        m.put("commissionRate", c.getCommissionRate());
        m.put("valid", true);
        return m;
    }
}

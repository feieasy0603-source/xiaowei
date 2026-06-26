package com.xiaowei.api;

import com.xiaowei.common.ApiResponse;
import com.xiaowei.service.ChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @GetMapping("/resolve")
    public ApiResponse<Map<String, Object>> resolve(@RequestParam String dCode) {
        return ApiResponse.ok(channelService.resolve(dCode));
    }
}

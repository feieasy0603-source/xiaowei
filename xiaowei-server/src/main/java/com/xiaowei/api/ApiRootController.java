package com.xiaowei.api;

import com.xiaowei.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ApiRootController {

  @GetMapping("/")
  public ApiResponse<Map<String, String>> root() {
    Map<String, String> links = new LinkedHashMap<>();
    links.put("swagger", "/swagger-ui/index.html");
    links.put("products", "/products");
    links.put("demoLogin", "POST /auth/demo");
    links.put("frontend", "http://127.0.0.1:5173/#/iw/intelligentWriting/0");
    links.put("admin", "http://127.0.0.1:5174/admin/");
    return ApiResponse.ok(links);
  }
}

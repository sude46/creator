package com.creator.identity.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {
    @GetMapping("/api/admin/overview")
    public Map<String, String> overview() {
        return Map.of("message", "管理员身份验证通过，视频审核模块将在后续接入");
    }
}

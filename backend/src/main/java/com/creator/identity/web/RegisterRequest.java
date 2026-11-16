package com.creator.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "请输入用户名")
        @Size(max = 32, message = "用户名最多 32 位")
        String username,
        @NotBlank(message = "请输入密码")
        @Size(max = 64, message = "密码最多 64 位")
        String password) {
    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", password=<redacted>]";
    }
}

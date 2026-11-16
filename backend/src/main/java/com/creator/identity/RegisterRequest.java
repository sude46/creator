package com.creator.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "请输入用户名")
        @Pattern(regexp = "[a-zA-Z0-9_]{3,32}", message = "用户名应为 3～32 位字母、数字或下划线")
        String username,
        @NotBlank(message = "请输入密码")
        @Size(min = 10, max = 64, message = "密码长度应为 10～64 位")
        String password) {
}

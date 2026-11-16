package com.creator.common;

public enum ErrorCode {
    INVALID_USERNAME("用户名应为 3～32 位字母、数字或下划线"),
    WEAK_PASSWORD("密码需包含字母和数字，长度 10～64 位，UTF-8 编码不超过 72 字节"),
    USERNAME_TAKEN("该用户名已被使用"),
    ADMIN_BOOTSTRAP_CONFLICT("初始化管理员的用户名已被占用，或管理员账号不可用"),
    UNAUTHENTICATED("请先登录"),
    SESSION_INVALID("账号状态已变更，请重新登录"),
    FORBIDDEN("无操作权限"),
    CSRF_INVALID("安全令牌已失效，请刷新后重试"),
    LOGIN_FAILED("用户名或密码错误，或账号不可用"),
    AUTH_RATE_LIMITED("请求过于频繁，请稍后再试"),
    AUTH_BUSY("认证服务繁忙，请稍后重试"),
    INVALID_REQUEST("请求格式错误或包含不支持的字段"),
    VALIDATION_FAILED("请检查输入内容"),
    NOT_FOUND("请求的资源不存在"),
    METHOD_NOT_ALLOWED("请求方法不支持"),
    UNSUPPORTED_MEDIA_TYPE("请求内容类型不支持"),
    NOT_ACCEPTABLE("不支持请求的响应类型"),
    SERVICE_UNAVAILABLE("服务暂时不可用，请稍后重试"),
    INTERNAL_ERROR("服务发生异常，请稍后重试");

    private final String message;

    ErrorCode(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}

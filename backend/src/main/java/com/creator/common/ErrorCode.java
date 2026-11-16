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
    INVALID_PAGE("页码应为 1～1000，单页数量应为 1～50"),
    INVALID_CATEGORY("分类名称需为 1～32 位非控制字符，排序值应为 0～10000"),
    CATEGORY_NAME_TAKEN("分类名称已存在"),
    CATEGORY_IN_USE("分类仍被作品引用，请先迁移作品或停用分类"),
    STALE_WRITE("数据已变更，请刷新后重试"),
    INVALID_VIDEO("标题需为 1～120 位，简介最多 2000 位且不能包含非法控制字符"),
    INVALID_REQUEST_KEY("创建请求键必须为标准 UUID"),
    REQUEST_KEY_CONFLICT("该请求键已用于不同的创建内容"),
    CATEGORY_UNAVAILABLE("分类不存在或已停用"),
    UPLOAD_BUSY("上传名额已满，请稍后重试"),
    UPLOAD_TOO_LARGE("视频文件超过大小限制"),
    INVALID_MEDIA("仅支持 MP4 视频，文件为空、损坏或长度不符"),
    UPLOAD_TIMEOUT("上传超时，请查询作品状态后重试"),
    UPLOAD_QUOTA_EXCEEDED("存储配额或待处理作品数量已达上限"),
    SOURCE_ALREADY_BOUND("该作品已上传源文件，请查询状态"),
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

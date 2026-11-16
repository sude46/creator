package com.creator.common;

public record PageRequest(int page, int size) {
    public static final int MAX_SIZE = 50;
    public static final int MAX_PAGE = 1000;

    public PageRequest {
        if (page < 1 || page > MAX_PAGE || size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_PAGE);
        }
    }

    public int offset() {
        return (page - 1) * size;
    }
}

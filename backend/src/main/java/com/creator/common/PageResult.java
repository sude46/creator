package com.creator.common;

import java.util.List;

public record PageResult<Item>(List<Item> items, long total, int page, int size) {
    public PageResult {
        items = List.copyOf(items);
    }
}

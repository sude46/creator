package com.creator.catalog.domain;

public record Category(Long id, String name, int sortOrder, boolean enabled, long version) {
    public static final int MAX_SORT_ORDER = 10000;
}

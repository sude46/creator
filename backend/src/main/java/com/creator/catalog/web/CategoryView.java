package com.creator.catalog.web;

import com.creator.catalog.domain.Category;

public record CategoryView(String id, String name, int sortOrder, boolean enabled, long version) {
    public static CategoryView from(Category category) {
        return new CategoryView(category.id().toString(), category.name(), category.sortOrder(), category.enabled(), category.version());
    }
}

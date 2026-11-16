package com.creator.catalog.domain;

import com.creator.common.PageRequest;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
    Optional<Category> findById(long id);

    List<Category> findPage(boolean enabledOnly, PageRequest page);

    long count(boolean enabledOnly);

    Category create(CategoryName name, int sortOrder);

    boolean update(long id, long version, CategoryName name, int sortOrder, boolean enabled);

    boolean delete(long id, long version);
}

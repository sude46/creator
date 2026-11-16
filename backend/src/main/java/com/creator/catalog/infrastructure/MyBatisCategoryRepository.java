package com.creator.catalog.infrastructure;

import com.creator.catalog.domain.Category;
import com.creator.catalog.domain.CategoryName;
import com.creator.catalog.domain.CategoryRepository;
import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.common.PageRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisCategoryRepository implements CategoryRepository {
    private final CategoryMapper mapper;

    public MyBatisCategoryRepository(CategoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Category> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toCategory);
    }

    @Override
    public List<Category> findPage(boolean enabledOnly, PageRequest page) {
        return mapper.findPage(enabledOnly, page.size(), page.offset()).stream().map(this::toCategory).toList();
    }

    @Override
    public long count(boolean enabledOnly) {
        return mapper.count(enabledOnly);
    }

    @Override
    public Category create(CategoryName name, int sortOrder) {
        var row = new CategoryRow();
        row.setName(name.value());
        row.setNameKey(name.canonicalKey().getBytes(StandardCharsets.UTF_8));
        row.setSortOrder(sortOrder);
        row.setEnabled(true);
        row.setVersion(0L);
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_TAKEN);
        }
        return toCategory(row);
    }

    @Override
    public boolean update(long id, long version, CategoryName name, int sortOrder, boolean enabled) {
        try {
            return mapper.updateVersioned(id, version, name.value(), name.canonicalKey().getBytes(StandardCharsets.UTF_8),
                    sortOrder, enabled) == 1;
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_TAKEN);
        }
    }

    @Override
    public boolean delete(long id, long version) {
        try {
            return mapper.deleteVersioned(id, version) == 1;
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CATEGORY_IN_USE);
        }
    }

    private Category toCategory(CategoryRow row) {
        return new Category(row.getId(), row.getName(), row.getSortOrder(), row.getEnabled(), row.getVersion());
    }
}

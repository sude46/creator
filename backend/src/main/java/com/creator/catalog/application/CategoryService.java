package com.creator.catalog.application;

import com.creator.audit.application.AuditLog;
import com.creator.catalog.domain.Category;
import com.creator.catalog.domain.CategoryName;
import com.creator.catalog.domain.CategoryRepository;
import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.common.PageRequest;
import com.creator.common.PageResult;
import com.creator.identity.domain.Actor;
import com.creator.identity.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {
    private final CategoryRepository categories;
    private final AuditLog audit;

    public CategoryService(CategoryRepository categories, AuditLog audit) {
        this.categories = categories;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResult<Category> available(PageRequest page) {
        return page(true, page);
    }

    @Transactional(readOnly = true)
    public PageResult<Category> managed(Actor actor, PageRequest page) {
        requireAdmin(actor);
        return page(false, page);
    }

    @Transactional
    public Category create(Actor actor, String name, int sortOrder) {
        requireAdmin(actor);
        validateSortOrder(sortOrder);
        var category = categories.create(new CategoryName(name), sortOrder);
        audit.record(actor, "CATEGORY_CREATED", "CATEGORY", category.id());
        return category;
    }

    @Transactional
    public Category update(Actor actor, long id, long version, String name, int sortOrder, boolean enabled) {
        requireAdmin(actor);
        requireExisting(id);
        validateSortOrder(sortOrder);
        var categoryName = new CategoryName(name);
        if (version < 0 || !categories.update(id, version, categoryName, sortOrder, enabled)) {
            throw new BusinessException(ErrorCode.STALE_WRITE);
        }
        audit.record(actor, "CATEGORY_UPDATED", "CATEGORY", id);
        return new Category(id, categoryName.value(), sortOrder, enabled, version + 1);
    }

    @Transactional
    public void delete(Actor actor, long id, long version) {
        requireAdmin(actor);
        requireExisting(id);
        if (version < 0 || !categories.delete(id, version)) {
            throw new BusinessException(ErrorCode.STALE_WRITE);
        }
        audit.record(actor, "CATEGORY_DELETED", "CATEGORY", id);
    }

    private PageResult<Category> page(boolean enabledOnly, PageRequest page) {
        return new PageResult<>(categories.findPage(enabledOnly, page), categories.count(enabledOnly), page.page(), page.size());
    }

    private void requireExisting(long id) {
        if (categories.findById(id).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    private void requireAdmin(Actor actor) {
        if (!actor.superAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateSortOrder(int sortOrder) {
        if (sortOrder < 0 || sortOrder > Category.MAX_SORT_ORDER) {
            throw new BusinessException(ErrorCode.INVALID_CATEGORY);
        }
    }
}

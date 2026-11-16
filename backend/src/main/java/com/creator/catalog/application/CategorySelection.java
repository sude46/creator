package com.creator.catalog.application;

import com.creator.catalog.domain.CategoryRepository;
import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategorySelection {
    private final CategoryRepository categories;

    public CategorySelection(CategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireEnabled(long id) {
        if (categories.findByIdForUpdate(id).filter(category -> category.enabled()).isEmpty()) {
            throw new BusinessException(ErrorCode.CATEGORY_UNAVAILABLE);
        }
    }
}

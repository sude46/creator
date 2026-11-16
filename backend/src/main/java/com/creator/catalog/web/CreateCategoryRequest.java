package com.creator.catalog.web;

import com.creator.catalog.domain.Category;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 128) String name,
        @NotNull @Min(0) @Max(Category.MAX_SORT_ORDER) Integer sortOrder) {
}

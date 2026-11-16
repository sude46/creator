package com.creator.catalog.web;

import com.creator.catalog.application.CategoryService;
import com.creator.catalog.domain.Category;
import com.creator.common.PageRequest;
import com.creator.common.PageResult;
import com.creator.identity.domain.Actor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class CategoryController {
    private final CategoryService categories;

    public CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @GetMapping("/api/categories")
    public PageResult<CategoryView> available(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return view(categories.available(new PageRequest(page, size)));
    }

    @GetMapping("/api/admin/categories")
    public PageResult<CategoryView> managed(@RequestAttribute("currentActor") Actor actor,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return view(categories.managed(actor, new PageRequest(page, size)));
    }

    @PostMapping("/api/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView create(@RequestAttribute("currentActor") Actor actor, @Valid @RequestBody CreateCategoryRequest request) {
        return CategoryView.from(categories.create(actor, request.name(), request.sortOrder()));
    }

    @PutMapping("/api/admin/categories/{id}")
    public CategoryView update(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return CategoryView.from(categories.update(actor, id, request.version(), request.name(), request.sortOrder(), request.enabled()));
    }

    @DeleteMapping("/api/admin/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute("currentActor") Actor actor, @PathVariable long id, @RequestParam long version) {
        categories.delete(actor, id, version);
    }

    private PageResult<CategoryView> view(PageResult<Category> page) {
        return new PageResult<>(page.items().stream().map(CategoryView::from).toList(), page.total(), page.page(), page.size());
    }
}

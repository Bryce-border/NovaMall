package cn.novamall.product;

import cn.novamall.common.core.ApiResponse;
import cn.novamall.product.service.CategoryApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
@Validated
public class CategoryController {

    private final CategoryApplicationService categoryService;

    public CategoryController(CategoryApplicationService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<CategoryApiModels.CategoryListResponse> list() {
        return ApiResponse.ok(categoryService.list());
    }

    @PostMapping
    public ApiResponse<CategoryApiModels.CategoryResponse> create(
            @Valid @RequestBody CategoryApiModels.CreateCategoryRequest request) {
        return ApiResponse.ok(categoryService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CategoryApiModels.CategoryResponse> update(
            @PathVariable @Positive Long id,
            @Valid @RequestBody CategoryApiModels.UpdateCategoryRequest request) {
        return ApiResponse.ok(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        categoryService.delete(id);
        return ApiResponse.ok(null);
    }
}

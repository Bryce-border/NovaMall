package cn.novamall.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class CategoryApiModels {

    private CategoryApiModels() { }

    public record CreateCategoryRequest(
            Long parentId,
            @NotBlank @Size(max = 64) String name,
            @NotNull Integer status,
            @NotNull Integer sortOrder) { }

    public record UpdateCategoryRequest(
            Long parentId,
            @NotBlank @Size(max = 64) String name,
            @NotNull Integer status,
            @NotNull Integer sortOrder) { }

    public record CategoryResponse(Long id, Long parentId, String name, Integer status,
                                   Integer sortOrder, LocalDateTime createdAt, LocalDateTime updatedAt) { }

    public record CategoryListResponse(List<CategoryResponse> items) { }
}

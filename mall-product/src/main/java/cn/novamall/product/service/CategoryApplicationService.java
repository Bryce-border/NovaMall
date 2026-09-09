package cn.novamall.product.service;

import cn.novamall.product.CategoryApiModels;
import cn.novamall.product.entity.CategoryEntity;
import cn.novamall.product.entity.ProductEntity;
import cn.novamall.product.mapper.CategoryMapper;
import cn.novamall.product.mapper.ProductMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CategoryApplicationService {

    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;

    public CategoryApplicationService(CategoryMapper categoryMapper, ProductMapper productMapper) {
        this.categoryMapper = categoryMapper;
        this.productMapper = productMapper;
    }

    public CategoryApiModels.CategoryListResponse list() {
        List<CategoryApiModels.CategoryResponse> items = categoryMapper.selectList(
                        Wrappers.<CategoryEntity>lambdaQuery()
                                .orderByAsc(CategoryEntity::getSortOrder)
                                .orderByAsc(CategoryEntity::getId))
                .stream()
                .map(this::toResponse)
                .toList();
        return new CategoryApiModels.CategoryListResponse(items);
    }

    @Transactional
    public CategoryApiModels.CategoryResponse create(CategoryApiModels.CreateCategoryRequest request) {
        validate(request.parentId(), null, request.status(), request.sortOrder());
        LocalDateTime now = LocalDateTime.now();
        CategoryEntity category = new CategoryEntity();
        category.setParentId(normalizeParentId(request.parentId()));
        category.setName(request.name());
        category.setStatus(request.status());
        category.setSortOrder(request.sortOrder());
        category.setDeleted(0);
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        categoryMapper.insert(category);
        return toResponse(category);
    }

    @Transactional
    public CategoryApiModels.CategoryResponse update(Long id, CategoryApiModels.UpdateCategoryRequest request) {
        CategoryEntity category = require(id);
        validate(request.parentId(), id, request.status(), request.sortOrder());
        category.setParentId(normalizeParentId(request.parentId()));
        category.setName(request.name());
        category.setStatus(request.status());
        category.setSortOrder(request.sortOrder());
        category.setUpdatedAt(LocalDateTime.now());
        categoryMapper.updateById(category);
        return toResponse(category);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        if (categoryMapper.selectCount(Wrappers.<CategoryEntity>lambdaQuery()
                .eq(CategoryEntity::getParentId, id)) > 0) {
            throw new IllegalArgumentException("category has child categories: " + id);
        }
        if (productMapper.selectCount(Wrappers.<ProductEntity>lambdaQuery()
                .eq(ProductEntity::getCategoryId, id)) > 0) {
            throw new IllegalArgumentException("category has products: " + id);
        }
        categoryMapper.deleteById(id);
    }

    private void validate(Long parentId, Long currentId, Integer status, Integer sortOrder) {
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("status must be 0 or 1");
        }
        if (sortOrder == null || sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must be greater than or equal to 0");
        }
        long normalizedParentId = parentId == null ? 0L : parentId;
        if (currentId != null && normalizedParentId == currentId) {
            throw new IllegalArgumentException("category cannot be its own parent");
        }
        if (normalizedParentId != 0) {
            CategoryEntity parent = categoryMapper.selectById(normalizedParentId);
            if (parent == null || !Integer.valueOf(1).equals(parent.getStatus())) {
                throw new IllegalArgumentException("active parent category not found: " + normalizedParentId);
            }
        }
    }

    private CategoryEntity require(Long id) {
        CategoryEntity category = categoryMapper.selectById(id);
        if (category == null) {
            throw new IllegalArgumentException("category not found: " + id);
        }
        return category;
    }

    private Long normalizeParentId(Long parentId) {
        return parentId == null ? 0L : parentId;
    }

    private CategoryApiModels.CategoryResponse toResponse(CategoryEntity category) {
        return new CategoryApiModels.CategoryResponse(category.getId(), category.getParentId(), category.getName(),
                category.getStatus(), category.getSortOrder(), category.getCreatedAt(), category.getUpdatedAt());
    }
}

package cn.novamall.product.service;

import cn.novamall.product.ProductApiModels;
import cn.novamall.product.entity.CategoryEntity;
import cn.novamall.product.entity.ProductEntity;
import cn.novamall.product.mapper.CategoryMapper;
import cn.novamall.product.mapper.ProductMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductApplicationServiceTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ProductSearchService searchService;

    @Mock
    private ProductRankingService rankingService;

    @Mock
    private cn.novamall.product.mapper.ProductSkuMapper skuMapper;

    @InjectMocks
    private ProductApplicationService service;

    @BeforeEach
    void initializeMybatisMetadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                cn.novamall.product.entity.ProductSkuEntity.class);
    }

    @Test
    void getReadsCacheAndRecordsProductView() throws Exception {
        ProductApiModels.ProductResponse cached = response(100L, "Cached phone", "99.00", 1);
        ZSetOperations<String, String> zSetOperations = org.mockito.Mockito.mock(ZSetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(valueOperations.get("product:detail:100")).thenReturn("cached-json");
        when(objectMapper.readValue("cached-json", ProductApiModels.ProductResponse.class)).thenReturn(cached);

        ProductApiModels.ProductResponse result = service.get(100L);

        assertEquals("Cached phone", result.name());
        verify(productMapper, never()).selectById(100L);
        verify(zSetOperations).incrementScore("product:hot", "100", 1D);
    }

    @Test
    void getFallsBackToDatabaseAndWarmsCache() throws Exception {
        ProductEntity product = product(100L, "Database phone", "199.00", 1);
        ZSetOperations<String, String> zSetOperations = org.mockito.Mockito.mock(ZSetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(valueOperations.get("product:detail:100")).thenReturn(null);
        when(productMapper.selectById(100L)).thenReturn(product);
        when(objectMapper.writeValueAsString(any(ProductApiModels.ProductResponse.class))).thenReturn("product-json");

        ProductApiModels.ProductResponse result = service.get(100L);

        assertEquals("Database phone", result.name());
        verify(valueOperations).set(eq("product:detail:100"), anyString(), any());
        verify(zSetOperations).incrementScore("product:hot", "100", 1D);
    }

    @Test
    void searchFallsBackToMysqlWhenElasticsearchFails() {
        ProductEntity product = product(100L, "Phone", "299.00", 1);
        Page<ProductEntity> page = new Page<>(1, 20);
        page.setRecords(List.of(product));
        page.setTotal(1);
        when(searchService.search(anyString(), eq(null), eq(null), eq(null), eq(1L), eq(20L)))
                .thenThrow(new IllegalStateException("ES unavailable"));
        when(productMapper.selectPage(any(Page.class), any())).thenReturn(page);

        ProductApiModels.ProductPageResponse result = service.search(1, 20, "phone", null, null, null);

        assertEquals(1, result.total());
        assertEquals("Phone", result.items().get(0).name());
        verify(productMapper).selectPage(any(Page.class), any());
    }

    @Test
    void updateWritesDatabaseSyncsIndexAndEvictsCache() {
        CategoryEntity category = new CategoryEntity();
        category.setId(10L);
        category.setStatus(1);
        ProductEntity product = product(100L, "Old phone", "299.00", 1);
        when(categoryMapper.selectById(10L)).thenReturn(category);
        when(productMapper.selectById(100L)).thenReturn(product);
        when(productMapper.updateById(product)).thenReturn(1);

        ProductApiModels.ProductResponse result = service.update(100L,
                new ProductApiModels.UpdateProductRequest(10L, "New phone", "Updated", new BigDecimal("399.00"), 1));

        assertEquals("New phone", result.name());
        assertEquals(new BigDecimal("399.00"), result.price());
        verify(productMapper).updateById(product);
        verify(redisTemplate).delete("product:detail:100");
        ArgumentCaptor<cn.novamall.product.entity.ProductDocument> documentCaptor =
                ArgumentCaptor.forClass(cn.novamall.product.entity.ProductDocument.class);
        verify(searchService).index(documentCaptor.capture());
        assertEquals("New phone", documentCaptor.getValue().getName());
    }

    @Test
    void createRejectsInactiveCategoryBeforeWritingProduct() {
        CategoryEntity category = new CategoryEntity();
        category.setId(10L);
        category.setStatus(0);
        when(categoryMapper.selectById(10L)).thenReturn(category);

        assertThrows(IllegalArgumentException.class, () -> service.create(
                new ProductApiModels.CreateProductRequest(10L, "Phone", null, new BigDecimal("1.00"), 1)));

        verify(productMapper, never()).insert(any(ProductEntity.class));
        verify(searchService, never()).index(any());
    }

    private ProductEntity product(Long id, String name, String price, int status) {
        ProductEntity product = new ProductEntity();
        product.setId(id);
        product.setCategoryId(10L);
        product.setName(name);
        product.setSubtitle("subtitle");
        product.setPrice(new BigDecimal(price));
        product.setStatus(status);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        return product;
    }

    private ProductApiModels.ProductResponse response(Long id, String name, String price, int status) {
        LocalDateTime now = LocalDateTime.now();
        return new ProductApiModels.ProductResponse(id, 10L, name, "subtitle",
                new BigDecimal(price), status, now, now);
    }
}

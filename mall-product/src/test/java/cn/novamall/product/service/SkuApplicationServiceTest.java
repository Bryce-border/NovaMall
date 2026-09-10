package cn.novamall.product.service;

import cn.novamall.product.SkuApiModels;
import cn.novamall.product.entity.ProductEntity;
import cn.novamall.product.entity.ProductSkuEntity;
import cn.novamall.product.mapper.ProductMapper;
import cn.novamall.product.mapper.ProductSkuMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkuApplicationServiceTest {
    @Mock ProductMapper products;
    @Mock ProductSkuMapper skus;
    @Spy ObjectMapper json = new ObjectMapper();
    @InjectMocks SkuApplicationService service;

    @Test
    void nonDefaultSkuUsesItsOwnPriceSpecsAndImage() throws Exception {
        given(1, "{\"color\":\"Black\"}");
        SkuApiModels.SkuResponse response = service.get(101L);
        assertEquals(new BigDecimal("12.00"), response.price());
        assertEquals("Black", response.specs().get("color"));
        assertEquals("/black.jpg", response.coverImageUrl());
        assertEquals(100L, response.productId());
        var wire = json.readTree(json.writeValueAsString(response));
        assertTrue(wire.get("skuId").isTextual());
        assertEquals("12.00", wire.get("price").asText());
    }

    @Test
    void offSaleProductMakesActiveSkuUnavailable() {
        given(0, "{}");
        assertEquals(0, service.get(101L).status());
    }

    @Test
    void invalidStoredSpecificationsFailExplicitly() {
        given(1, "broken-json");
        assertThrows(IllegalStateException.class, () -> service.get(101L));
    }

    private void given(int status, String specs) {
        ProductEntity product = new ProductEntity();
        product.setId(100L);
        product.setName("Phone");
        product.setStatus(status);
        product.setPrice(new BigDecimal("10.00"));
        ProductSkuEntity sku = new ProductSkuEntity();
        sku.setId(101L);
        sku.setProductId(100L);
        sku.setSkuCode("PHONE-BLACK");
        sku.setName("Black");
        sku.setPrice(new BigDecimal("12.00"));
        sku.setImageUrl("/black.jpg");
        sku.setStatus(1);
        sku.setSpecJson(specs);
        when(products.selectById(100L)).thenReturn(product);
        when(skus.selectById(101L)).thenReturn(sku);
    }
}

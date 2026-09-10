package cn.novamall.order;

import cn.novamall.common.web.ApiExceptionHandler;
import cn.novamall.order.client.ProductClient;
import cn.novamall.order.service.CartOrderService;
import cn.novamall.order.service.OrderApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderContractTest {
    @Test
    void checkoutRejectsMissingAddressBeforeServiceInvocation() throws Exception {
        CartOrderService service = mock(CartOrderService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CartOrderController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/orders/checkout").header("X-User-Id", "7").header("X-User-Name", "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestKey\":\"e8b6a56a-d2bc-40a5-89b4-89ac1648a900\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void directOrderRejectsAmbiguousReferencesAndNullItems() throws Exception {
        OrderApplicationService service = mock(OrderApplicationService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new OrderController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        for (String body : new String[]{"{\"items\":[{\"productId\":100,\"skuId\":101,\"quantity\":1}]}",
                "{\"items\":[null]}"}) {
            mvc.perform(post("/api/orders").header("X-User-Id", "7").header("X-User-Name", "alice")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void skuClientDecodesStringIdsAndMoneyWithoutPrecisionLoss() throws Exception {
        ProductClient.SkuSnapshot snapshot = new ObjectMapper().readValue("""
                {"skuId":"9007199254740993","productId":"100","skuCode":"BLACK",
                 "productName":"Phone","skuName":"Black","specs":{"color":"Black"},
                 "coverImageUrl":null,"price":"3999.00","marketPrice":"4299.00","status":1}
                """, ProductClient.SkuSnapshot.class);
        assertEquals(9007199254740993L, snapshot.skuId());
        assertEquals("3999.00", snapshot.price().toPlainString());
        assertEquals("Black", snapshot.specs().get("color"));
    }
}

package cn.novamall.order.client;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "mall-inventory", path = "/internal/inventory")
public interface InventoryClient {

    @PostMapping("/reserve")
    ReserveResponse reserve(@RequestBody ReserveRequest request);

    @PostMapping("/release")
    void release(@RequestBody ReleaseRequest request);

    record ReserveRequest(@NotNull @Positive Long productId, @NotNull @Positive Integer quantity,
                          @NotNull String orderNo) { }

    record ReserveResponse(boolean success, String message) { }

    record ReleaseRequest(@NotNull String orderNo) { }
}

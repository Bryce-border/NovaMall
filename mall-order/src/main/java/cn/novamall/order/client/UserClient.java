package cn.novamall.order.client;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;
@FeignClient(name="mall-user",path="/internal/users")
public interface UserClient {
 @GetMapping("/addresses/{id}") AddressSnapshot address(@PathVariable("id") Long id,@RequestParam("userId") Long userId);
 record AddressSnapshot(Long id,Long userId,String receiverName,String receiverPhone,String province,String city,String district,String detailAddress,String postalCode){}

 @GetMapping("/cart/selected") List<CartItemSnapshot> selectedCart(@RequestParam("userId") Long userId);
 @PostMapping("/cart/cleanup") void cleanupCart(@RequestBody CartCleanupRequest request);
 record CartItemSnapshot(Long cartItemId, Long skuId, Integer quantity, Long version) { }
 record CartCleanupItem(Long cartItemId, Long version) { }
 record CartCleanupRequest(Long userId, List<CartCleanupItem> items) { }
}

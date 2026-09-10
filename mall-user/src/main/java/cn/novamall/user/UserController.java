package cn.novamall.user;

import cn.novamall.common.core.ApiResponse;
import cn.novamall.user.entity.UserEntity;
import cn.novamall.user.service.UserApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserApplicationService userService;

    public UserController(UserApplicationService userService) {
        this.userService = userService;
    }

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("mall-user");
    }

    @PostMapping("/register")
    public ApiResponse<UserApiModels.RegisterResponse> register(
            @Valid @RequestBody UserApiModels.RegisterRequest request) {
        UserEntity user = userService.register(request.username(), request.password(), request.nickname());
        return ApiResponse.ok(new UserApiModels.RegisterResponse(user.getId(), user.getUsername(), user.getNickname()));
    }

    @GetMapping("/addresses")
    public ApiResponse<java.util.List<UserApiModels.AddressResponse>> addresses(@RequestHeader("X-User-Id") Long userId) { return ApiResponse.ok(userService.addresses(userId)); }

    @PostMapping("/addresses")
    public ApiResponse<UserApiModels.AddressResponse> addAddress(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody UserApiModels.AddressRequest request) { return ApiResponse.ok(userService.addAddress(userId, request)); }

    @DeleteMapping("/addresses/{id}")
    public ApiResponse<Void> deleteAddress(@RequestHeader("X-User-Id") Long userId, @org.springframework.web.bind.annotation.PathVariable Long id) { userService.deleteAddress(userId, id); return ApiResponse.ok(null); }

    @GetMapping("/cart") public ApiResponse<java.util.List<UserApiModels.CartItemResponse>> cart(@RequestHeader("X-User-Id") Long userId){return ApiResponse.ok(userService.cart(userId));}
    @PostMapping("/cart/items") public ApiResponse<UserApiModels.CartItemResponse> addCart(@RequestHeader("X-User-Id") Long userId,@Valid @RequestBody UserApiModels.CartItemRequest r){return ApiResponse.ok(userService.addCart(userId,r));}
    @PutMapping("/cart/items/{id}") public ApiResponse<UserApiModels.CartItemResponse> updateCart(@RequestHeader("X-User-Id") Long userId,@PathVariable Long id,@Valid @RequestBody UserApiModels.CartUpdateRequest r){return ApiResponse.ok(userService.updateCart(userId,id,r));}
    @DeleteMapping("/cart/items/{id}") public ApiResponse<Void> deleteCart(@RequestHeader("X-User-Id") Long userId,@PathVariable Long id){userService.deleteCart(userId,id);return ApiResponse.ok(null);}
    @PutMapping("/cart/items/{id}/selected") public ApiResponse<UserApiModels.CartItemResponse> selectCart(@RequestHeader("X-User-Id") Long userId,@PathVariable Long id,@RequestParam boolean selected){return ApiResponse.ok(userService.selectCart(userId,id,selected));}
}

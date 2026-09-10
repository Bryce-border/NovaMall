package cn.novamall.user;

import cn.novamall.user.service.CartCheckoutService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/internal/users/cart")
@Validated
public class CartInternalController {
    private final CartCheckoutService service;

    public CartInternalController(CartCheckoutService service) { this.service = service; }

    @GetMapping("/selected")
    public List<CartCheckoutModels.Item> selected(@RequestParam @Positive Long userId) {
        return service.selected(userId);
    }

    @PostMapping("/cleanup")
    public void cleanup(@Valid @RequestBody CartCheckoutModels.CleanupRequest request) {
        service.cleanup(request);
    }
}

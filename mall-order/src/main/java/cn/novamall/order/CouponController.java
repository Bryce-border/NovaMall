package cn.novamall.order;
import cn.novamall.common.core.ApiResponse;
import cn.novamall.order.service.CouponService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {
    private final CouponService service;
    public CouponController(CouponService service){this.service=service;}
    @GetMapping("/campaigns") public ApiResponse<?> campaigns(){return ApiResponse.ok(service.campaigns(false));}
    @GetMapping("/mine") public ApiResponse<?> mine(@RequestHeader("X-User-Id") Long user){return ApiResponse.ok(service.wallet(user));}
    @PostMapping("/campaigns/{id}/claim") public ApiResponse<?> claim(@RequestHeader("X-User-Id") Long user,@PathVariable long id,@RequestBody CouponService.ClaimRequest request){return ApiResponse.ok(service.claim(user,id,request.confirmMockPayment()));}
    @GetMapping("/admin") public ApiResponse<?> admin(@RequestHeader("X-User-Role") String role){requireAdmin(role);return ApiResponse.ok(service.campaigns(true));}
    @PostMapping("/admin") public ApiResponse<?> create(@RequestHeader("X-User-Role") String role,@Valid @RequestBody CouponService.CampaignRequest request){requireAdmin(role);return ApiResponse.ok(service.create(request));}
    @PutMapping("/admin/{id}/status") public ApiResponse<?> status(@RequestHeader("X-User-Role") String role,@PathVariable long id,@RequestParam int status){requireAdmin(role);service.status(id,status);return ApiResponse.ok(null);}
    private void requireAdmin(String role){if(!"ADMIN".equalsIgnoreCase(role))throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
}

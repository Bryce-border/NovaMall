package cn.novamall.order;

import cn.novamall.common.core.ApiResponse;
import cn.novamall.order.service.OrderDetailService;
import cn.novamall.order.service.RefundService;
import cn.novamall.order.service.RefundReturnService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/refunds")
public class RefundController {
    private final RefundService refunds;
    private final OrderDetailService orders;
    private final RefundReturnService returns;
    public RefundController(RefundService refunds, OrderDetailService orders, RefundReturnService returns) { this.refunds = refunds; this.orders = orders; this.returns=returns; }

    @PostMapping("/{no}/return-logistics")
    public ApiResponse<?> logistics(@RequestHeader("X-User-Id") long user,@PathVariable String no,@Valid @RequestBody RefundApiModels.LogisticsRequest body) {
        return ApiResponse.ok(returns.logistics(user,no,body));
    }
    @PostMapping("/{no}/recheck")
    public ApiResponse<?> recheck(@RequestHeader("X-User-Id") long user,@PathVariable String no,@Valid @RequestBody RefundApiModels.RecheckRequest body) {
        return ApiResponse.ok(returns.recheck(user,no,body));
    }
    @PostMapping("/admin/{no}/inspect")
    public ApiResponse<?> inspect(@RequestHeader("X-User-Role") String role,@RequestHeader("X-User-Id") long admin,@PathVariable String no,@Valid @RequestBody RefundApiModels.InspectRequest body) {
        requireAdmin(role);return ApiResponse.ok(returns.inspect(admin,no,body));
    }

    @GetMapping("/orders/{orderNo}/eligibility")
    public ApiResponse<?> eligibility(@RequestHeader("X-User-Id") long user, @PathVariable String orderNo) {
        return ApiResponse.ok(refunds.eligibility(user, orderNo));
    }
    @PostMapping("/orders/{orderNo}/quote")
    public ApiResponse<?> quote(@RequestHeader("X-User-Id") long user, @PathVariable String orderNo, @Valid @RequestBody RefundApiModels.QuoteRequest body) {
        return ApiResponse.ok(refunds.quote(user, orderNo, body));
    }
    @PostMapping("/orders/{orderNo}")
    public ApiResponse<?> apply(@RequestHeader("X-User-Id") long user, @PathVariable String orderNo, @Valid @RequestBody RefundApiModels.ApplyRequest body) {
        return ApiResponse.ok(refunds.apply(user, orderNo, body));
    }
    @GetMapping("/{no}")
    public ApiResponse<?> detail(@RequestHeader("X-User-Id") long user, @PathVariable String no) { return ApiResponse.ok(refunds.detail(user, no)); }
    @PostMapping("/{no}/withdraw")
    public ApiResponse<?> withdraw(@RequestHeader("X-User-Id") long user, @PathVariable String no) { return ApiResponse.ok(refunds.withdraw(user, no)); }

    @GetMapping({"", "/admin"})
    public ApiResponse<?> page(@RequestHeader("X-User-Id") long user, @RequestHeader("X-User-Role") String role,
            jakarta.servlet.http.HttpServletRequest request,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status, @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String refundNo, @RequestParam(required = false) String buyer,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        boolean admin = request.getRequestURI().endsWith("/admin");
        if (admin) requireAdmin(role);
        return ApiResponse.ok(refunds.page(admin ? null : user, page, size, status, orderNo, refundNo, buyer, from, to));
    }
    @GetMapping("/admin/{no}")
    public ApiResponse<?> adminDetail(@RequestHeader("X-User-Role") String role, @PathVariable String no) {
        requireAdmin(role); return ApiResponse.ok(refunds.detail(null, no));
    }
    @GetMapping("/admin/{no}/order")
    public ApiResponse<?> original(@RequestHeader("X-User-Role") String role, @PathVariable String no) {
        requireAdmin(role);
        var refund = refunds.detail(null, no);
        return ApiResponse.ok(orders.get(Long.valueOf(refund.get("user_id").toString()), refund.get("order_no").toString()));
    }
    @PostMapping("/admin/{no}/review")
    public ApiResponse<?> review(@RequestHeader("X-User-Role") String role, @RequestHeader("X-User-Id") long admin,
            @PathVariable String no, @Valid @RequestBody RefundApiModels.ReviewRequest body) {
        requireAdmin(role); return ApiResponse.ok(refunds.review(admin, no, body));
    }
    @PostMapping("/admin/{no}/retry")
    public ApiResponse<?> retry(@RequestHeader("X-User-Role") String role, @RequestHeader("X-User-Id") long admin, @PathVariable String no) {
        requireAdmin(role); return ApiResponse.ok(refunds.retry(admin, no));
    }
    private void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}

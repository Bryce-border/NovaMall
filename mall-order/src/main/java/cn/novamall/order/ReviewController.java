package cn.novamall.order;
import cn.novamall.common.core.ApiResponse;
import cn.novamall.common.id.IdGenerator;
import cn.novamall.order.service.CouponService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final JdbcTemplate db;
    public ReviewController(JdbcTemplate db){this.db=db;}
    public record ReviewRequest(@Min(1) @Max(5) int rating,@NotBlank @Size(max=1000) String content){}
    @GetMapping("/{productId}") public ApiResponse<?> list(@PathVariable long productId){
        return ApiResponse.ok(CouponService.clean(db.queryForList("SELECT id,rating,content,CONCAT(LEFT(user_name,1),'***') AS buyer,created_at FROM mall_product_review WHERE product_id=? ORDER BY created_at DESC LIMIT 50",productId)));
    }
    @PostMapping("/{productId}") @Transactional
    public ApiResponse<?> create(@PathVariable long productId,@RequestHeader("X-User-Id") long user,@RequestHeader("X-User-Name") String name,@Valid @RequestBody ReviewRequest request){
        Integer count=db.queryForObject("SELECT COUNT(*) FROM mall_order o JOIN mall_order_item i ON o.id=i.order_id WHERE o.user_id=? AND o.status='COMPLETED' AND i.product_id=?",Integer.class,user,productId);
        if(count==null||count==0)throw new IllegalArgumentException("确认收货后即可评价该商品");
        try {db.update("INSERT INTO mall_product_review(id,product_id,user_id,user_name,rating,content,created_at) VALUES(?,?,?,?,?,?,NOW())",IdGenerator.nextId(),productId,user,name,request.rating(),request.content().trim());}
        catch(DuplicateKeyException e){throw new IllegalArgumentException("你已经评价过这件商品");}
        return ApiResponse.ok(null);
    }
}

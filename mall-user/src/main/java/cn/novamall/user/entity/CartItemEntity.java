package cn.novamall.user.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@TableName("mall_cart_item")
public class CartItemEntity {
 private Long version;
 public Long getVersion(){return version;} public void setVersion(Long v){version=v;}
 @TableId(type=IdType.ASSIGN_ID) private Long id; private Long userId; private Long productId; private Long skuId; private Integer quantity; private Integer selected; private LocalDateTime createdAt; private LocalDateTime updatedAt;
 public Long getId(){return id;} public void setId(Long v){id=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;} public Long getProductId(){return productId;} public void setProductId(Long v){productId=v;} public Long getSkuId(){return skuId;} public void setSkuId(Long v){skuId=v;} public Integer getQuantity(){return quantity;} public void setQuantity(Integer v){quantity=v;} public Integer getSelected(){return selected;} public void setSelected(Integer v){selected=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}

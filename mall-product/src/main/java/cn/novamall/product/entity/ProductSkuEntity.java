package cn.novamall.product.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@TableName("mall_product_sku")
public class ProductSkuEntity { @TableId(type=IdType.ASSIGN_ID) private Long id; private Long productId; private String skuCode; private String name; private String specJson; private String imageUrl; private BigDecimal price; private BigDecimal marketPrice; private Integer status; private LocalDateTime createdAt; private LocalDateTime updatedAt;
 public Long getId(){return id;} public void setId(Long v){id=v;} public Long getProductId(){return productId;} public void setProductId(Long v){productId=v;} public String getSkuCode(){return skuCode;} public void setSkuCode(String v){skuCode=v;} public String getName(){return name;} public void setName(String v){name=v;} public String getSpecJson(){return specJson;} public void setSpecJson(String v){specJson=v;} public String getImageUrl(){return imageUrl;} public void setImageUrl(String v){imageUrl=v;} public BigDecimal getPrice(){return price;} public void setPrice(BigDecimal v){price=v;} public BigDecimal getMarketPrice(){return marketPrice;} public void setMarketPrice(BigDecimal v){marketPrice=v;} public Integer getStatus(){return status;} public void setStatus(Integer v){status=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}

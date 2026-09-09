package cn.novamall.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("mall_seckill_order")
public class SeckillOrderEntity {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private String seckillNo; private Long userId; private Long productId;
    private Integer quantity; private String status; private String messageId;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getSeckillNo(){return seckillNo;} public void setSeckillNo(String v){seckillNo=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public Long getProductId(){return productId;} public void setProductId(Long v){productId=v;}
    public Integer getQuantity(){return quantity;} public void setQuantity(Integer v){quantity=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getMessageId(){return messageId;} public void setMessageId(String v){messageId=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}

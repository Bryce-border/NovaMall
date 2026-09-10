package cn.novamall.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("mall_user_address")
public class UserAddressEntity {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private Long userId; private String receiverName; private String receiverPhone;
    private String province; private String city; private String district; private String detailAddress;
    private String postalCode; private Integer isDefault;
    @TableLogic private Integer deleted;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public String getReceiverName(){return receiverName;} public void setReceiverName(String v){receiverName=v;}
    public String getReceiverPhone(){return receiverPhone;} public void setReceiverPhone(String v){receiverPhone=v;}
    public String getProvince(){return province;} public void setProvince(String v){province=v;}
    public String getCity(){return city;} public void setCity(String v){city=v;}
    public String getDistrict(){return district;} public void setDistrict(String v){district=v;}
    public String getDetailAddress(){return detailAddress;} public void setDetailAddress(String v){detailAddress=v;}
    public String getPostalCode(){return postalCode;} public void setPostalCode(String v){postalCode=v;}
    public Integer getIsDefault(){return isDefault;} public void setIsDefault(Integer v){isDefault=v;}
    public Integer getDeleted(){return deleted;} public void setDeleted(Integer v){deleted=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}

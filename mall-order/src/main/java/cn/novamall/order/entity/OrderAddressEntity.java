package cn.novamall.order.entity;
import com.baomidou.mybatisplus.annotation.*;
@TableName("mall_order_address")
public class OrderAddressEntity { @TableId(type=IdType.ASSIGN_ID) private Long id; private Long orderId; private String receiverName,receiverPhone,province,city,district,detailAddress,postalCode;
 public Long getId(){return id;} public void setId(Long v){id=v;} public Long getOrderId(){return orderId;} public void setOrderId(Long v){orderId=v;} public String getReceiverName(){return receiverName;} public void setReceiverName(String v){receiverName=v;} public String getReceiverPhone(){return receiverPhone;} public void setReceiverPhone(String v){receiverPhone=v;} public String getProvince(){return province;} public void setProvince(String v){province=v;} public String getCity(){return city;} public void setCity(String v){city=v;} public String getDistrict(){return district;} public void setDistrict(String v){district=v;} public String getDetailAddress(){return detailAddress;} public void setDetailAddress(String v){detailAddress=v;} public String getPostalCode(){return postalCode;} public void setPostalCode(String v){postalCode=v;}
}

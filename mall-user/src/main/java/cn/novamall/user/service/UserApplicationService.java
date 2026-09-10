package cn.novamall.user.service;

import cn.novamall.user.entity.UserEntity;
import cn.novamall.user.mapper.UserMapper;
import cn.novamall.user.mapper.UserAddressMapper;
import cn.novamall.user.mapper.CartItemMapper;
import cn.novamall.user.entity.CartItemEntity;
import cn.novamall.user.entity.UserAddressEntity;
import cn.novamall.user.UserApiModels;
import cn.novamall.user.client.ProductClient;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserApplicationService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserAddressMapper addressMapper;
    private final CartItemMapper cartMapper;
    private final ProductClient productClient;

    public UserApplicationService(UserMapper userMapper, PasswordEncoder passwordEncoder, UserAddressMapper addressMapper, CartItemMapper cartMapper, ProductClient productClient) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.addressMapper = addressMapper;
        this.cartMapper = cartMapper;
        this.productClient = productClient;
    }

    @Transactional
    public UserEntity register(String username, String password, String nickname) {
        if (userMapper.findByUsername(username) != null) {
            throw new IllegalArgumentException("username already exists");
        }
        LocalDateTime now = LocalDateTime.now();
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setRole("USER");
        user.setStatus(1);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        return user;
    }

    public UserEntity findByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    public List<UserApiModels.AddressResponse> addresses(Long userId) {
        return addressMapper.selectList(Wrappers.<UserAddressEntity>lambdaQuery().eq(UserAddressEntity::getUserId, userId)
                .orderByDesc(UserAddressEntity::getIsDefault).orderByDesc(UserAddressEntity::getUpdatedAt)).stream().map(this::addressResponse).toList();
    }

    @Transactional
    public UserApiModels.AddressResponse addAddress(Long userId, UserApiModels.AddressRequest request) {
        if (Boolean.TRUE.equals(request.isDefault())) addressMapper.clearDefault(userId);
        UserAddressEntity e = new UserAddressEntity(); e.setUserId(userId); apply(e, request);
        e.setIsDefault(Boolean.TRUE.equals(request.isDefault()) ? 1 : 0); e.setDeleted(0);
        e.setCreatedAt(LocalDateTime.now()); e.setUpdatedAt(e.getCreatedAt()); addressMapper.insert(e); return addressResponse(e);
    }

    @Transactional
    public void deleteAddress(Long userId, Long id) {
        UserAddressEntity e = addressMapper.selectOne(Wrappers.<UserAddressEntity>lambdaQuery().eq(UserAddressEntity::getId,id).eq(UserAddressEntity::getUserId,userId));
        if (e == null) throw new IllegalArgumentException("address not found"); addressMapper.deleteById(id);
    }

    private void apply(UserAddressEntity e, UserApiModels.AddressRequest r) { e.setReceiverName(r.receiverName()); e.setReceiverPhone(r.receiverPhone()); e.setProvince(r.province()); e.setCity(r.city()); e.setDistrict(r.district()); e.setDetailAddress(r.detailAddress()); e.setPostalCode(r.postalCode()); }
    private UserApiModels.AddressResponse addressResponse(UserAddressEntity e) { return new UserApiModels.AddressResponse(e.getId(),e.getReceiverName(),e.getReceiverPhone(),e.getProvince(),e.getCity(),e.getDistrict(),e.getDetailAddress(),e.getPostalCode(),Integer.valueOf(1).equals(e.getIsDefault())); }

    public List<UserApiModels.CartItemResponse> cart(Long userId) { return cartMapper.selectList(Wrappers.<CartItemEntity>lambdaQuery().eq(CartItemEntity::getUserId,userId).orderByDesc(CartItemEntity::getUpdatedAt)).stream().map(this::cartResponse).toList(); }
    @Transactional public UserApiModels.CartItemResponse addCart(Long userId, UserApiModels.CartItemRequest r) {
        requireQuantity(r.quantity());
        ProductClient.SkuSnapshot sku = productClient.get(r.skuId());
        if (sku == null || !r.skuId().equals(sku.skuId()) || sku.productId() == null
                || !Integer.valueOf(1).equals(sku.status())) throw new IllegalArgumentException("sku is not available");
        CartItemEntity item = new CartItemEntity();
        item.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
        item.setUserId(userId);
        item.setProductId(sku.productId());
        item.setSkuId(sku.skuId());
        item.setQuantity(r.quantity());
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(item.getCreatedAt());
        cartMapper.addQuantity(item);
        return cartResponse(cartMapper.selectOne(Wrappers.<CartItemEntity>lambdaQuery()
                .eq(CartItemEntity::getUserId, userId).eq(CartItemEntity::getSkuId, sku.skuId())));
    }
    @Transactional
    public UserApiModels.CartItemResponse updateCart(Long userId, Long id, UserApiModels.CartUpdateRequest r) {
        requireQuantity(r.quantity());
        CartItemEntity item = ownedCart(userId, id);
        item.setQuantity(r.quantity());
        updateVersionedCart(item);
        return cartResponse(item);
    }
    @Transactional public void deleteCart(Long userId, Long id){cartMapper.delete(ownedQuery(userId,id));}
    @Transactional
    public UserApiModels.CartItemResponse selectCart(Long userId, Long id, boolean selected) {
        CartItemEntity item = ownedCart(userId, id);
        item.setSelected(selected ? 1 : 0);
        updateVersionedCart(item);
        return cartResponse(item);
    }

    private void requireQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) throw new IllegalArgumentException("positive quantity required");
    }

    private void updateVersionedCart(CartItemEntity item) {
        item.setUpdatedAt(LocalDateTime.now());
        if (cartMapper.updateVersioned(item) != 1) {
            throw new IllegalStateException("cart item changed; reload and retry");
        }
    }
    private CartItemEntity ownedCart(Long u,Long id){CartItemEntity e=cartMapper.selectOne(ownedQuery(u,id));if(e==null)throw new IllegalArgumentException("cart item not found");return e;}
    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CartItemEntity> ownedQuery(Long u,Long id){return Wrappers.<CartItemEntity>lambdaQuery().eq(CartItemEntity::getUserId,u).eq(CartItemEntity::getId,id);}
    private UserApiModels.CartItemResponse cartResponse(CartItemEntity e){return new UserApiModels.CartItemResponse(e.getId(),e.getProductId(),e.getSkuId(),e.getQuantity(),Integer.valueOf(1).equals(e.getSelected()));}
}

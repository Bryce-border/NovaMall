package cn.novamall.user;

import cn.novamall.user.entity.UserEntity;
import cn.novamall.user.service.UserApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cn.novamall.user.entity.UserAddressEntity;
import cn.novamall.user.mapper.UserAddressMapper;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/users")
public class UserInternalController {

    private final UserApplicationService userService;
    private final UserAddressMapper addressMapper;

    public UserInternalController(UserApplicationService userService, UserAddressMapper addressMapper) {
        this.userService = userService;
        this.addressMapper = addressMapper;
    }

    @GetMapping("/addresses/{id}")
    public AddressSnapshot address(@PathVariable Long id, @RequestParam Long userId) {
        UserAddressEntity a = addressMapper.selectById(id);
        if (a == null || !userId.equals(a.getUserId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "address not found");
        return new AddressSnapshot(a.getId(), a.getUserId(), a.getReceiverName(), a.getReceiverPhone(), a.getProvince(), a.getCity(), a.getDistrict(), a.getDetailAddress(), a.getPostalCode());
    }

    @GetMapping("/by-username")
    public UserProfile findByUsername(@RequestParam String username) {
        UserEntity user = userService.findByUsername(username);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found");
        }
        return new UserProfile(user.getId(), user.getUsername(), user.getPasswordHash(), user.getRole(), user.getStatus());
    }

    public record UserProfile(Long id, String username, String passwordHash, String role, Integer status) { }
    public record AddressSnapshot(Long id, Long userId, String receiverName, String receiverPhone, String province, String city, String district, String detailAddress, String postalCode) { }
}

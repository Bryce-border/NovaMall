package cn.novamall.auth;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "mall-user", path = "/internal/users")
public interface UserClient {

    @GetMapping("/by-username")
    UserProfile findByUsername(@RequestParam String username);

    record UserProfile(Long id, String username, String passwordHash, String role, Integer status) { }
}

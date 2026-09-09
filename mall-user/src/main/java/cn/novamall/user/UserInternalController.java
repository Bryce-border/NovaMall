package cn.novamall.user;

import cn.novamall.user.entity.UserEntity;
import cn.novamall.user.service.UserApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/users")
public class UserInternalController {

    private final UserApplicationService userService;

    public UserInternalController(UserApplicationService userService) {
        this.userService = userService;
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
}

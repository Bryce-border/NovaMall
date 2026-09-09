package cn.novamall.user;

import cn.novamall.common.core.ApiResponse;
import cn.novamall.user.entity.UserEntity;
import cn.novamall.user.service.UserApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserApplicationService userService;

    public UserController(UserApplicationService userService) {
        this.userService = userService;
    }

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("mall-user");
    }

    @PostMapping("/register")
    public ApiResponse<UserApiModels.RegisterResponse> register(
            @Valid @RequestBody UserApiModels.RegisterRequest request) {
        UserEntity user = userService.register(request.username(), request.password(), request.nickname());
        return ApiResponse.ok(new UserApiModels.RegisterResponse(user.getId(), user.getUsername(), user.getNickname()));
    }
}

package cn.novamall.auth;

import cn.novamall.common.core.ApiResponse;
import cn.novamall.auth.service.AuthApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthApplicationService authService;

    public AuthController(AuthApplicationService authService) {
        this.authService = authService;
    }

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("mall-auth");
    }

    @PostMapping("/login")
    public ApiResponse<AuthApiModels.LoginResponse> login(
            @Valid @RequestBody AuthApiModels.LoginRequest request) {
        AuthApplicationService.LoginResult result = authService.login(request.username(), request.password());
        return ApiResponse.ok(new AuthApiModels.LoginResponse(
                result.accessToken(), "Bearer", result.expiresInSeconds()));
    }
}

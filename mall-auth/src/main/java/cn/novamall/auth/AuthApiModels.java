package cn.novamall.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthApiModels {

    private AuthApiModels() { }

    public record LoginRequest(
            @NotBlank @Size(max = 32) String username,
            @NotBlank @Size(max = 64) String password) { }

    public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds) { }
}

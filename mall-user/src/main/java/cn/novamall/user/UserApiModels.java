package cn.novamall.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class UserApiModels {

    private UserApiModels() { }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 32) String username,
            @NotBlank @Size(min = 8, max = 64) String password,
            @NotBlank @Size(max = 64) String nickname) { }

    public record RegisterResponse(Long id, String username, String nickname) { }
}

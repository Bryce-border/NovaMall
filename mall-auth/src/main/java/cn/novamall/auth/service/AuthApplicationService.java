package cn.novamall.auth.service;

import cn.novamall.auth.UserClient;
import cn.novamall.common.security.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthApplicationService {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofHours(2);

    private final UserClient userClient;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthApplicationService(UserClient userClient, PasswordEncoder passwordEncoder,
                                  JwtTokenService jwtTokenService) {
        this.userClient = userClient;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    public LoginResult login(String username, String password) {
        UserClient.UserProfile user;
        try {
            user = userClient.findByUsername(username);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("invalid username or password");
        }
        if (user == null || user.status() == null || user.status() != 1
                || !passwordEncoder.matches(password, user.passwordHash())) {
            throw new IllegalArgumentException("invalid username or password");
        }
        String token = jwtTokenService.createAccessToken(user.username(), user.id(), user.role(), ACCESS_TOKEN_TTL);
        return new LoginResult(token, ACCESS_TOKEN_TTL.toSeconds());
    }

    public record LoginResult(String accessToken, long expiresInSeconds) { }
}

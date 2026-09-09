package cn.novamall.common.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenServiceTest {

    private final JwtTokenService tokenService = new JwtTokenService(
            "nova-mall-local-secret-key-change-before-production");

    @Test
    void carriesUserIdAndSubject() {
        String token = tokenService.createAccessToken("alice", 42L, "USER", Duration.ofMinutes(5));

        assertTrue(tokenService.isValid(token));
        assertEquals("alice", tokenService.subject(token));
        assertEquals(42L, tokenService.userId(token));
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        JwtTokenService other = new JwtTokenService("another-local-secret-key-that-is-long-enough");
        String token = other.createAccessToken("alice", "USER", Duration.ofMinutes(5));

        assertFalse(tokenService.isValid(token));
    }
}

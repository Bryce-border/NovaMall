package cn.novamall.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public final class JwtTokenService {

    private final SecretKey signingKey;

    public JwtTokenService(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(String subject, String role, Duration ttl) {
        return createAccessToken(subject, null, role, ttl);
    }

    public String createAccessToken(String subject, Long userId, String role, Duration ttl) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)));
        if (userId != null) {
            builder.claim("userId", userId);
        }
        return builder.signWith(signingKey).compact();
    }

    public String subject(String token) {
        return parse(token).getPayload().getSubject();
    }

    public Long userId(String token) {
        Number userId = parse(token).getPayload().get("userId", Number.class);
        return userId == null ? null : userId.longValue();
    }

    public String role(String token) {
        return parse(token).getPayload().get("role", String.class);
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token);
    }
}

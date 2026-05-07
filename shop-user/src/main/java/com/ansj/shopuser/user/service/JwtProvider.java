package com.ansj.shopuser.user.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.Optional;
import java.util.function.Function;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    public static final long REFRESH_TOKEN_EXPIRY_MILLIS = 1000L * 60 * 60 * 24 * 7;

    private final SecretKey key = Keys.hmacShaKeyFor(
        "my-secret-key-my-secret-key-my-secret-key".getBytes()
    );

    public String createAccessToken(Long userId, String role) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
            .setSubject(String.valueOf(userId))
            .claim("role", role)
            .setIssuedAt(new Date(now))
            .setExpiration(new Date(now + 1000 * 60 * 30))
            .signWith(key)
            .compact();
    }

    public String createRefreshToken(Long userId) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
            .setSubject(String.valueOf(userId))
            .setIssuedAt(new Date(now))
            .setExpiration(new Date(now + REFRESH_TOKEN_EXPIRY_MILLIS))
            .signWith(key)
            .compact();
    }

    public boolean validAccessToken(String accessToken) {
        try {
            getClaims(accessToken);
            return true;
        } catch (ExpiredJwtException e) {
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Optional<String> reissue(String refreshToken, Function<Long, String> roleFunc) {
        try {
            Claims claims = getClaims(refreshToken);

            Long userId = Long.valueOf(claims.getSubject());
            String role = roleFunc.apply(userId);
            String accessToken = createAccessToken(userId, role);

            return Optional.of(accessToken);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Long getUserId(String token) {
        Claims claims = getClaims(token);
        return Long.valueOf(claims.getSubject());
    }

    /** 만료된 토큰에서도 userId를 추출 (logout 전용) */
    public Long getUserIdIgnoreExpiry(String token) {
        try {
            return Long.valueOf(getClaims(token).getSubject());
        } catch (ExpiredJwtException e) {
            return Long.valueOf(e.getClaims().getSubject());
        }
    }

    /** 만료된 토큰이면 0을 반환 */
    public long getRemainingMillis(String accessToken) {
        try {
            Claims claims = getClaims(accessToken);
            return claims.getExpiration().getTime() - System.currentTimeMillis();
        } catch (ExpiredJwtException e) {
            return 0L;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .getBody();
    }
}

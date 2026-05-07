package com.ansj.shopuser.user.service;

import com.ansj.shopuser.user.controller.TokenResponse;
import com.ansj.shopuser.user.dto.LoginResponse;
import com.ansj.shopuser.user.entity.UserEntity;
import com.ansj.shopuser.user.model.User;
import com.ansj.shopuser.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@RequiredArgsConstructor
@Service
public class LoginService {

    private final BlacklistService blacklistService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public LoginResponse login(String username, String password) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalStateException("아이디를 입력하지 않았습니다.");
        }
        UserEntity userEntity = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if (!passwordEncoder.matches(password, userEntity.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        String accessToken = jwtProvider.createAccessToken(
            userEntity.getUserId(), userEntity.getRole().getRoleName());
        String refreshToken = jwtProvider.createRefreshToken(userEntity.getUserId());

        refreshTokenService.save(userEntity.getUserId(), refreshToken,
            JwtProvider.REFRESH_TOKEN_EXPIRY_MILLIS);

        return new LoginResponse(accessToken, refreshToken, User.of(userEntity));
    }

    @Transactional
    public TokenResponse reissue(String refreshToken) {
        Long userId;
        try {
            userId = jwtProvider.getUserId(refreshToken);
        } catch (JwtException e) {
            throw new IllegalArgumentException("유효하지 않은 리프레시 토큰입니다.");
        }

        String stored = refreshTokenService.get(userId);
        if (!refreshToken.equals(stored)) {
            throw new IllegalArgumentException("유효하지 않은 리프레시 토큰입니다.");
        }

        UserEntity userEntity = userRepository.findByIdFetching(userId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        String newAccessToken = jwtProvider.createAccessToken(userId, userEntity.getRole().getRoleName());
        String newRefreshToken = jwtProvider.createRefreshToken(userId);
        refreshTokenService.save(userId, newRefreshToken, JwtProvider.REFRESH_TOKEN_EXPIRY_MILLIS);

        return TokenResponse.of(newAccessToken, newRefreshToken);
    }

    public void logout(String accessToken) {
        Long userId = jwtProvider.getUserIdIgnoreExpiry(accessToken);
        refreshTokenService.delete(userId);
        blacklistService.save(accessToken);
    }
}

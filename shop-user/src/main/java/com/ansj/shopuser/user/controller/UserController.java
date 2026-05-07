package com.ansj.shopuser.user.controller;

import com.ansj.shopuser.user.dto.LoginRequest;
import com.ansj.shopuser.user.dto.LoginResponse;
import com.ansj.shopuser.user.dto.SignUpRequest;
import com.ansj.shopuser.user.model.User;
import com.ansj.shopuser.user.service.LoginService;
import com.ansj.shopuser.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/users")
@RestController
public class UserController {

    private final UserService userService;
    private final LoginService loginService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/signup")
    public User signUp(@RequestBody SignUpRequest signUpRequest) {
        return userService.signUp(signUpRequest);
    }

    @GetMapping("/me")
    public User me(@RequestHeader("X-User-Id") Long userId) {
        return userService.getUser(userId);
    }

    @PostMapping("/validate-username")
    public void validateUsername(@RequestParam("username") String username) {
        userService.validateUsernameAvailable(username);
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest loginRequest) {
        return loginService.login(loginRequest.username(), loginRequest.password());
    }

    @PostMapping("/reissue")
    public TokenResponse reissue(@RequestBody RefreshRequest refreshRequest) {
        return loginService.reissue(refreshRequest.getRefreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader HttpHeaders httpHeaders) {
        String authHeader = httpHeaders.getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String accessToken = authHeader.substring(7);
        loginService.logout(accessToken);
        return ResponseEntity.noContent().build();
    }
}

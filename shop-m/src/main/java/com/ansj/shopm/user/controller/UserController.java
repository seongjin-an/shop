package com.ansj.shopm.user.controller;

import com.ansj.shopm.common.CustomUserDetails;
import com.ansj.shopm.user.dto.SignUpRequest;
import com.ansj.shopm.user.model.User;
import com.ansj.shopm.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/api/users")
@RestController
public class UserController {

    private final UserService userService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/signup")
    public User signUp(@RequestBody SignUpRequest signUpRequest) {
        return userService.signUp(signUpRequest);
    }

    @GetMapping("/me")
    public User me(@AuthenticationPrincipal CustomUserDetails user) {
        return User.of(user);
    }

    @PostMapping("/validate-username")
    public void validateUsername(@RequestParam("username") String username) {
        userService.validateUsernameAvailable(username);
    }
}

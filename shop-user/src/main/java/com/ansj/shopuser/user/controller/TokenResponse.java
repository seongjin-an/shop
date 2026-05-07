package com.ansj.shopuser.user.controller;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor(staticName = "of")
@Data
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
}

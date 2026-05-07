package com.ansj.shopuser.user.dto;

import com.ansj.shopuser.user.model.User;

public record LoginResponse(String accessToken, String refreshToken, User user) {}

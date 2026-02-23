package com.ansj.shopuser.user.dto;

import com.ansj.shopuser.user.entity.RoleEntity;
import com.ansj.shopuser.user.entity.UserEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SignUpRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
    @NotBlank
    private String passwordConfirm;
    @NotBlank
    private String fullName;
    private String email;
    private String roleName;

    public UserEntity toEntity(String encodedPassword, RoleEntity roleEntity) {
        return UserEntity.builder()
                .username(username)
                .password(encodedPassword)
                .fullName(fullName)
                .email(email)
                .role(roleEntity)
                .build();
    }
}

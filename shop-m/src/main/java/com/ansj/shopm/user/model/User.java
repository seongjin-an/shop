package com.ansj.shopm.user.model;

import com.ansj.shopm.common.CustomUserDetails;
import com.ansj.shopm.user.entity.UserEntity;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class User {
    private final Long userId;
    private final String username;
    private final String fullName;
    private final String email;
    private final List<String> authorities;

    private User(Long userId, String username, String fullName, String email, List<String> authorities) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.authorities = authorities;
    }

    public static User of(UserEntity userEntity) {
        return new User(userEntity.getUserId(), userEntity.getUsername(), userEntity.getFullName(),
                userEntity.getEmail(), List.of(userEntity.getRole().getRoleName()));
    }

    public static User of(CustomUserDetails userDetails) {

        List<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();
        return new User(userDetails.getUserId(), userDetails.getUsername(), userDetails.getFullName(),
                userDetails.getEmail(), authorities);
    }
}

package com.ansj.shopuser.common;

import com.ansj.shopuser.user.entity.UserEntity;
import com.ansj.shopuser.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // 내가 호출하는 것이 아닌, 시큐리티가, 프레임워크가 호출하는 메서드임.
    @Override
    public CustomUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity userEntity = userRepository.findByUsernameWithRole(username)
                .orElseThrow(() -> {
                    log.error("User not found: {}", username);
                    return new UsernameNotFoundException("User not found: " + username);
                });
        String roleName = userEntity.getRole().getRoleName();
        return new CustomUserDetails(userEntity.getUserId(), userEntity.getUsername(), userEntity.getPassword(),
                userEntity.getFullName(), userEntity.getEmail(), List.of(new SimpleGrantedAuthority(roleName)));
    }
}


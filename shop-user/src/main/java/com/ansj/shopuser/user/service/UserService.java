package com.ansj.shopuser.user.service;

import com.ansj.shopuser.user.dto.SignUpRequest;
import com.ansj.shopuser.user.entity.RoleEntity;
import com.ansj.shopuser.user.entity.UserEntity;
import com.ansj.shopuser.user.model.User;
import com.ansj.shopuser.user.repository.RoleRepository;
import com.ansj.shopuser.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    private final PasswordEncoder passwordEncoder;

    public User signUp(SignUpRequest signUpRequest) {
        if (!signUpRequest.getPassword().equals(signUpRequest.getPasswordConfirm())) {
            throw new IllegalStateException("비밀번호가 일치하지 않습니다.");
        }
        String encodedPassword = passwordEncoder.encode(signUpRequest.getPassword());
        String reqRoleName = signUpRequest.getRoleName();
        String roleName = StringUtils.hasText(reqRoleName) ? reqRoleName : "ROLE_USER";
        RoleEntity roleEntity = roleRepository.findByRoleName(roleName).orElseThrow(IllegalArgumentException::new);

        UserEntity entity = signUpRequest.toEntity(encodedPassword, roleEntity);
        return User.of(userRepository.save(entity));
    }

    public User getUser(Long userId) {
        UserEntity userEntity = userRepository.findByIdFetching(userId).orElseThrow(() -> new IllegalArgumentException("잘못된 요청입니다."));
        return User.of(userEntity);
    }

    public void validateUsernameAvailable(String username) {
        boolean exists = userRepository.existsByUsername(username);
        if (exists) throw new IllegalArgumentException("사용할 수 없는 아이디입니다.");
    }
}

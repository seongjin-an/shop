package com.ansj.shopuser.common;

import com.ansj.shopuser.user.dto.SignUpRequest;
import com.ansj.shopuser.user.entity.RoleEntity;
import com.ansj.shopuser.user.repository.RoleRepository;
import com.ansj.shopuser.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ApplicationInitializer implements ApplicationRunner {

    private static final List<String> ROLES = List.of("ROLE_ADMIN", "ROLE_SELLER", "ROLE_USER");

    private final UserService userService;

    private final RoleRepository roleRepository;

    /* admin user 가 없으면 생성! */
    private void initAdminUser() {
        try {
            userService.validateUsernameAvailable("admin");

            SignUpRequest signUpRequest = new SignUpRequest();
            signUpRequest.setUsername("admin");
            signUpRequest.setPassword("admin");
            signUpRequest.setPasswordConfirm("admin");
            signUpRequest.setFullName("admin");
            signUpRequest.setEmail("admin@admin.com");
            signUpRequest.setRoleName("ROLE_ADMIN");
            userService.signUp(signUpRequest);
        } catch (IllegalArgumentException ignored) {}
    }

    private void initRole() {
        List<RoleEntity> roleEntities = ROLES.stream()
                .filter(roleName -> !roleRepository.existsByRoleName(roleName))
                .map(RoleEntity::of)
                .toList();

        if (!roleEntities.isEmpty()) {
            roleRepository.saveAll(roleEntities);
            roleRepository.flush();
        }

    }

    @Override
    public void run(ApplicationArguments args) {
        initRole();
        initAdminUser();
    }
}

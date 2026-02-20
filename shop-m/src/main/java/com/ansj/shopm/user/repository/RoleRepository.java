package com.ansj.shopm.user.repository;

import com.ansj.shopm.user.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {
    Optional<RoleEntity> findByRoleName(String roleName);
    boolean existsByRoleName(String roleName);
}

package com.ansj.shopm.user.repository;

import com.ansj.shopm.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    @Query("SELECT u FROM UserEntity u JOIN FETCH u.role WHERE u.userId = :id")
    Optional<UserEntity> findByIdFetching(Long id);
    boolean existsByUsername(String username);
    Optional<UserEntity> findByUsername(String username);

    @Query("SELECT u FROM UserEntity u JOIN FETCH u.role WHERE u.username = :username")
    Optional<UserEntity> findByUsernameWithRole(String username);
}

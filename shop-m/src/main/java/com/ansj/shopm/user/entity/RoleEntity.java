package com.ansj.shopm.user.entity;

import jakarta.persistence.*;
import lombok.*;

@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Table(name = "role")
@Entity
public class RoleEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long roleId;

    @Column(unique = true, nullable = false)
    private String roleName;

    private RoleEntity(String roleName) {
        this.roleName = roleName;
    }

    public static RoleEntity of(String roleName) {
        return new RoleEntity(roleName);
    }
}

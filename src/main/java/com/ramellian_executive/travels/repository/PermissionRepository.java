package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByPermissionName(String permissionName);

    Optional<Permission> findByPermissionNameIgnoreCase(String permissionName);

    boolean existsByPermissionNameIgnoreCase(String permissionName);
}

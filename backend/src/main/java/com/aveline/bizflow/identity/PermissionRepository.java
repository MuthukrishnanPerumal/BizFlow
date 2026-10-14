package com.aveline.bizflow.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermissionRepository extends JpaRepository<AppPermission, Long> {

    List<AppPermission> findAllByOrderByModuleCodeAscSortOrderAscNameAsc();
}

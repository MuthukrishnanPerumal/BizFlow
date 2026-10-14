package com.aveline.bizflow.identity;

import com.aveline.bizflow.identity.dto.PermissionDtos.PermissionCatalogResponse;
import com.aveline.bizflow.identity.dto.PermissionDtos.PermissionGroupResponse;
import com.aveline.bizflow.identity.dto.RoleDtos.PermissionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class PermissionQueryService {

    private static final List<String> MODULE_ORDER = List.of(
            "PRODUCT",
            "SUPPLIER",
            "CUSTOMER",
            "PURCHASE",
            "SALES",
            "INVENTORY",
            "FINANCE",
            "REPORT",
            "USER",
            "ROLE");

    private static final Map<String, String> MODULE_LABELS = Map.of(
            "PRODUCT", "Products",
            "SUPPLIER", "Suppliers",
            "CUSTOMER", "Customers",
            "PURCHASE", "Purchases",
            "SALES", "Sales",
            "INVENTORY", "Inventory",
            "FINANCE", "Finance",
            "REPORT", "Reports",
            "USER", "Users",
            "ROLE", "Roles");

    private final PermissionRepository permissionRepository;
    private final IdentityMapper identityMapper;

    public PermissionQueryService(PermissionRepository permissionRepository, IdentityMapper identityMapper) {
        this.permissionRepository = permissionRepository;
        this.identityMapper = identityMapper;
    }

    public PermissionCatalogResponse catalog() {
        Map<String, List<PermissionResponse>> grouped = new LinkedHashMap<>();
        for (AppPermission permission : permissionRepository.findAllByOrderByModuleCodeAscSortOrderAscNameAsc()) {
            grouped.computeIfAbsent(permission.getModuleCode(), key -> new ArrayList<>())
                    .add(identityMapper.toPermission(permission));
        }
        List<PermissionGroupResponse> groups = new ArrayList<>();
        for (String module : MODULE_ORDER) {
            List<PermissionResponse> permissions = grouped.remove(module);
            if (permissions != null) {
                groups.add(new PermissionGroupResponse(module, label(module), permissions));
            }
        }
        grouped.forEach((module, permissions) -> groups.add(new PermissionGroupResponse(module, label(module), permissions)));
        return new PermissionCatalogResponse(List.copyOf(groups));
    }

    private String label(String module) {
        return MODULE_LABELS.getOrDefault(module, module);
    }
}

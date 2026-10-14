package com.aveline.bizflow.identity;

import com.aveline.bizflow.audit.AuditOutcome;
import com.aveline.bizflow.audit.AuditRecorder;
import com.aveline.bizflow.common.api.LikePatterns;
import com.aveline.bizflow.common.api.PageRequests;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.common.exception.ConflictException;
import com.aveline.bizflow.common.exception.ResourceNotFoundException;
import com.aveline.bizflow.identity.dto.RoleDtos.CreateRoleRequest;
import com.aveline.bizflow.identity.dto.RoleDtos.RoleResponse;
import com.aveline.bizflow.identity.dto.RoleDtos.UpdateRoleRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class RoleService {

    private static final Set<String> SORTS = Set.of("code", "name", "createdAt");

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final IdentityMapper identityMapper;
    private final AuditRecorder auditRecorder;

    public RoleService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            UserRepository userRepository,
            IdentityMapper identityMapper,
            AuditRecorder auditRecorder) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.identityMapper = identityMapper;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public PageResponse<RoleResponse> list(String search, int page, int size, String sort) {
        Pageable pageable = PageRequests.of(page, size, sort, SORTS, "name");
        Page<AppRole> result = roleRepository.search(LikePatterns.contain(search), pageable);
        List<Long> ids = result.getContent().stream().map(AppRole::getId).toList();
        Map<Long, AppRole> withPermissions = ids.isEmpty()
                ? Map.of()
                : roleRepository.findWithPermissionsByIdIn(ids).stream()
                        .collect(Collectors.toMap(AppRole::getId, Function.identity()));
        List<RoleResponse> items = ids.stream().map(withPermissions::get).map(identityMapper::toRole).toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public RoleResponse get(Long id) {
        return identityMapper.toRole(load(id));
    }

    public RoleResponse create(CreateRoleRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (roleRepository.countCodeIncludingDeleted(code) > 0) {
            throw new ConflictException("code", "A role with this code already exists");
        }
        AppRole role = AppRole.create(code, request.name().trim(), blankToNull(request.description()));
        role.replacePermissions(loadPermissions(request.permissionIds()));
        save(role);
        auditRecorder.record("ROLE_CREATED", "create", AuditOutcome.SUCCESS, "role", String.valueOf(role.getId()), code);
        return identityMapper.toRole(role);
    }

    public RoleResponse update(Long id, UpdateRoleRequest request) {
        AppRole role = load(id);
        if (!Objects.equals(role.getVersion(), request.version())) {
            throw new OptimisticLockingFailureException("The record was updated by someone else");
        }
        role.rename(request.name().trim(), blankToNull(request.description()));
        role.replacePermissions(loadPermissions(request.permissionIds()));
        save(role);
        auditRecorder.record("ROLE_UPDATED", "update", AuditOutcome.SUCCESS, "role", String.valueOf(role.getId()), role.getCode());
        return identityMapper.toRole(role);
    }

    public void delete(Long id) {
        AppRole role = load(id);
        if (role.isSystemRole()) {
            throw new BusinessRuleException("SYSTEM_ROLE", "System roles cannot be deleted");
        }
        if (userRepository.countUsersWithRole(role.getId()) > 0) {
            throw new ConflictException("This role is assigned to one or more users");
        }
        String code = role.getCode();
        role.retire();
        save(role);
        auditRecorder.record("ROLE_DELETED", "delete", AuditOutcome.SUCCESS, "role", String.valueOf(id), code);
    }

    private AppRole load(Long id) {
        return roleRepository.findWithPermissionsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role was not found"));
    }

    private Set<AppPermission> loadPermissions(Set<Long> permissionIds) {
        if (permissionIds.isEmpty()) {
            return new HashSet<>();
        }
        List<AppPermission> found = permissionRepository.findAllById(permissionIds);
        if (found.size() != permissionIds.size()) {
            throw new BusinessRuleException("UNKNOWN_PERMISSION", "One or more permissions do not exist");
        }
        return new HashSet<>(found);
    }

    private void save(AppRole role) {
        try {
            roleRepository.saveAndFlush(role);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("code", "A role with this code already exists");
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

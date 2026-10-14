package com.aveline.bizflow.identity;

import com.aveline.bizflow.audit.AuditOutcome;
import com.aveline.bizflow.audit.AuditRecorder;
import com.aveline.bizflow.common.api.LikePatterns;
import com.aveline.bizflow.common.api.PageRequests;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.common.exception.ConflictException;
import com.aveline.bizflow.common.exception.ResourceNotFoundException;
import com.aveline.bizflow.identity.dto.UserDtos.AssignRolesRequest;
import com.aveline.bizflow.identity.dto.UserDtos.CreateUserRequest;
import com.aveline.bizflow.identity.dto.UserDtos.ResetPasswordRequest;
import com.aveline.bizflow.identity.dto.UserDtos.UpdateUserRequest;
import com.aveline.bizflow.identity.dto.UserDtos.UserResponse;
import com.aveline.bizflow.security.CurrentAccounts;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
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
public class UserService {

    private static final Set<String> SORTS = Set.of("username", "fullName", "email", "status", "createdAt");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityMapper identityMapper;
    private final AuditRecorder auditRecorder;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            IdentityMapper identityMapper,
            AuditRecorder auditRecorder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.identityMapper = identityMapper;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String search, UserStatus status, int page, int size, String sort) {
        Pageable pageable = PageRequests.of(page, size, sort, SORTS, "createdAt");
        Page<AppUser> result = userRepository.search(LikePatterns.contain(search), status, pageable);
        List<Long> ids = result.getContent().stream().map(AppUser::getId).toList();
        Map<Long, AppUser> withRoles = ids.isEmpty()
                ? Map.of()
                : userRepository.findWithRolesByIdIn(ids).stream()
                        .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        List<UserResponse> items = ids.stream().map(withRoles::get).map(identityMapper::toUser).toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return identityMapper.toUser(load(id));
    }

    public UserResponse create(CreateUserRequest request) {
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());
        assertUsernameAvailable(username);
        assertEmailAvailable(email, 0L);
        if (request.password().equalsIgnoreCase(username)) {
            throw new BusinessRuleException("PASSWORD_POLICY", "Password must not match the username");
        }
        Set<AppRole> roles = loadRoles(request.roleIds());
        assertCanGrant(null, roles);
        AppUser user = AppUser.create(
                username,
                request.fullName().trim(),
                email,
                blankToNull(request.phone()),
                passwordEncoder.encode(request.password()),
                request.status());
        user.replaceRoles(roles);
        save(user);
        auditRecorder.record("USER_CREATED", "create", AuditOutcome.SUCCESS, "user", String.valueOf(user.getId()), username);
        return identityMapper.toUser(user);
    }

    public UserResponse update(Long id, UpdateUserRequest request) {
        AppUser user = load(id);
        String email = normalizeEmail(request.email());
        Set<AppRole> roles = loadRoles(request.roleIds());
        assertCanManage(user);
        assertCanGrant(user, roles);
        assertOwnStatus(user, request.status());
        assertSuperAdminCoverage(user, roles, request.status());
        assertVersion(user, request.version());
        assertEmailAvailable(email, user.getId());
        user.updateProfile(request.fullName().trim(), email, blankToNull(request.phone()), request.status());
        user.replaceRoles(roles);
        if (request.password() != null && !request.password().isBlank()) {
            if (request.password().equalsIgnoreCase(user.getUsername())) {
                throw new BusinessRuleException("PASSWORD_POLICY", "Password must not match the username");
            }
            user.changePassword(passwordEncoder.encode(request.password()), true);
        }
        save(user);
        auditRecorder.record("USER_UPDATED", "update", AuditOutcome.SUCCESS, "user", String.valueOf(user.getId()), user.getUsername());
        return identityMapper.toUser(user);
    }

    public UserResponse assignRoles(Long id, AssignRolesRequest request) {
        AppUser user = load(id);
        Set<AppRole> roles = loadRoles(request.roleIds());
        assertCanManage(user);
        assertCanGrant(user, roles);
        assertSuperAdminCoverage(user, roles, user.getStatus());
        assertVersion(user, request.version());
        user.replaceRoles(roles);
        save(user);
        auditRecorder.record(
                "USER_ROLES_UPDATED",
                "assign-roles",
                AuditOutcome.SUCCESS,
                "user",
                String.valueOf(user.getId()),
                user.getUsername());
        return identityMapper.toUser(user);
    }

    public UserResponse resetPassword(Long id, ResetPasswordRequest request) {
        AppUser user = load(id);
        assertCanManage(user);
        assertVersion(user, request.version());
        if (request.password().equalsIgnoreCase(user.getUsername())) {
            throw new BusinessRuleException("PASSWORD_POLICY", "Password must not match the username");
        }
        user.changePassword(passwordEncoder.encode(request.password()), true);
        save(user);
        auditRecorder.record(
                "USER_PASSWORD_RESET",
                "reset-password",
                AuditOutcome.SUCCESS,
                "user",
                String.valueOf(user.getId()),
                user.getUsername());
        return identityMapper.toUser(user);
    }

    public void delete(Long id) {
        AppUser user = load(id);
        assertCanManage(user);
        if (user.getUsername().equals(CurrentAccounts.require().username())) {
            throw new BusinessRuleException("SELF_DELETE", "You cannot delete your own account");
        }
        assertSuperAdminCoverage(user, Set.of(), UserStatus.INACTIVE);
        String username = user.getUsername();
        user.retire();
        save(user);
        auditRecorder.record("USER_DELETED", "delete", AuditOutcome.SUCCESS, "user", String.valueOf(id), username);
    }

    private AppUser load(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User was not found"));
    }

    private void save(AppUser user) {
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("The username or email is already in use");
        }
    }

    private Set<AppRole> loadRoles(Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return new HashSet<>();
        }
        List<AppRole> found = roleRepository.findAllById(roleIds);
        if (found.size() != roleIds.size()) {
            throw new BusinessRuleException("UNKNOWN_ROLE", "One or more roles do not exist");
        }
        return new HashSet<>(found);
    }

    private void assertUsernameAvailable(String username) {
        if (userRepository.countUsernameIncludingDeleted(username) > 0) {
            throw new ConflictException("username", "A user with this username already exists");
        }
    }

    private void assertEmailAvailable(String email, long excludingId) {
        if (userRepository.countEmailExcluding(email, excludingId) > 0) {
            throw new ConflictException("email", "A user with this email already exists");
        }
    }

    private void assertVersion(AppUser user, Long version) {
        if (!Objects.equals(user.getVersion(), version)) {
            throw new OptimisticLockingFailureException("The record was updated by someone else");
        }
    }

    private void assertOwnStatus(AppUser user, UserStatus status) {
        if (user.getUsername().equals(CurrentAccounts.require().username()) && status != UserStatus.ACTIVE) {
            throw new BusinessRuleException("SELF_STATUS", "You cannot deactivate your own account");
        }
    }

    private void assertCanManage(AppUser user) {
        if (hasRole(user, SystemRoleCodes.SUPER_ADMIN) && !CurrentAccounts.hasRole(SystemRoleCodes.SUPER_ADMIN)) {
            throw new BusinessRuleException(
                    "SUPER_ADMIN_REQUIRED",
                    "Only a super administrator can change a super administrator");
        }
    }

    private void assertCanGrant(AppUser current, Set<AppRole> roles) {
        boolean grantsSuperAdmin = roles.stream().anyMatch(role -> SystemRoleCodes.SUPER_ADMIN.equals(role.getCode()));
        boolean alreadySuperAdmin = current != null && hasRole(current, SystemRoleCodes.SUPER_ADMIN);
        if (grantsSuperAdmin && !alreadySuperAdmin && !CurrentAccounts.hasRole(SystemRoleCodes.SUPER_ADMIN)) {
            throw new BusinessRuleException(
                    "SUPER_ADMIN_REQUIRED",
                    "Only a super administrator can assign the super administrator role");
        }
    }

    private void assertSuperAdminCoverage(AppUser target, Set<AppRole> newRoles, UserStatus newStatus) {
        if (!hasRole(target, SystemRoleCodes.SUPER_ADMIN)) {
            return;
        }
        boolean keeps = newStatus == UserStatus.ACTIVE
                && newRoles.stream().anyMatch(role -> SystemRoleCodes.SUPER_ADMIN.equals(role.getCode()));
        if (!keeps && userRepository.countActiveWithRole(SystemRoleCodes.SUPER_ADMIN) <= 1) {
            throw new BusinessRuleException(
                    "LAST_SUPER_ADMIN",
                    "At least one active super administrator is required");
        }
    }

    private boolean hasRole(AppUser user, String code) {
        return user.getRoles().stream().anyMatch(role -> code.equals(role.getCode()));
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

package com.aveline.bizflow.identity;

import com.aveline.bizflow.audit.AuditOutcome;
import com.aveline.bizflow.audit.AuditRecorder;
import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.common.exception.ResourceNotFoundException;
import com.aveline.bizflow.identity.dto.AuthDtos.ChangePasswordRequest;
import com.aveline.bizflow.identity.dto.AuthDtos.LoginRequest;
import com.aveline.bizflow.identity.dto.AuthDtos.LoginResponse;
import com.aveline.bizflow.identity.dto.UserDtos.CurrentUserResponse;
import com.aveline.bizflow.security.AccountPrincipal;
import com.aveline.bizflow.security.AuthMessages;
import com.aveline.bizflow.security.ClientAuthenticationException;
import com.aveline.bizflow.security.JwtProperties;
import com.aveline.bizflow.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class AuthService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$12$TbM4Rz7MPpHn5iwX/uf3C.a8f.06u2vK2TLozGBvCNrPypehiQBp.";

    private final UserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final AuthTransactionService transactions;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final IdentityMapper identityMapper;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    public AuthService(
            UserRepository userRepository,
            RevokedTokenRepository revokedTokenRepository,
            AuthTransactionService transactions,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            IdentityMapper identityMapper,
            AuditRecorder auditRecorder,
            Clock clock) {
        this.userRepository = userRepository;
        this.revokedTokenRepository = revokedTokenRepository;
        this.transactions = transactions;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.identityMapper = identityMapper;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    public LoginResponse login(LoginRequest request) {
        String identifier = request.username().trim().toLowerCase(Locale.ROOT);
        AppUser user = userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .orElse(null);
        boolean passwordMatches = passwordEncoder.matches(
                request.password(),
                user == null ? DUMMY_PASSWORD_HASH : user.getPasswordHash());
        if (user == null || !passwordMatches) {
            if (user != null && user.getStatus() == UserStatus.ACTIVE) {
                boolean locked = transactions.registerFailedLogin(user.getId());
                if (locked) {
                    throw new ClientAuthenticationException(AuthMessages.LOCKED);
                }
            } else {
                auditRecorder.recordAs(
                        actor(user, identifier),
                        "AUTH_LOGIN",
                        "login",
                        AuditOutcome.FAILURE,
                        "user",
                        user == null ? null : String.valueOf(user.getId()),
                        "Invalid credentials");
            }
            throw new ClientAuthenticationException(AuthMessages.INVALID_CREDENTIALS);
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            auditRecorder.recordAs(user.getUsername(), "AUTH_LOGIN", "login", AuditOutcome.FAILURE, "user",
                    String.valueOf(user.getId()), "Account is locked");
            throw new ClientAuthenticationException(AuthMessages.LOCKED);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            auditRecorder.recordAs(user.getUsername(), "AUTH_LOGIN", "login", AuditOutcome.FAILURE, "user",
                    String.valueOf(user.getId()), "Account is inactive");
            throw new ClientAuthenticationException(AuthMessages.INACTIVE);
        }
        return issue(transactions.completeLogin(user.getId()));
    }

    @Transactional
    public void logout(AccountPrincipal principal) {
        revokedTokenRepository.deleteByExpiresAtBefore(clock.instant());
        if (principal.tokenId() != null && !revokedTokenRepository.existsById(principal.tokenId())) {
            revokedTokenRepository.save(RevokedToken.revoke(
                    principal.tokenId(),
                    principal.username(),
                    principal.tokenExpiresAt(),
                    clock.instant()));
        }
        auditRecorder.record(
                "AUTH_LOGOUT",
                "logout",
                AuditOutcome.SUCCESS,
                "user",
                String.valueOf(principal.userId()),
                "Signed out");
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(Long userId) {
        AppUser user = userRepository.findWithAccessById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User was not found"));
        return identityMapper.toCurrentUser(user);
    }

    public LoginResponse changePassword(Long userId, ChangePasswordRequest request) {
        AppUser user = userRepository.findWithAccessById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User was not found"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            auditRecorder.recordAs(
                    user.getUsername(),
                    "AUTH_PASSWORD_CHANGE",
                    "change-password",
                    AuditOutcome.FAILURE,
                    "user",
                    String.valueOf(user.getId()),
                    "Current password was rejected");
            throw new ClientAuthenticationException(AuthMessages.CURRENT_PASSWORD);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException(
                    "PASSWORD_REUSE",
                    "Choose a password that is different from the current password");
        }
        AppUser updated = transactions.applyNewPassword(user.getId(), passwordEncoder.encode(request.newPassword()), false);
        auditRecorder.recordAs(
                updated.getUsername(),
                "AUTH_PASSWORD_CHANGE",
                "change-password",
                AuditOutcome.SUCCESS,
                "user",
                String.valueOf(updated.getId()),
                "Password changed");
        return issue(updated);
    }

    private LoginResponse issue(AppUser user) {
        List<String> roles = user.getRoles().stream().map(AppRole::getCode).sorted().toList();
        List<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(AppPermission::getCode)
                .distinct()
                .sorted()
                .toList();
        String token = jwtService.generateAccessToken(user.getUsername(), roles, permissions, user.getSessionVersion());
        Instant expiresAt = clock.instant().plus(jwtProperties.accessTokenTtl());
        return new LoginResponse("Bearer", token, expiresAt, identityMapper.toCurrentUser(user));
    }

    private String actor(AppUser user, String identifier) {
        if (user != null) {
            return user.getUsername();
        }
        return identifier == null || identifier.isBlank() ? "unknown" : identifier;
    }
}

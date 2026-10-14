package com.aveline.bizflow.identity;

import com.aveline.bizflow.audit.AuditOutcome;
import com.aveline.bizflow.audit.AuditRecorder;
import com.aveline.bizflow.common.exception.ResourceNotFoundException;
import com.aveline.bizflow.security.AuthMessages;
import com.aveline.bizflow.security.ClientAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class AuthTransactionService {

    private final UserRepository userRepository;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    public AuthTransactionService(UserRepository userRepository, AuditRecorder auditRecorder, Clock clock) {
        this.userRepository = userRepository;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    @Transactional
    public AppUser completeLogin(Long userId) {
        AppUser user = userRepository.findWithAccessById(userId)
                .orElseThrow(() -> new ClientAuthenticationException(AuthMessages.REQUIRED));
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new ClientAuthenticationException(AuthMessages.LOCKED);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ClientAuthenticationException(AuthMessages.INACTIVE);
        }
        user.recordSuccessfulLogin(clock.instant());
        userRepository.save(user);
        auditRecorder.recordAs(
                user.getUsername(),
                "AUTH_LOGIN",
                "login",
                AuditOutcome.SUCCESS,
                "user",
                String.valueOf(user.getId()),
                "Signed in");
        return user;
    }

    @Transactional
    public boolean registerFailedLogin(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User was not found"));
        boolean locked = user.registerFailedLogin(PasswordRules.LOCK_THRESHOLD);
        userRepository.save(user);
        auditRecorder.recordAs(
                user.getUsername(),
                "AUTH_LOGIN",
                "login",
                AuditOutcome.FAILURE,
                "user",
                String.valueOf(user.getId()),
                locked ? "Account locked after repeated failures" : "Invalid credentials");
        return locked;
    }

    @Transactional
    public AppUser applyNewPassword(Long userId, String passwordHash, boolean mustChangePassword) {
        AppUser user = userRepository.findWithAccessById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User was not found"));
        user.changePassword(passwordHash, mustChangePassword);
        userRepository.save(user);
        return user;
    }
}

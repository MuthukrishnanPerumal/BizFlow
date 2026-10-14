package com.aveline.bizflow.identity;

import com.aveline.bizflow.security.AccountPrincipal;
import com.aveline.bizflow.security.AuthMessages;
import com.aveline.bizflow.security.AuthenticatedAccount;
import com.aveline.bizflow.security.AuthenticatedAccountSource;
import com.aveline.bizflow.security.ClientAuthenticationException;
import com.aveline.bizflow.security.JwtPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class DatabaseAccountSource implements AuthenticatedAccountSource {

    private final UserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;

    public DatabaseAccountSource(UserRepository userRepository, RevokedTokenRepository revokedTokenRepository) {
        this.userRepository = userRepository;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedAccount load(JwtPrincipal principal) {
        if (principal.tokenId() != null && revokedTokenRepository.existsById(principal.tokenId())) {
            throw new ClientAuthenticationException(AuthMessages.SESSION_ENDED);
        }
        AppUser user = userRepository.findWithAccessByUsername(principal.username())
                .orElseThrow(() -> new ClientAuthenticationException(AuthMessages.REQUIRED));
        if (user.getSessionVersion() != principal.sessionVersion()) {
            throw new ClientAuthenticationException(AuthMessages.SESSION_ENDED);
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new ClientAuthenticationException(AuthMessages.LOCKED);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ClientAuthenticationException(AuthMessages.INACTIVE);
        }
        AccountPrincipal account = new AccountPrincipal(
                user.getId(),
                user.getUsername(),
                principal.tokenId(),
                principal.expiresAt(),
                user.isMustChangePassword());
        return new AuthenticatedAccount(account, authorities(user));
    }

    private List<GrantedAuthority> authorities(AppUser user) {
        Set<String> codes = new LinkedHashSet<>();
        for (AppRole role : user.getRoles()) {
            codes.add("ROLE_" + role.getCode());
            for (AppPermission permission : role.getPermissions()) {
                codes.add(permission.getCode());
            }
        }
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String code : codes) {
            authorities.add(new SimpleGrantedAuthority(code));
        }
        return List.copyOf(authorities);
    }
}

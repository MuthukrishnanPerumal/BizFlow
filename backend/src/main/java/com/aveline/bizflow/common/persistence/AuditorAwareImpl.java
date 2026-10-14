package com.aveline.bizflow.common.persistence;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public class AuditorAwareImpl implements AuditorAware<String> {

    static final String SYSTEM_ACTOR = "system";

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.of(SYSTEM_ACTOR);
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return Optional.of(SYSTEM_ACTOR);
        }
        return Optional.of(name.length() > 128 ? name.substring(0, 128) : name);
    }
}

package com.aveline.bizflow.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentAccounts {

    private CurrentAccounts() {
    }

    public static AccountPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            return principal;
        }
        throw new ClientAuthenticationException(AuthMessages.REQUIRED);
    }

    public static boolean hasRole(String roleCode) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || roleCode == null || roleCode.isBlank()) {
            return false;
        }
        String authority = roleCode.startsWith("ROLE_") ? roleCode : "ROLE_" + roleCode;
        return authentication.getAuthorities().stream()
                .anyMatch(item -> authority.equals(item.getAuthority()));
    }
}

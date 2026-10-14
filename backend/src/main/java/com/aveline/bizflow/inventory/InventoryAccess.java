package com.aveline.bizflow.inventory;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

final class InventoryAccess {

    private InventoryAccess() {
    }

    static void require(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Authentication is required");
        }
        boolean allowed = authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
        if (!allowed) {
            throw new AccessDeniedException("You do not have permission for this inventory action");
        }
    }
}

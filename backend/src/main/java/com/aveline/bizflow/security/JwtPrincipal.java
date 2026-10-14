package com.aveline.bizflow.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record JwtPrincipal(
        String username,
        List<String> roles,
        List<String> permissions,
        String tokenId,
        int sessionVersion,
        Instant expiresAt
) implements UserDetails {

    public JwtPrincipal {
        roles = roles == null ? List.of() : List.copyOf(roles);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<String> authorities = new LinkedHashSet<>();
        for (String role : roles) {
            if (role != null && !role.isBlank()) {
                authorities.add(role.startsWith("ROLE_") ? role : "ROLE_" + role);
            }
        }
        for (String permission : permissions) {
            if (permission != null && !permission.isBlank()) {
                authorities.add(permission);
            }
        }
        List<GrantedAuthority> granted = new ArrayList<>();
        for (String authority : authorities) {
            granted.add(new SimpleGrantedAuthority(authority));
        }
        return List.copyOf(granted);
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}

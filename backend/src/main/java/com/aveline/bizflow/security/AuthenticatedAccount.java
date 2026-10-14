package com.aveline.bizflow.security;

import org.springframework.security.core.GrantedAuthority;

import java.util.List;

public record AuthenticatedAccount(AccountPrincipal principal, List<GrantedAuthority> authorities) {
}

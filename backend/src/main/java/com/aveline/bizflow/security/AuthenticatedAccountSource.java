package com.aveline.bizflow.security;

public interface AuthenticatedAccountSource {

    AuthenticatedAccount load(JwtPrincipal principal);
}

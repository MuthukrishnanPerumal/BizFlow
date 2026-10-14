package com.aveline.bizflow.security;

import org.springframework.security.core.AuthenticationException;

public class ClientAuthenticationException extends AuthenticationException {

    public ClientAuthenticationException(String message) {
        super(message);
    }
}

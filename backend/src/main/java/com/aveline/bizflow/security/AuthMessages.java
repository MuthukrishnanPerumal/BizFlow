package com.aveline.bizflow.security;

import java.util.Set;

public final class AuthMessages {

    public static final String REQUIRED = "Authentication is required";
    public static final String INVALID_CREDENTIALS = "Invalid username or password";
    public static final String LOCKED = "This account is locked";
    public static final String INACTIVE = "This account is inactive";
    public static final String SESSION_ENDED = "Your session has ended. Sign in again.";
    public static final String CURRENT_PASSWORD = "Current password is incorrect";
    public static final String PASSWORD_CHANGE_REQUIRED = "A password change is required before continuing";

    private static final Set<String> PUBLIC = Set.of(
            REQUIRED,
            INVALID_CREDENTIALS,
            LOCKED,
            INACTIVE,
            SESSION_ENDED,
            CURRENT_PASSWORD,
            PASSWORD_CHANGE_REQUIRED);

    private AuthMessages() {
    }

    public static String publicMessage(String message) {
        if (message != null && PUBLIC.contains(message)) {
            return message;
        }
        return REQUIRED;
    }
}

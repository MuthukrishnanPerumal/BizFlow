package com.aveline.bizflow.identity;

public final class PasswordRules {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 72;
    public static final int LOCK_THRESHOLD = 5;
    public static final String PATTERN =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{10,72}$";
    public static final String MESSAGE =
            "Password must be 10 to 72 characters and include uppercase, lowercase, a number, and a symbol";

    private PasswordRules() {
    }

    public static boolean isAcceptable(String password) {
        return password != null && password.matches(PATTERN);
    }
}

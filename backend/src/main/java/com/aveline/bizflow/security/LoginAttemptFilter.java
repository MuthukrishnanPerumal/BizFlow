package com.aveline.bizflow.security;

import com.aveline.bizflow.common.web.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class LoginAttemptFilter extends OncePerRequestFilter {

    static final String LOGIN_PATH = "/api/v1/auth/login";

    private final LoginThrottle loginThrottle;
    private final ApiErrorWriter apiErrorWriter;

    public LoginAttemptFilter(LoginThrottle loginThrottle, ApiErrorWriter apiErrorWriter) {
        this.loginThrottle = loginThrottle;
        this.apiErrorWriter = apiErrorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !LOGIN_PATH.equals(path(request));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (loginThrottle.allow(clientKey(request))) {
            filterChain.doFilter(request, response);
            return;
        }
        log.warn("Sign-in rate limit exceeded");
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(loginThrottle.retryAfterSeconds()));
        apiErrorWriter.write(response, 429, "Too many sign-in attempts. Try again shortly.");
    }

    private String clientKey(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        if (address == null || address.isBlank()) {
            return "unknown";
        }
        return address.length() <= 64 ? address : address.substring(0, 64);
    }

    private String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            return uri.substring(context.length());
        }
        return uri;
    }
}

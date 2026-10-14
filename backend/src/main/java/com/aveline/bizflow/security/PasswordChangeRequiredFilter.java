package com.aveline.bizflow.security;

import com.aveline.bizflow.common.web.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED = Set.of(
            "/api/v1/auth/me",
            "/api/v1/auth/logout",
            "/api/v1/auth/change-password",
            "/api/v1/system/info",
            "/api/v1/system/health",
            "/api/v1/system/preferences");

    private final ApiErrorWriter apiErrorWriter;

    public PasswordChangeRequiredFilter(ApiErrorWriter apiErrorWriter) {
        this.apiErrorWriter = apiErrorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AccountPrincipal principal)
                || !principal.mustChangePassword()
                || allowed(path(request))) {
            filterChain.doFilter(request, response);
            return;
        }
        apiErrorWriter.write(
                response,
                HttpServletResponse.SC_FORBIDDEN,
                AuthMessages.PASSWORD_CHANGE_REQUIRED);
    }

    private boolean allowed(String path) {
        return ALLOWED.contains(path) || path.startsWith("/actuator/health");
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

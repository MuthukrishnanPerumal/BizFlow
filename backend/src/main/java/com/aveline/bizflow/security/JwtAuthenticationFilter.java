package com.aveline.bizflow.security;

import com.aveline.bizflow.logging.RequestAttributes;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AuthenticatedAccountSource accountSource;
    private final ApiAuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            AuthenticatedAccountSource accountSource,
            ApiAuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtService = jwtService;
        this.accountSource = accountSource;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            request.setAttribute(RequestAttributes.ACTOR, "anonymous");
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            reject(request, response, new ClientAuthenticationException(AuthMessages.REQUIRED));
            return;
        }

        try {
            JwtPrincipal tokenPrincipal = jwtService.parse(token);
            AuthenticatedAccount account = accountSource.load(tokenPrincipal);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    account.principal(),
                    null,
                    account.authorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.setAttribute(RequestAttributes.ACTOR, account.principal().getUsername());
            filterChain.doFilter(request, response);
        } catch (ClientAuthenticationException ex) {
            SecurityContextHolder.clearContext();
            log.warn("Rejected account access");
            reject(request, response, ex);
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            log.warn("Rejected bearer token");
            reject(request, response, new ClientAuthenticationException(AuthMessages.REQUIRED));
        }
    }

    private void reject(
            HttpServletRequest request,
            HttpServletResponse response,
            ClientAuthenticationException exception) throws IOException {
        authenticationEntryPoint.commence(request, response, exception);
    }
}

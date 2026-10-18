package com.aveline.bizflow.logging;

import com.aveline.bizflow.config.PerformanceProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Set<String> QUIET_PATHS = Set.of(
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/api/v1/system/health",
            "/api/v1/system/info");

    private final long slowRequestMs;

    public RequestLoggingFilter(PerformanceProperties performanceProperties) {
        this.slowRequestMs = performanceProperties.slowRequestMs();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            String path = request.getRequestURI();
            Object actor = request.getAttribute(RequestAttributes.ACTOR);
            String actorLabel = actor == null ? "anonymous" : String.valueOf(actor);
            String correlationId = MDC.get(RequestAttributes.CORRELATION_ID);
            if (durationMs >= slowRequestMs && !QUIET_PATHS.contains(path)) {
                log.warn(
                        "slow_http_request method={} path={} status={} durationMs={} thresholdMs={} actor={} correlationId={}",
                        request.getMethod(),
                        path,
                        response.getStatus(),
                        durationMs,
                        slowRequestMs,
                        actorLabel,
                        correlationId);
            } else if (QUIET_PATHS.contains(path)) {
                log.debug(
                        "http_request method={} path={} status={} durationMs={} actor={} correlationId={}",
                        request.getMethod(),
                        path,
                        response.getStatus(),
                        durationMs,
                        actorLabel,
                        correlationId);
            } else {
                log.info(
                        "http_request method={} path={} status={} durationMs={} actor={} correlationId={}",
                        request.getMethod(),
                        path,
                        response.getStatus(),
                        durationMs,
                        actorLabel,
                        correlationId);
            }
        }
    }
}

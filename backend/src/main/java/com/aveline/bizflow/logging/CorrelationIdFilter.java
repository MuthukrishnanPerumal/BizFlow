package com.aveline.bizflow.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request.getHeader(RequestAttributes.CORRELATION_HEADER));
        MDC.put(RequestAttributes.CORRELATION_ID, correlationId);
        response.setHeader(RequestAttributes.CORRELATION_HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RequestAttributes.CORRELATION_ID);
        }
    }

    private String resolveCorrelationId(String header) {
        if (header != null && SAFE_ID.matcher(header).matches()) {
            return header;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}

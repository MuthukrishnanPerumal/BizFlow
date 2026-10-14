package com.aveline.bizflow.security;

import com.aveline.bizflow.common.web.ApiErrorWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorWriter apiErrorWriter;

    public ApiAccessDeniedHandler(ApiErrorWriter apiErrorWriter) {
        this.apiErrorWriter = apiErrorWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        apiErrorWriter.write(
                response,
                HttpStatus.FORBIDDEN.value(),
                "You do not have permission to perform this action");
    }
}

package com.aveline.bizflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.Set;

@Configuration
public class OpenApiConfig {

    private static final Set<String> PUBLIC_OPERATIONS = Set.of(
            "AuthController.login",
            "SystemController.info",
            "SystemController.health",
            "SystemController.preferences");

    @Bean
    public OpenAPI bizflowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Aveline BizFlow API")
                        .version("v1")
                        .description("""
                                Purchase, sales, inventory, and finance API.
                                Sign in with POST /api/v1/auth/login and send Authorization: Bearer <accessToken>.
                                The server loads roles and permissions from the database on every request. A token cannot grant access that the account no longer has.
                                Successful responses use success, message, data, and timestamp. Failures use success, message, errors, and timestamp.
                                Errors never include stack traces, password hashes, or tokens.
                                Public operations are login and GET /api/v1/system/info, /health, and /preferences.
                                """))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .name("Authorization")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token from POST /api/v1/auth/login")));
    }

    @Bean
    public OperationCustomizer apiOperationNotes() {
        return (operation, handlerMethod) -> {
            if (isPublic(handlerMethod)) {
                operation.setSecurity(List.of());
            }
            PreAuthorize access = handlerMethod.getMethodAnnotation(PreAuthorize.class);
            if (access != null) {
                String requirement = "Authorization: " + access.value();
                String description = operation.getDescription();
                operation.setDescription(description == null || description.isBlank()
                        ? requirement
                        : description + " " + requirement);
            }
            addError(operation, "400", "The request failed validation or the body could not be read");
            addError(operation, "401", "Authentication is required or the session has ended");
            addError(operation, "403", "The account does not have permission for this action");
            addError(operation, "404", "The requested resource was not found");
            addError(operation, "409", "The request conflicts with existing data or a newer version");
            addError(operation, "422", "A business rule rejected the request");
            return operation;
        };
    }

    private boolean isPublic(HandlerMethod handlerMethod) {
        String key = handlerMethod.getBeanType().getSimpleName() + "." + handlerMethod.getMethod().getName();
        return PUBLIC_OPERATIONS.contains(key);
    }

    private void addError(io.swagger.v3.oas.models.Operation operation, String status, String description) {
        if (operation.getResponses() == null) {
            operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
        }
        if (operation.getResponses().get(status) == null) {
            operation.getResponses().addApiResponse(
                    status,
                    new io.swagger.v3.oas.models.responses.ApiResponse().description(description));
        }
    }
}

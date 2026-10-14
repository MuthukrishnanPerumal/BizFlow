package com.aveline.bizflow.common.exception;

import com.aveline.bizflow.notification.AppNotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler(mock(AppNotificationService.class)))
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void resourceNotFoundUsesErrorEnvelope() throws Exception {
        mockMvc.perform(get("/probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("The requested resource was not found"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void conflictUsesErrorEnvelope() throws Exception {
        mockMvc.perform(get("/probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("The record already exists"));
    }

    @Test
    void businessRuleUsesUnprocessableEntity() throws Exception {
        mockMvc.perform(get("/probe/rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].code").value("STOCK_LOCKED"))
                .andExpect(jsonPath("$.errors[0].message").value("The stock count is locked"));
    }

    @Test
    void validationReturnsFieldErrorsWithoutRejectedValues() throws Exception {
        mockMvc.perform(post("/probe/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("NotBlank"));
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/probe/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request body is malformed or unreadable"));
    }

    @Test
    void unexpectedErrorsHideInternalDetails() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    if (body.contains("secret") || body.contains("password")) {
                        throw new AssertionError("Response leaked internal exception text");
                    }
                });
    }

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        @GetMapping("/missing")
        void missing() {
            throw new ResourceNotFoundException("The requested resource was not found");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("The record already exists");
        }

        @GetMapping("/rule")
        void rule() {
            throw new BusinessRuleException("STOCK_LOCKED", "The stock count is locked");
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody SampleRequest request) {
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("database password=secret");
        }
    }

    record SampleRequest(@NotBlank String name) {
    }
}

package com.aveline.bizflow.security;

import com.aveline.bizflow.config.ApiDocsProperties;
import com.aveline.bizflow.config.CorsProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final LoginAttemptFilter loginAttemptFilter;
    private final PasswordChangeRequiredFilter passwordChangeRequiredFilter;
    private final ApiAuthenticationEntryPoint authenticationEntryPoint;
    private final ApiAccessDeniedHandler accessDeniedHandler;
    private final CorsProperties corsProperties;
    private final ApiDocsProperties apiDocsProperties;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            LoginAttemptFilter loginAttemptFilter,
            PasswordChangeRequiredFilter passwordChangeRequiredFilter,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler,
            CorsProperties corsProperties,
            ApiDocsProperties apiDocsProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.loginAttemptFilter = loginAttemptFilter;
        this.passwordChangeRequiredFilter = passwordChangeRequiredFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.corsProperties = corsProperties;
        this.apiDocsProperties = apiDocsProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> {
                    headers
                            .frameOptions(frame -> frame.deny())
                            .contentTypeOptions(Customizer.withDefaults())
                            .cacheControl(Customizer.withDefaults())
                            .referrerPolicy(referrer -> referrer.policy(
                                    ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                            .contentSecurityPolicy(csp -> csp.policyDirectives(
                                    "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"));
                    headers.permissionsPolicy(permissions -> permissions.policy(
                            "camera=(), microphone=(), geolocation=(), payment=()"));
                })
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll();
                    if (apiDocsProperties.enabled()) {
                        auth.requestMatchers(
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**").permitAll();
                    }
                    auth.requestMatchers(HttpMethod.GET,
                                    "/api/v1/system/info",
                                    "/api/v1/system/health",
                                    "/api/v1/system/preferences").permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers("/error").permitAll()
                            .anyRequest().authenticated();
                })
                .addFilterBefore(loginAttemptFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(passwordChangeRequiredFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.origins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT,
                "X-Correlation-Id"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public FilterRegistrationBean<LoginAttemptFilter> loginAttemptFilterRegistration(LoginAttemptFilter filter) {
        FilterRegistrationBean<LoginAttemptFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}

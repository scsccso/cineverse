package com.cineverse.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Phase 0 placeholder. No business endpoints exist yet, so everything
 * (including /swagger-ui.html and /v3/api-docs) is left open and CSRF is
 * disabled for a stateless API. Phase 1 (User Management) replaces this
 * with JWT authentication and CUSTOMER/ADMIN authorization rules.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}

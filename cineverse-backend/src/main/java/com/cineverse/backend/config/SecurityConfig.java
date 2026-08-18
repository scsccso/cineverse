package com.cineverse.backend.config;

import com.cineverse.backend.auth.security.JwtAuthenticationFilter;
import com.cineverse.backend.auth.security.RestAccessDeniedHandler;
import com.cineverse.backend.auth.security.RestAuthenticationEntryPoint;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * /api/v1/auth/** (register/login/refresh/logout) is public; everything
 * else requires a valid access token. Sessions are stateless — JWT only.
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;
    private final CorsProperties corsProperties;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            RestAccessDeniedHandler restAccessDeniedHandler,
            CorsProperties corsProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.restAccessDeniedHandler = restAccessDeniedHandler;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/uploads/**", "/images/**").permitAll()
                        // Deploy platform health checks call this with no
                        // Authorization header — only /actuator/health is
                        // exposed at all (see application.yml), so this
                        // permitAll doesn't open up the wider Actuator surface.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Stripe's servers call this directly — no bearer
                        // token to check; the Stripe-Signature header
                        // (verified in PaymentService) is the only gate.
                        .requestMatchers("/api/v1/webhooks/**").permitAll()
                        // A scheduled cron calls this, not a logged-in admin
                        // — DemoResetService.verifySecret's shared-secret
                        // header check is the entire access control (see its
                        // doc comment), same reasoning as the webhook path
                        // above. Never reachable at all unless
                        // DEMO_RESET_SECRET is configured (503 otherwise).
                        .requestMatchers("/internal/demo-reset/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/movies/**", "/api/v1/genres/**",
                                "/api/v1/cinemas/**", "/api/v1/halls/**", "/api/v1/showtimes/**").permitAll()
                        .requestMatchers("/api/v1/movies/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/cinemas/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/showtimes/**").hasRole("ADMIN")
                        // Booking endpoints: any authenticated role (CUSTOMER
                        // or ADMIN) — ownership (own booking vs. someone
                        // else's) is a data-level check in BookingService,
                        // not something this route matcher can express.
                        .requestMatchers("/api/v1/bookings/**").authenticated()
                        // Check-in/redemption — box-office staff only, never customer-facing.
                        .requestMatchers("/api/v1/tickets/**").hasRole("ADMIN")
                        // Sales/occupancy reports (Phase 8) — box-office/management only.
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * The frontend sends credentials: 'include' so the httpOnly refresh
     * cookie gets set/sent cross-origin; that requires an explicit origin
     * allowlist here (allowCredentials + "*" origin is rejected by the
     * CORS spec, and Spring enforces it).
     */
    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Never actually called — JwtAuthenticationFilter builds Authentication
     * straight from the JWT's claims and never asks an AuthenticationManager
     * to authenticate anything. This bean exists only so
     * UserDetailsServiceAutoConfiguration backs off: it's
     * @ConditionalOnMissingBean on {AuthenticationManager,
     * AuthenticationProvider, UserDetailsService,
     * AuthenticationManagerResolver} as a group, and with none of those
     * declared it was silently standing up its own InMemoryUserDetailsManager
     * with a random generated password (the "Using generated security
     * password" log line) — harmless here since nothing routes through it,
     * but misleading in a JWT-only app.
     *
     * <p>Deliberately an AuthenticationManager, not a UserDetailsService:
     * providing a UserDetailsService bean instead would silence Boot's
     * warning but trips a *different*, separate log line from Spring
     * Security's own AuthenticationConfiguration ("Global
     * AuthenticationManager configured with UserDetailsService bean..."),
     * since that machinery activates whenever any UserDetailsService bean is
     * present. An AuthenticationManager bean satisfies Boot's check without
     * ever registering a UserDetailsService, so neither log line fires.
     */
    @Bean
    public AuthenticationManager authenticationManager() {
        return authentication -> {
            throw new AuthenticationServiceException(
                    "Unused: this application authenticates via JWT, not Spring Security's AuthenticationManager");
        };
    }
}

package com.docspot.config;

import com.docspot.security.CustomAccessDeniedHandler;
import com.docspot.security.CustomAuthenticationEntryPoint;
import com.docspot.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // Enables @PreAuthorize / @PostAuthorize on methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter                  jwtAuthFilter;
    private final UserDetailsService             userDetailsService;
    private final CustomAuthenticationEntryPoint authEntryPoint;
    private final CustomAccessDeniedHandler      accessDeniedHandler;

    /**
     * Publicly accessible endpoints — no token required.
     */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/**",          // register, login, forgot/reset password
            "/swagger-ui/**",        // Swagger UI assets
            "/swagger-ui.html",
            "/api-docs/**",          // OpenAPI JSON
            "/v3/api-docs/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // ── CORS ──────────────────────────────────────────────────────────
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // ── Disable CSRF (stateless REST API — using HttpOnly cookies) ────
                .csrf(AbstractHttpConfigurer::disable)

                // ── Stateless session — no HttpSession created or used ────────────
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // ── Route authorization rules ─────────────────────────────────────
                .authorizeHttpRequests(auth -> auth

                        // Permissive check for CORS preflight OPTIONS requests across all routes
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Public — anyone
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()

                        // Admin-only endpoints
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Doctor-only endpoints
                        .requestMatchers("/api/doctor/**").hasRole("DOCTOR")

                        // Patient-only endpoints
                        .requestMatchers("/api/patient/**").hasRole("PATIENT")

                        // Notifications visible to both doctors and patients
                        .requestMatchers("/api/notifications/**").hasAnyRole("DOCTOR", "PATIENT")

                        // Everything else must be authenticated
                        .anyRequest().authenticated()
                )

                // ── Custom JSON error responses (no HTML redirects) ───────────────
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)  // 401 — missing / invalid token
                        .accessDeniedHandler(accessDeniedHandler)  // 403 — valid token, wrong role
                )

                // ── Wire DaoAuthenticationProvider ───────────────────────────────
                .authenticationProvider(authenticationProvider())

                // ── JwtAuthFilter runs BEFORE Spring's username/password filter ───
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * DaoAuthenticationProvider:
     *  - loads user via UserDetailsService
     *  - compares raw password with BCrypt hash using PasswordEncoder
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * CORS config — explicitly enumerates origin origins when credentials (HttpOnly cookies) are allowed.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Exact local frontend origin matches (wildcards combined with allowCredentials are strictly blocked)
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers"));
        config.setExposedHeaders(List.of("Set-Cookie"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // Cache preflight response for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
package com.docspot.config;

import com.docspot.security.CsrfCookieFilter;
import com.docspot.security.CustomAccessDeniedHandler;
import com.docspot.security.CustomAuthenticationEntryPoint;
import com.docspot.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
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

    /** Same property CookieUtil uses. Set app.cookie.secure=false only for local http dev. */
    @Value("${app.cookie.secure:true}")
    private boolean secureCookie;

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

    /**
     * Endpoints that run BEFORE a session exists, so there is nothing for an
     * attacker to ride on. Everything else (including /api/auth/logout and
     * /api/auth/refresh-token) requires a valid X-XSRF-TOKEN header.
     */
    private static final String[] CSRF_EXEMPT_ENDPOINTS = {
            "/api/auth/login",
            "/api/auth/register/**",
            "/api/auth/forgot-password",
            "/api/auth/verify-otp",
            "/api/auth/reset-password",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**",
            "/v3/api-docs/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // ── Double-submit cookie CSRF repository ──────────────────────────────
        // XSRF-TOKEN is readable by JS (HttpOnly=false) on purpose: the frontend
        // copies it into the X-XSRF-TOKEN header. An attacker's site can't read it.
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookiePath("/");
        csrfRepo.setSecure(secureCookie);
        csrfRepo.setCookieCustomizer(c -> c.sameSite("Strict"));

        http
                // ── CORS (only matters for local dev; prod goes through the Vercel proxy) ──
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // ── CSRF: double-submit cookie, plain (non-BREACH) token for SPAs ─
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepo)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(CSRF_EXEMPT_ENDPOINTS)
                )

                // ── Stateless session — no HttpSession created or used ────────────
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // ── Route authorization rules ─────────────────────────────────────
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/doctor/**").hasRole("DOCTOR")
                        .requestMatchers("/api/patient/**").hasRole("PATIENT")
                        .requestMatchers("/api/notifications/**").hasAnyRole("DOCTOR", "PATIENT")
                        .anyRequest().authenticated()
                )

                // ── Custom JSON error responses (no HTML redirects) ───────────────
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)  // 401
                        .accessDeniedHandler(accessDeniedHandler)  // 403 (also used for CSRF failures)
                )

                .authenticationProvider(authenticationProvider())

                // ── Filter order ──────────────────────────────────────────────────
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);

        return http.build();
    }

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

    @Value("${app.security.bcrypt-strength:10}")
    private int bcryptStrength;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(bcryptStrength);
    }

    /**
     * CORS — in production the browser only talks to the Vercel origin (proxy),
     * so this is mainly for local dev and direct API testing.
     * Origins must match exactly: no trailing slash.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "https://docspot-frontend-steel.vercel.app"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin",
                "Access-Control-Request-Method", "Access-Control-Request-Headers",
                "X-XSRF-TOKEN"
        ));
        config.setExposedHeaders(List.of("Set-Cookie"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
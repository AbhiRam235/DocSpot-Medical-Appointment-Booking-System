package com.docspot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request (OncePerRequestFilter).
 *
 * Flow:
 *  1. Extract JWT from HttpOnly Cookie ("accessToken") or fallback to Authorization header
 *  2. Extract email from JWT subject
 *  3. Load UserDetails from DB via UserDetailsService
 *  4. Validate token (signature, expiry, type=ACCESS, email match)
 *  5. Set Authentication in SecurityContext → request is now authenticated
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    /**
     * Bypasses filter execution for CORS OPTIONS preflight requests.
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // ① Extract JWT from HttpOnly cookie first, then fallback to Authorization header
        String jwt = extractJwtFromCookie(request);

        if (jwt == null) {
            jwt = extractJwtFromHeader(request);
        }

        // ② If no token found anywhere, continue down filter chain
        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String email = jwtUtil.extractEmail(jwt);

            // ③ Process authentication if email extracted and context not set
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // ④ Load user details from DB
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                // ⑤ Validate token signature, expiration, and identity
                if (jwtUtil.isAccessTokenValid(jwt, userDetails)) {

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    // ⑥ Store authentication in thread-local SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Authenticated user: {} | Role: {}", email, userDetails.getAuthorities());
                }
            }

        } catch (Exception e) {
            log.debug("JWT filter validation skipped for [{}]: {}", request.getRequestURI(), e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts token from HttpOnly cookie named "accessToken".
     */
    private String extractJwtFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Fallback extractor for Authorization: Bearer <token>
     */
    private String extractJwtFromHeader(HttpServletRequest request) {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
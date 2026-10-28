package com.docspot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * NEW FILE. Spring Security 6 creates the CSRF token lazily, so the
 * XSRF-TOKEN cookie would never be written unless something reads the token.
 * This filter reads it on every request, which forces the cookie to be set.
 *
 * Must be registered AFTER CsrfFilter (see SecurityConfig).
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken(); // forces token resolution -> cookie is written
        }

        filterChain.doFilter(request, response);
    }
}
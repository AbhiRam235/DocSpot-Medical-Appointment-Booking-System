package com.docspot.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds and writes the HttpOnly auth cookies (accessToken, refreshToken)
 * and clears them on logout / failed refresh.
 *
 * NEW FILE — this is the piece that was missing. JwtAuthFilter already
 * reads the access token from a cookie named "accessToken" (see its
 * extractJwtFromCookie method), but nothing was ever setting that cookie —
 * AuthController was only ever returning tokens in the JSON body. This
 * class is what actually puts them where the filter expects to find them.
 *
 * Uses Spring's ResponseCookie (not the servlet Cookie class) because
 * ResponseCookie is the only way to set SameSite from Java — the servlet
 * API has no setter for it.
 */
@Component
@Slf4j
public class CookieUtil {

    public static final String ACCESS_TOKEN_COOKIE  = "accessToken";
    public static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    @Value("${app.jwt.access-token-expiration}")
    private long accessTokenExpirationMs;

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpirationMs;

    /**
     * Add to application.yml — defaults to true (safe). Set false ONLY in a
     * local-dev profile running over plain HTTP; a Secure cookie is
     * silently dropped by the browser over HTTP, which is why this needs
     * to be a real property rather than hardcoded true. Never false in any
     * environment reachable over a real network.
     *   app:
     *     cookie:
     *       secure: true
     */
    @Value("${app.cookie.secure:false}")
    private boolean secureCookies;

    /**
     * Strict is correct as long as frontend and backend are same-site
     * (e.g. the Vite dev proxy in the frontend repo, or a shared parent
     * domain in prod). Your SecurityConfig currently disables CSRF
     * protection entirely — that's only safe *because* these cookies are
     * SameSite=Strict, which stops the browser from attaching them to any
     * cross-site request in the first place, CSRF included. If you ever
     * need SameSite=None (genuinely different sites for frontend/backend),
     * re-enable CSRF protection (e.g. a double-submit token) at the same
     * time — don't ship one without the other.
     */
    @Value("${app.cookie.same-site:Strict}")
    private String sameSite;

    /** Leave blank for a host-only cookie — correct unless frontend and
     *  backend live on different subdomains of one parent domain. */
    @Value("${app.cookie.domain:}")
    private String cookieDomain;

    public void addAccessTokenCookie(HttpServletResponse response, String token) {
        addCookie(response, ACCESS_TOKEN_COOKIE, token, "/", accessTokenExpirationMs / 1000);
    }

    /**
     * Scoped to /api/auth rather than "/" — it's only ever needed by
     * /api/auth/refresh-token and /api/auth/logout, so there's no reason
     * for the browser to attach it to every /api/patient/**, /api/doctor/**
     * etc. request too.
     */
    public void addRefreshTokenCookie(HttpServletResponse response, String token) {
        addCookie(response, REFRESH_TOKEN_COOKIE, token, "/api/auth", refreshTokenExpirationMs / 1000);
    }

    /** Called on logout, and should also be called if you ever want to
     *  force-invalidate a session server-side (e.g. admin-triggered). */
    public void clearAuthCookies(HttpServletResponse response) {
        addCookie(response, ACCESS_TOKEN_COOKIE, "", "/", 0);
        addCookie(response, REFRESH_TOKEN_COOKIE, "", "/api/auth", 0);
    }

    private void addCookie(HttpServletResponse response, String name, String value, String path, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAgeSeconds);

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            builder.domain(cookieDomain);
        }

        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
        log.debug("Set cookie: {} (path={}, maxAge={}s, secure={}, sameSite={})",
                name, path, maxAgeSeconds, secureCookies, sameSite);
    }
}

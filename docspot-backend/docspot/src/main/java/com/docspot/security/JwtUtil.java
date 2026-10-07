package com.docspot.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Component
@Slf4j
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-token-expiration}")
    private long accessTokenExpiration; // 15 min in ms

    // ─── Signing Key ─────────────────────────────────────────────────────────

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ─── Token Generation ─────────────────────────────────────────────────────

    /**
     * Short-lived access token (15 min).
     * Contains: email (subject), userId, role, type=ACCESS
     */
    public String generateAccessToken(UserDetails userDetails, Long userId, String role) {
        return Jwts.builder()
                .setSubject(userDetails.getUsername())   // email
                .claim("userId", userId)
                .claim("role", role)
                .claim("type", "ACCESS")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Short-lived password reset token (15 min).
     * Contains: email (subject), type=PASSWORD_RESET
     * Issued only after OTP is verified — acts as proof of OTP verification.
     */
    public String generatePasswordResetToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .claim("type", "PASSWORD_RESET")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 15 * 60 * 1000L))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Refresh token is a plain UUID stored in the DB.
     * Not a JWT — no expiry info is embedded; expiry is checked against DB record.
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    // ─── Claim Extraction ─────────────────────────────────────────────────────

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    // Throws JwtException subtypes on invalid/expired tokens
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // ─── Token Validation ─────────────────────────────────────────────────────

    /**
     * Called by JwtAuthFilter for every request.
     * Validates: email match, type=ACCESS, not expired, valid signature.
     */
    public boolean isAccessTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = extractAllClaims(token);
            String email = claims.getSubject();
            String type  = claims.get("type", String.class);

            return email.equals(userDetails.getUsername())
                    && "ACCESS".equals(type)
                    && !claims.getExpiration().before(new Date());

        } catch (ExpiredJwtException e) {
            log.debug("Access token expired for request");
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Called in AuthService.resetPassword().
     * Validates signature + expiry, then checks type=PASSWORD_RESET.
     * Throws descriptive exceptions so AuthService can convert them to 400 errors.
     */
    public Claims validateAndExtractResetToken(String token) {
        // Let ExpiredJwtException propagate — AuthService catches it
        Claims claims = extractAllClaims(token);

        String type = claims.get("type", String.class);
        if (!"PASSWORD_RESET".equals(type)) {
            throw new JwtException("Token is not a password reset token");
        }
        return claims;
    }
}
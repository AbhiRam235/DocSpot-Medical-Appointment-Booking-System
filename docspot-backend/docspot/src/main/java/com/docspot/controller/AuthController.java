package com.docspot.controller;

import com.docspot.dto.request.*;
import com.docspot.dto.response.ApiResponse;
import com.docspot.dto.response.AuthResponse;
import com.docspot.exception.BadRequestException;
import com.docspot.security.CookieUtil;
import com.docspot.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * CHANGED from your original:
 *  - login: now takes HttpServletResponse and writes both tokens as
 *    HttpOnly cookies via CookieUtil, instead of just returning them in
 *    the JSON body (AuthResponse still carries them internally, but its
 *    @JsonIgnore fields keep them out of what actually gets serialized).
 *  - refreshToken: no longer takes a @RequestBody RefreshTokenRequest —
 *    reads the refresh token from its HttpOnly cookie via @CookieValue,
 *    and writes the new access token back as a cookie.
 *  - logout: same change — reads the refresh token cookie instead of a
 *    body, and clears both cookies via CookieUtil regardless of whether a
 *    valid refresh token was found (so logout always "works" from the
 *    client's point of view, even if the session had already expired).
 *  - registerPatient/registerDoctor/forgotPassword/verifyOtp/resetPassword
 *    are UNCHANGED — none of them involve the access/refresh cookies.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Register, login, token management, and password reset")
public class AuthController {

    private final AuthService authService;
    private final CookieUtil  cookieUtil;

    // ─── POST /api/auth/register/patient ─────────────────────────────────────

    @PostMapping("/register/patient")
    @Operation(
            summary     = "Register a new patient",
            description = "Creates a patient account with instant access. No approval required."
    )
    public ResponseEntity<ApiResponse<String>> registerPatient(
            @Valid @RequestBody PatientRegisterRequest request) {

        String message = authService.registerPatient(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(message));
    }

    // ─── POST /api/auth/register/doctor ──────────────────────────────────────

    @PostMapping("/register/doctor")
    @Operation(
            summary     = "Register a new doctor",
            description = """
            Submits a doctor application. Status is PENDING until admin approves.
            Doctor cannot log in until approved.
            Admin is notified via email automatically.
            """
    )
    public ResponseEntity<ApiResponse<String>> registerDoctor(
            @Valid @RequestBody DoctorRegisterRequest request) {

        String message = authService.registerDoctor(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(message));
    }

    // ─── POST /api/auth/login ─────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(
            summary     = "Login — all roles",
            description = """
            Single login endpoint for ADMIN, DOCTOR, and PATIENT.
            Issues a JWT access token (15 min) and a refresh token (7 days) as
            HttpOnly cookies. Neither token appears in the response body —
            the body only carries role/userId/name/email.
            DOCTOR accounts must be APPROVED before login is allowed.
            """
    )
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {

        AuthResponse authResponse = authService.login(request);

        cookieUtil.addAccessTokenCookie(response, authResponse.getAccessToken());
        cookieUtil.addRefreshTokenCookie(response, authResponse.getRefreshToken());

        return ResponseEntity.ok(ApiResponse.success("Login successful.", authResponse));
    }

    // ─── POST /api/auth/refresh-token ────────────────────────────────────────

    @PostMapping("/refresh-token")
    @Operation(
            summary     = "Refresh access token",
            description = """
            No request body. Reads the refresh token from its HttpOnly cookie
            and issues a new access token cookie in response — the refresh
            token itself is left unchanged.
            Returns 400 if the refresh token cookie is missing, invalid,
            expired, or revoked.
            """
    )
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @CookieValue(name = "refreshToken", required = false) String refreshTokenCookie,
            HttpServletResponse response) {

        if (refreshTokenCookie == null || refreshTokenCookie.isBlank()) {
            throw new BadRequestException("No refresh token found. Please log in again.");
        }

        AuthResponse authResponse = authService.refreshToken(refreshTokenCookie);

        cookieUtil.addAccessTokenCookie(response, authResponse.getAccessToken());

        return ResponseEntity.ok(ApiResponse.success("Token refreshed.", authResponse));
    }

    // ─── POST /api/auth/logout ────────────────────────────────────────────────

    @PostMapping("/logout")
    // CHANGED: dropped @PreAuthorize("isAuthenticated()") from your original.
    // This endpoint's job only requires knowledge of the refreshToken
    // cookie, not a currently-valid access token — and access tokens expire
    // in 15 minutes, so requiring one made logout fail with a confusing 403
    // any time someone left a tab open past that window and then clicked
    // logout. authService.logout() already handles a null/invalid token
    // gracefully, and the cookies get cleared either way.
    @Operation(
            summary     = "Logout",
            description = """
            No request body. Revokes the refresh token (read from its HttpOnly
            cookie, if present) in the database and clears both auth cookies.
            """
    )
    public ResponseEntity<ApiResponse<String>> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshTokenCookie,
            HttpServletResponse response) {

        String message = authService.logout(refreshTokenCookie);
        cookieUtil.clearAuthCookies(response);

        return ResponseEntity.ok(ApiResponse.success(message));
    }

    // ─── POST /api/auth/forgot-password ──────────────────────────────────────

    @PostMapping("/forgot-password")
    @Operation(
            summary     = "Forgot password — send OTP",
            description = """
            Sends a 6-digit OTP to the provided email if an account exists.
            Always returns a success response (prevents email enumeration).
            OTP is valid for 10 minutes.
            """
    )
    public ResponseEntity<ApiResponse<String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        String message = authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(message));
    }

    // ─── POST /api/auth/verify-otp ───────────────────────────────────────────

    @PostMapping("/verify-otp")
    @Operation(
            summary     = "Verify OTP",
            description = """
            Validates the OTP sent to the user's email.
            On success, returns a short-lived password reset token (opaque
            UUID, 15 min). This reset token is required in the
            /reset-password request.
            """
    )
    public ResponseEntity<ApiResponse<String>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {

        String resetToken = authService.verifyOtp(request);
        return ResponseEntity.ok(
                ApiResponse.success("OTP verified. Use the reset token to set a new password.", resetToken)
        );
    }

    // ─── POST /api/auth/reset-password ───────────────────────────────────────

    @PostMapping("/reset-password")
    @Operation(
            summary     = "Reset password",
            description = """
            Sets a new password using the reset token obtained from /verify-otp.
            Requires newPassword and confirmPassword to match.
            Invalidates any active refresh tokens — user must log in again.
            """
    )
    public ResponseEntity<ApiResponse<String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        String message = authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(message));
    }
}

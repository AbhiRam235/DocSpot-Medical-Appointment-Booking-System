package com.docspot.controller;

import com.docspot.dto.request.*;
import com.docspot.dto.response.ApiResponse;
import com.docspot.dto.response.AuthResponse;
import com.docspot.dto.response.VerifyOtpResponse;
import com.docspot.exception.BadRequestException;
import com.docspot.security.CookieUtil;
import com.docspot.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Only change vs your current version: new GET /api/auth/csrf endpoint.
 * Everything else is identical.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Register, login, token management, and password reset")
public class AuthController {

    private final AuthService authService;
    private final CookieUtil  cookieUtil;

    // ─── GET /api/auth/csrf ───────────────────────────────────────────────────

    @GetMapping("/csrf")
    @Operation(
            summary     = "Bootstrap CSRF cookie",
            description = """
            Returns 204. The response sets the XSRF-TOKEN cookie (via CsrfCookieFilter).
            The frontend calls this once on app load, then echoes the cookie value
            in the X-XSRF-TOKEN header on every POST/PUT/PATCH/DELETE.
            """
    )
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

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
            token itself is left unchanged. Requires the X-XSRF-TOKEN header.
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
    @Operation(
            summary     = "Logout",
            description = """
            No request body. Revokes the refresh token (read from its HttpOnly
            cookie, if present) in the database and clears both auth cookies.
            Requires the X-XSRF-TOKEN header.
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
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);
        String message = authService.forgotPassword(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success(message));
    }

    // ─── POST /api/auth/verify-otp ───────────────────────────────────────────

    @PostMapping("/verify-otp")
    @Operation(
            summary     = "Verify OTP",
            description = "Verifies the submitted 6-digit OTP and returns a password reset token if valid."
    )
    public VerifyOtpResponse verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletRequest httpRequest) {

        return authService.verifyOtp(request, getClientIp(httpRequest));
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
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
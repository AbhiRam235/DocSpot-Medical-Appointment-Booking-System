package com.docspot.service;

import com.docspot.dto.request.*;
import com.docspot.dto.response.AuthResponse;
import com.docspot.entity.*;
import com.docspot.enums.DoctorStatus;
import com.docspot.enums.Role;
import com.docspot.event.DoctorRegisteredEvent;
import com.docspot.exception.BadRequestException;
import com.docspot.repository.*;
import com.docspot.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CHANGED from your original:
 *  - refreshToken(RefreshTokenRequest) -> refreshToken(String refreshTokenValue)
 *    Same logic inside, just reads the token from the plain String the
 *    controller pulled off the cookie instead of req.getRefreshToken().
 *  - logout(RefreshTokenRequest) -> logout(String refreshTokenValue), and
 *    it's now tolerant of a null/blank/unrecognized token (logs and
 *    returns success) instead of throwing — see the method for why.
 *  - Everything else (registerPatient, registerDoctor, login,
 *    forgotPassword, verifyOtp, resetPassword, changePassword, the private
 *    helpers) is UNCHANGED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository              userRepository;
    private final DoctorRepository            doctorRepository;
    private final PatientRepository           patientRepository;
    private final RefreshTokenRepository      refreshTokenRepository;
    private final PasswordResetOtpRepository  otpRepository;
    private final PasswordResetTokenRepository resetTokenRepository;

    private final PasswordEncoder        passwordEncoder;
    private final AuthenticationManager  authenticationManager;
    private final UserDetailsService     userDetailsService;
    private final JwtUtil                jwtUtil;
    private final EmailService              emailService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration; // 7 days in ms

    @Value("${app.admin.email}")
    private String adminEmail;

    // ─── 1. Register Patient ──────────────────────────────────────────────────

    @Transactional
    public String registerPatient(PatientRegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new BadRequestException("An account with this email already exists.");
        }

        User user = User.builder()
                .name(req.getName())
                .email(req.getEmail())
                .mobile(req.getMobile())
                .password(passwordEncoder.encode(req.getPassword()))
                .role(Role.PATIENT)
                .build();
        userRepository.save(user);

        Patient patient = Patient.builder()
                .user(user)
                .dob(req.getDob())
                .bloodGroup(req.getBloodGroup())
                .build();
        patientRepository.save(patient);

        log.info("Patient registered: {}", user.getEmail());
        return "Registration successful. You can now log in.";
    }

    // ─── 2. Register Doctor ───────────────────────────────────────────────────

    @Transactional
    public String registerDoctor(DoctorRegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new BadRequestException("An account with this email already exists.");
        }

        User user = User.builder()
                .name(req.getName())
                .email(req.getEmail())
                .mobile(req.getMobile())
                .password(passwordEncoder.encode(req.getPassword()))
                .role(Role.DOCTOR)
                .build();
        userRepository.save(user);

        Doctor doctor = Doctor.builder()
                .user(user)
                .gender(req.getGender())
                .speciality(req.getSpeciality())
                .qualification(req.getQualification())
                .experienceYears(req.getExperienceYears())
                .consultationFee(req.getConsultationFee())
                .city(req.getCity())
                .bio(req.getBio())
                .licenseNumber(req.getLicenseNumber())
                .status(DoctorStatus.PENDING)
                .build();
        doctorRepository.save(doctor);

        eventPublisher.publishEvent(new DoctorRegisteredEvent(
                user.getId(), user.getName(), user.getEmail(), req.getSpeciality().name()
        ));

        log.info("Doctor registered (PENDING): {}", user.getEmail());
        return "Application submitted successfully. You will be notified once the admin reviews it.";
    }

    // ─── 3. Login ─────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest req) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword())
        );

        User user = userRepository.findByEmailAndDeletedFalse(req.getEmail())
                .orElseThrow(() -> new BadRequestException("Account not found or has been deactivated."));

        if (user.getRole() == Role.DOCTOR) {
            Doctor doctor = doctorRepository.findByUser_Email(user.getEmail())
                    .orElseThrow(() -> new BadRequestException("Doctor profile not found."));

            if (doctor.getStatus() == DoctorStatus.PENDING) {
                throw new BadRequestException(
                        "Your application is still under review. Please wait for admin approval.");
            }
            if (doctor.getStatus() == DoctorStatus.REJECTED) {
                throw new BadRequestException(
                        "Your application has been rejected. Please contact support.");
            }
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken  = jwtUtil.generateAccessToken(userDetails, user.getId(), user.getRole().name());
        String refreshToken = createOrReplaceRefreshToken(user);

        log.info("Login successful: {} ({})", user.getEmail(), user.getRole());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .role(user.getRole().name())
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .build();
    }

    // ─── 4. Refresh Token ─────────────────────────────────────────────────────

    /**
     * CHANGED: takes the raw refresh token value (pulled from the cookie by
     * the controller) instead of a RefreshTokenRequest DTO. Validation logic
     * is identical to before.
     */
    @Transactional
    public AuthResponse refreshToken(String refreshTokenValue) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token. Please log in again."));

        if (storedToken.isRevoked()) {
            throw new BadRequestException("Refresh token has been revoked. Please log in again.");
        }

        if (storedToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(storedToken);
            throw new BadRequestException("Refresh token has expired. Please log in again.");
        }

        User user = storedToken.getUser();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String newAccessToken = jwtUtil.generateAccessToken(userDetails, user.getId(), user.getRole().name());

        log.info("Access token refreshed for: {}", user.getEmail());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshTokenValue)
                .role(user.getRole().name())
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .build();
    }

    // ─── 5. Logout ────────────────────────────────────────────────────────────

    /**
     * CHANGED: takes the raw refresh token value (may be null — the cookie
     * is @CookieValue(required = false) at the controller) instead of a
     * RefreshTokenRequest DTO, and no longer throws if the token is
     * missing/unrecognized.
     *
     * Why tolerant now: logout's job is "end this session, client-side and
     * server-side, as best you can" — not "prove you had a valid session".
     * A user who is somehow already logged out (double-click, an old tab,
     * a session the access-token cookie alone kept alive past a manual DB
     * cleanup) should still get a clean "logged out" rather than a
     * confusing 400. The controller clears both cookies unconditionally
     * right after this returns either way, so the client-side effect is
     * identical; this only changes whether we also try to revoke a
     * matching DB row when one exists.
     */
    @Transactional
    public String logout(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            log.info("Logout called with no refresh token cookie present — nothing to revoke server-side.");
            return "Logged out successfully.";
        }

        refreshTokenRepository.findByToken(refreshTokenValue).ifPresentOrElse(
                storedToken -> {
                    storedToken.setRevoked(true);
                    refreshTokenRepository.save(storedToken);
                    log.info("Logout — refresh token revoked for user id: {}", storedToken.getUser().getId());
                },
                () -> log.info("Logout called with an unrecognized refresh token — nothing to revoke.")
        );

        return "Logged out successfully.";
    }

    // ─── 6. Forgot Password ───────────────────────────────────────────────────

    /**
     * Flow:
     *  → Check if account exists (always return success message to prevent email enumeration)
     *  → Invalidate any previous unused OTPs for this user
     *  → Generate 6-digit OTP, store with 10-min expiry
     *  → Send OTP to email asynchronously
     */
    @Transactional
    public String forgotPassword(ForgotPasswordRequest req) {
        userRepository.findByEmailAndDeletedFalse(req.getEmail()).ifPresent(user -> {
            otpRepository.invalidateAllForUser(user.getId());

            String otp = generateOtp();
            LocalDateTime expiry = LocalDateTime.now().plusMinutes(10);

            PasswordResetOtp resetOtp = PasswordResetOtp.builder()
                    .user(user)
                    .otp(otp)
                    .expiryTime(expiry)
                    .used(false)
                    .build();
            otpRepository.save(resetOtp);

            emailService.sendOtpEmail(user.getEmail(), user.getName(), otp);
            log.info("OTP sent to: {}", user.getEmail());
        });

        return "If an account exists with this email, an OTP has been sent.";
    }

    // ─── 7. Verify OTP ───────────────────────────────────────────────────────

    /**
     * Flow:
     *  → Find the most recent unused OTP for this email
     *  → Validate: matches, not expired
     *  → Mark OTP as used
     *  → Invalidate any leftover reset tokens from earlier attempts
     *  → Issue a fresh, single-use, DB-tracked reset token (opaque UUID, not a JWT)
     *  → Client sends this token in the reset-password request as proof of OTP verification
     */
    @Transactional
    public String verifyOtp(VerifyOtpRequest req) {
        User user = userRepository.findByEmailAndDeletedFalse(req.getEmail())
                .orElseThrow(() -> new BadRequestException("No account found with this email."));

        PasswordResetOtp otpRecord = otpRepository
                .findTopByUser_EmailAndUsedFalseOrderByCreatedAtDesc(req.getEmail())
                .orElseThrow(() -> new BadRequestException("No active OTP found. Please request a new one."));

        if (!otpRecord.getOtp().equals(req.getOtp())) {
            throw new BadRequestException("Incorrect OTP. Please try again.");
        }
        if (otpRecord.getExpiryTime().isBefore(LocalDateTime.now())) {
            otpRecord.setUsed(true);
            otpRepository.save(otpRecord);
            throw new BadRequestException("OTP has expired. Please request a new one.");
        }

        otpRecord.setUsed(true);
        otpRepository.save(otpRecord);

        // Invalidate any leftover unused reset tokens from earlier verify-otp attempts
        resetTokenRepository.invalidateAllForUser(user.getId());

        // Issue a fresh, single-use, DB-tracked reset token
        String tokenValue = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .token(tokenValue)
                .expiryDate(LocalDateTime.now().plusMinutes(15))
                .used(false)
                .build();
        resetTokenRepository.save(resetToken);

        log.info("OTP verified, reset token issued for: {}", user.getEmail());
        return tokenValue;
    }

    // ─── 8. Reset Password ───────────────────────────────────────────────────

    /**
     * Flow:
     *  → Look up the reset token in the DB (opaque UUID, not a JWT)
     *  → Validate: not used, not expired
     *  → Check newPassword == confirmPassword and differs from current password
     *  → Encode and persist new password
     *  → Mark reset token as used (prevents replay)
     *  → Revoke active refresh token (forces re-login on all devices)
     */
    @Transactional
    public String resetPassword(ResetPasswordRequest req) {
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match.");
        }

        PasswordResetToken resetToken = resetTokenRepository.findByToken(req.getResetToken())
                .orElseThrow(() -> new BadRequestException("Invalid reset token. Please request a new OTP."));

        if (resetToken.isUsed()) {
            throw new BadRequestException("This reset link has already been used. Please request a new OTP.");
        }
        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Reset session expired. Please request a new OTP.");
        }

        User user = resetToken.getUser();

        if (passwordEncoder.matches(req.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("New password cannot be the same as your current password.");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        // Mark this specific token used — this is what stops replay within the 15-min window
        resetToken.setUsed(true);
        resetTokenRepository.save(resetToken);

        refreshTokenRepository.findByUser_Id(user.getId()).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });

        log.info("Password reset successful for: {}", user.getEmail());
        return "Password reset successful. Please log in with your new password.";
    }

    // ─── 9. Change Password (logged-in user, any role) ────────────────────────

    /**
     * Flow:
     *  → Caller must already be authenticated (enforced at controller via @PreAuthorize)
     *  → Verify oldPassword matches what's stored
     *  → newPassword must differ from oldPassword and match confirmPassword
     *  → Encode and persist
     *  → Revoke active refresh token — forces re-login on other devices/sessions
     */
    @Transactional
    public String changePassword(String email, ChangePasswordRequest req) {
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match.");
        }

        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new BadRequestException("User not found."));

        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect.");
        }

        if (passwordEncoder.matches(req.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("New password cannot be the same as your current password.");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        refreshTokenRepository.findByUser_Id(user.getId()).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });

        log.info("Password changed for: {} ({})", email, user.getRole());
        return "Password changed successfully. Please log in again.";
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    private String createOrReplaceRefreshToken(User user) {
        String tokenValue = jwtUtil.generateRefreshToken();
        LocalDateTime expiry = LocalDateTime.now()
                .plusSeconds(refreshTokenExpiration / 1000);

        RefreshToken refreshToken = refreshTokenRepository.findByUser_Id(user.getId())
                .map(existing -> {
                    existing.setToken(tokenValue);
                    existing.setExpiryDate(expiry);
                    existing.setRevoked(false);
                    return existing;
                })
                .orElse(RefreshToken.builder()
                        .user(user)
                        .token(tokenValue)
                        .expiryDate(expiry)
                        .revoked(false)
                        .build());

        refreshTokenRepository.save(refreshToken);
        return tokenValue;
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        int otp = 100_000 + random.nextInt(900_000);
        return String.valueOf(otp);
    }
}

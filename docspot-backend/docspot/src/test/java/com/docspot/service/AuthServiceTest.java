package com.docspot.service;

import com.docspot.dto.request.DoctorRegisterRequest;
import com.docspot.dto.request.LoginRequest;
import com.docspot.dto.request.PatientRegisterRequest;
import com.docspot.dto.response.AuthResponse;
import com.docspot.entity.Doctor;
import com.docspot.entity.Patient;
import com.docspot.entity.RefreshToken;
import com.docspot.entity.User;
import com.docspot.enums.DoctorStatus;
import com.docspot.enums.Gender;
import com.docspot.enums.Role;
import com.docspot.enums.Speciality;
import com.docspot.event.DoctorRegisteredEvent;
import com.docspot.exception.BadRequestException;
import com.docspot.repository.*;
import com.docspot.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pure unit test: no Spring context, no DB, no Redis. Every dependency of AuthService is a mock,
 * so we only test AuthService's own decisions (who may register, who may log in, what gets saved).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordResetTokenRepository resetTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserDetailsService userDetailsService;
    @Mock private JwtUtil jwtUtil;
    @Mock private EmailService emailService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private OtpService otpService;

    // A real TransactionTemplate over a fake transaction manager: it just runs the lambda.
    @Spy
    private TransactionTemplate transactionTemplate =
            new TransactionTemplate(mock(PlatformTransactionManager.class));

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        // These two come from @Value in the real app; no Spring here, so set them by hand.
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", 604_800_000L);
        ReflectionTestUtils.setField(authService, "adminEmail", "admin@docspot.com");
    }

    // ───────────────────────── helpers ─────────────────────────

    private User user(Role role) {
        return User.builder().id(1L).name("Test User").email("user@test.com")
                .mobile("9876543210").password("hashed").role(role).build();
    }

    private LoginRequest loginRequest() {
        LoginRequest r = new LoginRequest();
        r.setEmail("user@test.com");
        r.setPassword("secret123");
        return r;
    }

    /** Stubs everything a successful login needs. Only call from tests that reach the end of login(). */
    private void stubSuccessfulLogin(User user) {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(mock(UserDetails.class));
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userRepository.findByEmailAndDeletedFalse(user.getEmail())).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken(any(), eq(user.getId()), eq(user.getRole().name()))).thenReturn("access-jwt");
        when(jwtUtil.generateRefreshToken()).thenReturn("refresh-uuid");
    }

    private void stubDoctorStatus(User user, DoctorStatus status) {
        when(doctorRepository.findByUser_Email(user.getEmail()))
                .thenReturn(Optional.of(Doctor.builder().user(user).status(status).build()));
    }

    // ───────────────────────── registerPatient ─────────────────────────

    @Test
    void registerPatient_success_savesHashedUserAndPatientProfile() {
        PatientRegisterRequest req = new PatientRegisterRequest();
        req.setName("Ravi");
        req.setEmail("ravi@test.com");
        req.setMobile("9876543210");
        req.setPassword("secret123");
        req.setDob(LocalDate.of(1995, 5, 10));
        req.setBloodGroup("O+");
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");

        String message = authService.registerPatient(req);

        assertThat(message).contains("Registration successful");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("hashed");   // raw password is never stored
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.PATIENT);

        ArgumentCaptor<Patient> patientCaptor = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(patientCaptor.capture());
        assertThat(patientCaptor.getValue().getBloodGroup()).isEqualTo("O+");
        assertThat(patientCaptor.getValue().getDob()).isEqualTo(LocalDate.of(1995, 5, 10));
    }

    @Test
    void registerPatient_emailAlreadyExists_throwsAndSavesNothing() {
        PatientRegisterRequest req = new PatientRegisterRequest();
        req.setEmail("ravi@test.com");
        when(userRepository.existsByEmail("ravi@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerPatient(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
        verify(patientRepository, never()).save(any());
    }

    @Test
    void registerPatient_twoRequestsRaceOnSameEmail_secondGetsFriendlyError() {
        PatientRegisterRequest req = new PatientRegisterRequest();
        req.setEmail("ravi@test.com");
        req.setPassword("secret123");
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        // existsByEmail said "free" (default false), but the DB unique constraint fires on save
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> authService.registerPatient(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
    }

    // ───────────────────────── registerDoctor ─────────────────────────

    @Test
    void registerDoctor_success_createsPendingDoctorAndPublishesEvent() {
        DoctorRegisterRequest req = new DoctorRegisterRequest();
        req.setName("Dr. Rao");
        req.setEmail("rao@test.com");
        req.setMobile("9876543210");
        req.setPassword("secret123");
        req.setGender(Gender.values()[0]);
        req.setSpeciality(Speciality.values()[0]);
        req.setQualification("MBBS");
        req.setExperienceYears(5);
        req.setConsultationFee(500.0);
        req.setCity("Hyderabad");
        req.setLicenseNumber("LIC-123");

        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);                       // simulate the DB assigning an id
            return u;
        });

        String message = authService.registerDoctor(req);

        assertThat(message).contains("Application submitted");

        ArgumentCaptor<Doctor> captor = ArgumentCaptor.forClass(Doctor.class);
        verify(doctorRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DoctorStatus.PENDING);   // cannot log in until approved
        assertThat(captor.getValue().getUser().getRole()).isEqualTo(Role.DOCTOR);
        verify(eventPublisher).publishEvent(any(DoctorRegisteredEvent.class));
    }

    @Test
    void registerDoctor_emailAlreadyExists_throwsAndPublishesNothing() {
        DoctorRegisterRequest req = new DoctorRegisterRequest();
        req.setEmail("rao@test.com");
        when(userRepository.existsByEmail("rao@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerDoctor(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");

        verify(doctorRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ───────────────────────── login ─────────────────────────

    @Test
    void login_patient_success_returnsBothTokens() {
        User patient = user(Role.PATIENT);
        stubSuccessfulLogin(patient);

        AuthResponse res = authService.login(loginRequest());

        assertThat(res.getAccessToken()).isEqualTo("access-jwt");
        assertThat(res.getRefreshToken()).isEqualTo("refresh-uuid");
        assertThat(res.getRole()).isEqualTo("PATIENT");
        assertThat(res.getEmail()).isEqualTo("user@test.com");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getToken()).isEqualTo("refresh-uuid");
        assertThat(captor.getValue().getExpiryDate()).isAfter(LocalDateTime.now().plusDays(6));
    }

    @Test
    void login_approvedDoctor_success() {
        User doctor = user(Role.DOCTOR);
        stubDoctorStatus(doctor, DoctorStatus.APPROVED);
        stubSuccessfulLogin(doctor);

        AuthResponse res = authService.login(loginRequest());

        assertThat(res.getRole()).isEqualTo("DOCTOR");
    }

    @Test
    void login_pendingDoctor_isBlocked() {
        User doctor = user(Role.DOCTOR);
        when(userRepository.findByEmailAndDeletedFalse(doctor.getEmail())).thenReturn(Optional.of(doctor));
        stubDoctorStatus(doctor, DoctorStatus.PENDING);

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("under review");

        verify(jwtUtil, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void login_rejectedDoctor_isBlocked() {
        User doctor = user(Role.DOCTOR);
        when(userRepository.findByEmailAndDeletedFalse(doctor.getEmail())).thenReturn(Optional.of(doctor));
        stubDoctorStatus(doctor, DoctorStatus.REJECTED);

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("rejected");
    }

    @Test
    void login_wrongPassword_propagatesAndIssuesNoToken() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(jwtUtil, refreshTokenRepository);
    }

    @Test
    void login_softDeletedUser_isBlocked() {
        // password check passes, but the account was deactivated
        when(userRepository.findByEmailAndDeletedFalse("user@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("deactivated");
    }

    @Test
    void login_existingRefreshToken_isReplacedNotDuplicated() {
        User patient = user(Role.PATIENT);
        stubSuccessfulLogin(patient);
        RefreshToken old = RefreshToken.builder().user(patient).token("old-token")
                .expiryDate(LocalDateTime.now().minusDays(1)).revoked(true).build();
        when(refreshTokenRepository.findByUser_Id(1L)).thenReturn(Optional.of(old));

        authService.login(loginRequest());

        assertThat(old.getToken()).isEqualTo("refresh-uuid");
        assertThat(old.isRevoked()).isFalse();
        verify(refreshTokenRepository).save(old);     // same row updated: one session per user
    }

    // ───────────────────────── logout ─────────────────────────

    @Test
    void logout_nullToken_succeedsWithoutTouchingDb() {
        assertThat(authService.logout(null)).isEqualTo("Logged out successfully.");
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void logout_blankToken_succeedsWithoutTouchingDb() {
        assertThat(authService.logout("   ")).isEqualTo("Logged out successfully.");
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void logout_unknownToken_stillSucceeds() {
        when(refreshTokenRepository.findByToken("ghost")).thenReturn(Optional.empty());

        assertThat(authService.logout("ghost")).isEqualTo("Logged out successfully.");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void logout_knownToken_revokesIt() {
        RefreshToken stored = RefreshToken.builder().user(user(Role.PATIENT)).token("rt-123")
                .expiryDate(LocalDateTime.now().plusDays(1)).revoked(false).build();
        when(refreshTokenRepository.findByToken("rt-123")).thenReturn(Optional.of(stored));

        authService.logout("rt-123");

        assertThat(stored.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(stored);
    }
}
package com.docspot.controller;

import com.docspot.dto.request.DoctorRegisterRequest;
import com.docspot.dto.request.LoginRequest;
import com.docspot.dto.request.PatientRegisterRequest;
import com.docspot.dto.response.AuthResponse;
import com.docspot.enums.Gender;
import com.docspot.enums.Speciality;
import com.docspot.exception.BadRequestException;
import com.docspot.security.CookieUtil;
import com.docspot.security.JwtUtil;
import com.docspot.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer test: loads only AuthController + Spring MVC (validation, JSON, status codes).
 * AuthService is mocked, so no DB / Redis is needed. Security filters are off here because
 * this test is about the controller, not the security rules.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AuthService authService;
    @MockBean private CookieUtil cookieUtil;

    // JwtAuthFilter is a @Component filter, so @WebMvcTest still creates it and needs these two.
    @MockBean private JwtUtil jwtUtil;
    @MockBean private UserDetailsService userDetailsService;

    // ───────────────────────── register patient ─────────────────────────

    @Test
    void registerPatient_validRequest_returns201() throws Exception {
        PatientRegisterRequest req = new PatientRegisterRequest();
        req.setName("Ravi");
        req.setEmail("ravi@test.com");
        req.setMobile("9876543210");
        req.setPassword("secret123");
        when(authService.registerPatient(any())).thenReturn("Registration successful. You can now log in.");

        mockMvc.perform(post("/api/auth/register/patient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void registerPatient_invalidMobile_returns400_andServiceNeverCalled() throws Exception {
        PatientRegisterRequest req = new PatientRegisterRequest();
        req.setName("Ravi");
        req.setEmail("ravi@test.com");
        req.setMobile("12345");                 // fails the ^[6-9][0-9]{9}$ rule
        req.setPassword("secret123");

        mockMvc.perform(post("/api/auth/register/patient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void registerPatient_shortPassword_returns400() throws Exception {
        PatientRegisterRequest req = new PatientRegisterRequest();
        req.setName("Ravi");
        req.setEmail("ravi@test.com");
        req.setMobile("9876543210");
        req.setPassword("123");                 // min 6

        mockMvc.perform(post("/api/auth/register/patient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    // ───────────────────────── register doctor ─────────────────────────

    @Test
    void registerDoctor_validRequest_returns201() throws Exception {
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
        when(authService.registerDoctor(any())).thenReturn("Application submitted successfully.");

        mockMvc.perform(post("/api/auth/register/doctor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void registerDoctor_missingLicenseNumber_returns400() throws Exception {
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
        // licenseNumber deliberately missing

        mockMvc.perform(post("/api/auth/register/doctor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    // ───────────────────────── login ─────────────────────────

    @Test
    void login_success_returns200_andSetsBothCookies() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("ravi@test.com");
        req.setPassword("secret123");
        when(authService.login(any())).thenReturn(AuthResponse.builder()
                .accessToken("access-jwt").refreshToken("refresh-uuid")
                .role("PATIENT").userId(1L).name("Ravi").email("ravi@test.com").build());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(cookieUtil).addAccessTokenCookie(any(HttpServletResponse.class), eq("access-jwt"));
        verify(cookieUtil).addRefreshTokenCookie(any(HttpServletResponse.class), eq("refresh-uuid"));
    }

    @Test
    void login_missingPassword_returns400_andServiceNeverCalled() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ravi@test.com\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void login_serviceRejects_returns400_andSetsNoCookies() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("rao@test.com");
        req.setPassword("secret123");
        when(authService.login(any()))
                .thenThrow(new BadRequestException("Your application is still under review. Please wait for admin approval."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cookieUtil);
    }

    // ───────────────────────── logout ─────────────────────────

    @Test
    void logout_withCookie_passesTokenToService_andClearsCookies() throws Exception {
        when(authService.logout("rt-abc")).thenReturn("Logged out successfully.");

        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("refreshToken", "rt-abc")))
                .andExpect(status().isOk());

        verify(authService).logout("rt-abc");
        verify(cookieUtil).clearAuthCookies(any(HttpServletResponse.class));
    }

    @Test
    void logout_withoutCookie_stillReturns200_andClearsCookies() throws Exception {
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isOk());

        verify(authService).logout(isNull());
        verify(cookieUtil).clearAuthCookies(any(HttpServletResponse.class));
    }
}
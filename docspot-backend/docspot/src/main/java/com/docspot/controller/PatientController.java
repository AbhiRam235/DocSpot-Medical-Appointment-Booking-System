package com.docspot.controller;

import com.docspot.dto.request.BookAppointmentRequest;
import com.docspot.dto.request.ChangePasswordRequest;
import com.docspot.dto.request.UpdatePatientProfileRequest;
import com.docspot.dto.response.*;
import com.docspot.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
@Tag(name = "Patient", description = "Patient-facing endpoints — requires ROLE_PATIENT")
public class PatientController {

    private final PatientService patientService;

    // ─── Profile ─────────────────────────────────────────────────────────────

    @GetMapping("/profile")
    @Operation(summary = "Get own profile")
    public ResponseEntity<ApiResponse<PatientProfileResponse>> getProfile(
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(
                ApiResponse.success("Profile fetched.",
                        patientService.getProfile(userDetails.getUsername()))
        );
    }

    @PutMapping("/profile")
    @Operation(
            summary     = "Update own profile",
            description = "All fields optional — only fields provided in the request are updated."
    )
    public ResponseEntity<ApiResponse<PatientProfileResponse>> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdatePatientProfileRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success("Profile updated.",
                        patientService.updateProfile(userDetails.getUsername(), request))
        );
    }

    @PutMapping("/change-password")
    @Operation(summary = "Change password — requires current password for verification")
    public ResponseEntity<ApiResponse<String>> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        patientService.changePassword(userDetails.getUsername(), request))
        );
    }

    // ─── Doctor Discovery ────────────────────────────────────────────────────

    @GetMapping("/doctors/search")
    @Operation(
            summary     = "Search APPROVED doctors",
            description = """
            All query params are optional — combine any subset to filter.
            
            Filters   : speciality, gender, city, minExperience, maxExperience, minFee, maxFee
            Sort      : sortBy=fee|experience  (default: fee)
            Sort order: sortOrder=asc|desc     (default: asc)
            Pagination: page=0&size=10
            
            Example: /api/patient/doctors/search?speciality=CARDIOLOGIST&city=Mumbai&sortBy=fee&sortOrder=asc
            """
    )
    public ResponseEntity<ApiResponse<Page<DoctorSummaryResponse>>> searchDoctors(
            @RequestParam(required = false) String speciality,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Integer minExperience,
            @RequestParam(required = false) Integer maxExperience,
            @RequestParam(required = false) Double minFee,
            @RequestParam(required = false) Double maxFee,
            @RequestParam(defaultValue = "fee")  String sortBy,
            @RequestParam(defaultValue = "asc")  String sortOrder,
            @RequestParam(defaultValue = "0")    int page,
            @RequestParam(defaultValue = "10")   int size) {

        return ResponseEntity.ok(
                ApiResponse.success("Doctors found.",
                        patientService.searchDoctors(
                                speciality, gender, city,
                                minExperience, maxExperience, minFee, maxFee,
                                sortBy, sortOrder, page, size))
        );
    }

    @GetMapping("/doctors/{doctorId}")
    @Operation(
            summary     = "View a doctor's full public profile",
            description = "Returns bio, qualifications, fee, city. Excludes admin-only fields."
    )
    public ResponseEntity<ApiResponse<DoctorDetailResponse>> getDoctorProfile(
            @PathVariable Long doctorId) {

        return ResponseEntity.ok(
                ApiResponse.success("Doctor profile fetched.",
                        patientService.getDoctorPublicProfile(doctorId))
        );
    }

    @GetMapping("/doctors/{doctorId}/slots")
    @Operation(
            summary     = "Check slot availability for a doctor on a specific date",
            description = """
            Returns all 3 sessions (MORNING / AFTERNOON / EVENING) with:
              - status      : AVAILABLE | FULL | UNAVAILABLE
              - timeRange   : "09:00 AM – 12:00 PM" etc.
              - bookedCount : current bookings in this slot
              - maxPatients : doctor's configured capacity
              - remainingSlots
            
            UNAVAILABLE = doctor doesn't work that session on this day of week.
            
            Example: /api/patient/doctors/5/slots?date=2025-09-15
            """
    )
    public ResponseEntity<ApiResponse<SlotAvailabilityResponse>> getAvailableSlots(
            @PathVariable Long doctorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return ResponseEntity.ok(
                ApiResponse.success("Slots fetched.",
                        patientService.getAvailableSlots(doctorId, date))
        );
    }

    // ─── Appointments ────────────────────────────────────────────────────────

    @PostMapping("/appointments")
    @Operation(
            summary     = "Book an appointment",
            description = """
            Body: { "doctorId": 5, "date": "2025-09-20", "session": "MORNING" }
            
            Validation order:
              1. Doctor must be APPROVED
              2. Date must not be in the past
              3. Doctor must have active availability for that day + session
              4. Patient must not already have a live booking with this doctor on that date
              5. Slot must not be full (bookedCount < maxPatients)  ← @Transactional check
            
            Confirmation emails sent async to both patient and doctor on success.
            """
    )
    public ResponseEntity<ApiResponse<AppointmentResponse>> bookAppointment(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody BookAppointmentRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Appointment booked successfully.",
                        patientService.bookAppointment(userDetails.getUsername(), request)));
    }

    @GetMapping("/appointments")
    @Operation(summary = "Upcoming CONFIRMED appointments — sorted by date ascending")
    public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> getUpcomingAppointments(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 10, sort = "appointmentDate",
                    direction = Sort.Direction.ASC) Pageable pageable) {

        return ResponseEntity.ok(
                ApiResponse.success("Appointments fetched.",
                        patientService.getUpcomingAppointments(userDetails.getUsername(), pageable))
        );
    }

    @GetMapping("/appointments/history")
    @Operation(summary = "Past appointments (COMPLETED + CANCELLED) — sorted by date descending")
    public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> getAppointmentHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 10, sort = "appointmentDate",
                    direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(
                ApiResponse.success("History fetched.",
                        patientService.getAppointmentHistory(userDetails.getUsername(), pageable))
        );
    }

    @PutMapping("/appointments/{appointmentId}/cancel")
    @Operation(
            summary     = "Cancel an appointment",
            description = """
            Rules:
              - Appointment must belong to the logged-in patient
              - Status must be CONFIRMED
              - Current time + 2 hours must be BEFORE the session start:
                  MORNING   → before 09:00 AM on appointment date
                  AFTERNOON → before 12:00 PM on appointment date
                  EVENING   → before 05:00 PM on appointment date
            
            Cancellation emails sent async to both patient and doctor.
            """
    )
    public ResponseEntity<ApiResponse<String>> cancelAppointment(
            @PathVariable Long appointmentId,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        patientService.cancelAppointment(appointmentId, userDetails.getUsername()))
        );
    }
}

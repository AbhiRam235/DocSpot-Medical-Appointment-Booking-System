package com.docspot.controller;

import com.docspot.dto.request.*;
import com.docspot.dto.response.*;
import com.docspot.service.DoctorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/doctor")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DOCTOR')")
@Tag(name = "Doctor", description = "Doctor-facing endpoints — requires ROLE_DOCTOR")
public class DoctorController {

    private final DoctorService doctorService;

    // ─── Profile ─────────────────────────────────────────────────────────────

    @GetMapping("/profile")
    @Operation(summary = "Get own profile — includes documents list")
    public ResponseEntity<ApiResponse<DoctorDetailResponse>> getProfile(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("Profile fetched.",
                doctorService.getProfile(userDetails.getUsername())));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update own profile",
            description = "Updatable: consultationFee, city, bio, qualification. Omitted fields unchanged.")
    public ResponseEntity<ApiResponse<DoctorDetailResponse>> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateDoctorProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Profile updated.",
                doctorService.updateProfile(userDetails.getUsername(), request)));
    }

    @PutMapping("/change-password")
    @Operation(summary = "Change password")
    public ResponseEntity<ApiResponse<String>> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.success(doctorService.changePassword(userDetails.getUsername(), request)));
    }

    // ─── Documents ────────────────────────────────────────────────────────────

    @PostMapping(value = "/documents", consumes = "multipart/form-data")
    @Operation(summary = "Upload a document",
            description = "documentType: DEGREE_CERTIFICATE | MEDICAL_LICENSE | IDENTITY_PROOF | OTHER. "
                    + "PDF/JPG/PNG only, max 5MB.")
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Document uploaded.",
                doctorService.uploadDocument(userDetails.getUsername(), documentType, file)));
    }

    @GetMapping("/documents")
    @Operation(summary = "List own uploaded documents")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getMyDocuments(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("Documents fetched.",
                doctorService.getMyDocuments(userDetails.getUsername())));
    }

    // ─── Availability ─────────────────────────────────────────────────────────

    @PostMapping("/availability")
    @Operation(
            summary = "Add availability window(s)",
            description = """
            Body is a list — each item is ONE bookable time window on ONE day.
            A doctor can have multiple non-overlapping windows on the same day
            by sending multiple entries with the same dayOfWeek.

            Example:
            [
              { "dayOfWeek": "MONDAY", "startTime": "09:00", "endTime": "12:00", "slotDurationMinutes": 30 },
              { "dayOfWeek": "MONDAY", "startTime": "16:00", "endTime": "19:00", "slotDurationMinutes": 30 },
              { "dayOfWeek": "WEDNESDAY", "startTime": "09:00", "endTime": "13:00", "slotDurationMinutes": 20 }
            ]

            This is additive, not an upsert — overlapping windows on the same
            day are rejected with a 400 rather than silently merged.
            """
    )
    public ResponseEntity<ApiResponse<List<AvailabilityResponse>>> setAvailability(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody List<AvailabilityRequest> requests) {
        return ResponseEntity.ok(ApiResponse.success("Availability added.",
                doctorService.setAvailability(userDetails.getUsername(), requests)));
    }

    @GetMapping("/availability")
    @Operation(summary = "Get own weekly availability windows — active only")
    public ResponseEntity<ApiResponse<List<AvailabilityResponse>>> getAvailability(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("Availability fetched.",
                doctorService.getAvailability(userDetails.getUsername())));
    }

    @PutMapping("/availability/{availabilityId}")
    @Operation(
            summary = "Update one availability window",
            description = """
            All fields optional — only sent fields change.

            Changing startTime, endTime, or slotDurationMinutes is BLOCKED
            (400) if you have any CONFIRMED appointment today or later on
            this window's day of week — editing the timing would shift the
            slot grid out from under an already-booked patient. The lock
            clears automatically once that booking's date passes or the
            patient cancels; there's nothing to unlock manually.

            Toggling `active` on its own is never blocked — deactivating
            only stops new bookings, it never touches an existing
            CONFIRMED appointment's own stored time.

            Changing startTime/endTime also re-validates against overlap
            with your other windows on the same day.
            """
    )
    public ResponseEntity<ApiResponse<AvailabilityResponse>> updateAvailability(
            @PathVariable Long availabilityId,
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateAvailabilityRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Availability updated.",
                doctorService.updateAvailability(availabilityId, userDetails.getUsername(), request)));
    }

    @DeleteMapping("/availability/{availabilityId}")
    @Operation(
            summary = "Delete a specific availability window",
            description = "Blocked (400) under the same rule as updating timing — "
                    + "if any CONFIRMED appointment exists today or later on this "
                    + "window's day of week, delete it first by waiting for those "
                    + "bookings to complete, or ask the patient to cancel."
    )
    public ResponseEntity<ApiResponse<String>> deleteAvailability(
            @PathVariable Long availabilityId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                doctorService.deleteAvailability(availabilityId, userDetails.getUsername())));
    }

    // ─── Appointments ────────────────────────────────────────────────────────

    @GetMapping("/appointments")
    @Operation(summary = "Upcoming CONFIRMED appointments, sorted by date ascending")
    public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> getUpcomingAppointments(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 10, sort = "appointmentDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Appointments fetched.",
                doctorService.getUpcomingAppointments(userDetails.getUsername(), pageable)));
    }

    @GetMapping("/appointments/history")
    @Operation(summary = "Past appointments (COMPLETED + CANCELLED), sorted by date descending")
    public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> getAppointmentHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 10, sort = "appointmentDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("History fetched.",
                doctorService.getAppointmentHistory(userDetails.getUsername(), pageable)));
    }

    @PutMapping("/appointments/{appointmentId}/complete")
    @Operation(summary = "Mark an appointment as COMPLETED",
            description = "Must be CONFIRMED and dated today or earlier.")
    public ResponseEntity<ApiResponse<String>> markAsCompleted(
            @PathVariable Long appointmentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                doctorService.markAsCompleted(appointmentId, userDetails.getUsername())));
    }
}

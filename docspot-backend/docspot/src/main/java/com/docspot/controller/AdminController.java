package com.docspot.controller;

import com.docspot.dto.request.ApproveDoctorRequest;
import com.docspot.dto.request.RejectDoctorRequest;
import com.docspot.dto.response.*;
import com.docspot.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Admin-only operations — requires ROLE_ADMIN")
public class AdminController {

    private final AdminService adminService;

    // ─── Dashboard ────────────────────────────────────────────────────────────

    @GetMapping("/dashboard/stats")
    @Operation(summary = "Dashboard summary — total doctors/patients/appointments, pending count")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getDashboardStats() {
        return ResponseEntity.ok(
                ApiResponse.success("Dashboard stats fetched.", adminService.getDashboardStats())
        );
    }

    // ─── Doctor Endpoints ─────────────────────────────────────────────────────

    @GetMapping("/doctors/pending")
    @Operation(summary = "All PENDING doctor applications, paginated")
    public ResponseEntity<ApiResponse<Page<DoctorSummaryResponse>>> getPendingDoctors(
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(
                ApiResponse.success("Pending doctors fetched.", adminService.getPendingDoctors(pageable))
        );
    }

    @GetMapping("/doctors")
    @Operation(
            summary     = "All doctors with optional filters",
            description = "Filter by status (PENDING/APPROVED/REJECTED), speciality, city. "
                    + "Sort by any field via ?sort=consultationFee,asc"
    )
    public ResponseEntity<ApiResponse<Page<DoctorSummaryResponse>>> getAllDoctors(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String speciality,
            @RequestParam(required = false) String city,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(
                ApiResponse.success("Doctors fetched.",
                        adminService.getAllDoctors(status, speciality, city, pageable))
        );
    }

    @GetMapping("/doctors/{doctorId}")
    @Operation(summary = "Full doctor profile including uploaded documents")
    public ResponseEntity<ApiResponse<DoctorDetailResponse>> getDoctorById(
            @PathVariable Long doctorId) {

        return ResponseEntity.ok(
                ApiResponse.success("Doctor fetched.", adminService.getDoctorById(doctorId))
        );
    }

    @PutMapping("/doctors/{doctorId}/approve")
    @Operation(
            summary     = "Approve a doctor",
            description = "Sets status to APPROVED. Optional admin note is emailed to the doctor."
    )
    public ResponseEntity<ApiResponse<String>> approveDoctor(
            @PathVariable Long doctorId,
            @RequestBody ApproveDoctorRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success(adminService.approveDoctor(doctorId, request))
        );
    }

    @PutMapping("/doctors/{doctorId}/reject")
    @Operation(
            summary     = "Reject a doctor",
            description = "Sets status to REJECTED. Reason is mandatory and is emailed to the doctor."
    )
    public ResponseEntity<ApiResponse<String>> rejectDoctor(
            @PathVariable Long doctorId,
            @Valid @RequestBody RejectDoctorRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success(adminService.rejectDoctor(doctorId, request))
        );
    }

    @DeleteMapping("/doctors/{doctorId}")
    @Operation(
            summary     = "Soft delete a doctor",
            description = "Sets user.deleted = true. Doctor disappears from all searches. "
                    + "Data is preserved. Refresh token is revoked (forced logout)."
    )
    public ResponseEntity<ApiResponse<String>> deleteDoctor(@PathVariable Long doctorId) {
        return ResponseEntity.ok(
                ApiResponse.success(adminService.softDeleteDoctor(doctorId))
        );
    }

    // ─── Patient Endpoints ────────────────────────────────────────────────────

    @GetMapping("/patients")
    @Operation(summary = "All active patients, paginated")
    public ResponseEntity<ApiResponse<Page<PatientSummaryResponse>>> getAllPatients(
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(
                ApiResponse.success("Patients fetched.", adminService.getAllPatients(pageable))
        );
    }

    @GetMapping("/patients/{patientId}")
    @Operation(summary = "Single patient profile")
    public ResponseEntity<ApiResponse<PatientSummaryResponse>> getPatientById(
            @PathVariable Long patientId) {

        return ResponseEntity.ok(
                ApiResponse.success("Patient fetched.", adminService.getPatientById(patientId))
        );
    }

    @DeleteMapping("/patients/{patientId}")
    @Operation(summary = "Soft delete a patient")
    public ResponseEntity<ApiResponse<String>> deletePatient(@PathVariable Long patientId) {
        return ResponseEntity.ok(
                ApiResponse.success(adminService.softDeletePatient(patientId))
        );
    }

    // ─── Appointment Endpoint ────────────────────────────────────────────────

    @GetMapping("/appointments")
    @Operation(
            summary     = "All appointments with optional filters",
            description = "Filter by status, date, doctorId, patientId. All params are optional."
    )
    public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> getAllAppointments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long patientId,
            @PageableDefault(size = 10, sort = "appointmentDate",
                    direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(
                ApiResponse.success("Appointments fetched.",
                        adminService.getAllAppointments(status, date, doctorId, patientId, pageable))
        );
    }
}
package com.docspot.service;

import com.docspot.dto.request.ApproveDoctorRequest;
import com.docspot.dto.request.RejectDoctorRequest;
import com.docspot.dto.response.*;
import com.docspot.entity.*;
import com.docspot.enums.AppointmentStatus;
import com.docspot.enums.DoctorStatus;
import com.docspot.enums.Speciality;
import com.docspot.event.DoctorApprovedEvent;
import com.docspot.event.DoctorRejectedEvent;
import com.docspot.exception.BadRequestException;
import com.docspot.exception.ResourceNotFoundException;
import com.docspot.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorDocumentRepository documentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ─── Dashboard Stats ──────────────────────────────────────────────────────

    public DashboardStatsResponse getDashboardStats() {
        return DashboardStatsResponse.builder()
                .totalDoctors(doctorRepository.count())
                .approvedDoctors(doctorRepository.countByStatus(DoctorStatus.APPROVED))
                .pendingDoctors(doctorRepository.countByStatus(DoctorStatus.PENDING))
                .rejectedDoctors(doctorRepository.countByStatus(DoctorStatus.REJECTED))
                .totalPatients(patientRepository.count())
                .totalAppointments(appointmentRepository.count())
                .todayAppointments(appointmentRepository.countTodayAppointments(LocalDate.now()))
                .build();
    }

    // ─── Doctor List Endpoints ────────────────────────────────────────────────

    public Page<DoctorSummaryResponse> getPendingDoctors(Pageable pageable) {
        return doctorRepository.findByStatus(DoctorStatus.PENDING, pageable).map(this::toSummary);
    }

    public Page<DoctorSummaryResponse> getAllDoctors(String status, String speciality, String city, Pageable pageable) {
        DoctorStatus statusEnum = parseEnum(DoctorStatus.class, status);
        Speciality specEnum = parseEnum(Speciality.class, speciality);
        return doctorRepository.findAllByFilters(statusEnum, specEnum, city, pageable).map(this::toSummary);
    }

    public DoctorDetailResponse getDoctorById(Long doctorId) {
        Doctor doctor = findDoctorById(doctorId);
        List<DoctorDocument> docs = documentRepository.findByDoctor_Id(doctorId);
        return toDetail(doctor, docs);
    }

    // ─── Approve / Reject ─────────────────────────────────────────────────────

    @Transactional
    public String approveDoctor(Long doctorId, ApproveDoctorRequest request) {
        Doctor doctor = findDoctorById(doctorId);
        if (doctor.getStatus() == DoctorStatus.APPROVED) {
            throw new BadRequestException("Doctor is already approved.");
        }

        doctor.setStatus(DoctorStatus.APPROVED);
        doctor.setAdminNote(request.getNote());
        doctor.setRejectionReason(null);
        doctorRepository.save(doctor);

        eventPublisher.publishEvent(new DoctorApprovedEvent(
                doctor.getUser().getId(), doctor.getUser().getName(),
                doctor.getUser().getEmail(), request.getNote()
        ));

        log.info("Doctor approved: {} (id={})", doctor.getUser().getEmail(), doctorId);
        return "Doctor approved successfully.";
    }

    @Transactional
    public String rejectDoctor(Long doctorId, RejectDoctorRequest request) {
        Doctor doctor = findDoctorById(doctorId);
        if (doctor.getStatus() == DoctorStatus.REJECTED) {
            throw new BadRequestException("Doctor application is already rejected.");
        }

        doctor.setStatus(DoctorStatus.REJECTED);
        doctor.setRejectionReason(request.getReason());
        doctorRepository.save(doctor);

        eventPublisher.publishEvent(new DoctorRejectedEvent(
                doctor.getUser().getId(), doctor.getUser().getName(),
                doctor.getUser().getEmail(), request.getReason()
        ));

        log.info("Doctor rejected: {} | Reason: {}", doctor.getUser().getEmail(), request.getReason());
        return "Doctor application rejected.";
    }

    // ─── Patient Endpoints ────────────────────────────────────────────────────

    public Page<PatientSummaryResponse> getAllPatients(Pageable pageable) {
        return patientRepository.findByUser_DeletedFalse(pageable).map(this::toPatientSummary);
    }

    public PatientSummaryResponse getPatientById(Long patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
        return toPatientSummary(patient);
    }

    // ─── Appointment Endpoint ────────────────────────────────────────────────

    public Page<AppointmentResponse> getAllAppointments(String status, LocalDate date,
                                                        Long doctorId, Long patientId, Pageable pageable) {
        AppointmentStatus statusEnum = parseEnum(AppointmentStatus.class, status);
        return appointmentRepository.findAllByFilters(statusEnum, date, doctorId, patientId, pageable)
                .map(this::toAppointmentResponse);
    }

    // ─── Soft Delete ─────────────────────────────────────────────────────────

    @Transactional
    public String softDeleteDoctor(Long doctorId) {
        Doctor doctor = findDoctorById(doctorId);
        User user = doctor.getUser();
        if (user.isDeleted()) {
            throw new BadRequestException("Doctor account is already deactivated.");
        }
        user.setDeleted(true);
        userRepository.save(user);

        refreshTokenRepository.findByUser_Id(user.getId()).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });

        log.info("Doctor soft-deleted: {} (id={})", user.getEmail(), doctorId);
        return "Doctor account deactivated successfully.";
    }

    @Transactional
    public String softDeletePatient(Long patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
        User user = patient.getUser();
        if (user.isDeleted()) {
            throw new BadRequestException("Patient account is already deactivated.");
        }
        user.setDeleted(true);
        userRepository.save(user);

        refreshTokenRepository.findByUser_Id(user.getId()).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });

        log.info("Patient soft-deleted: {} (id={})", user.getEmail(), patientId);
        return "Patient account deactivated successfully.";
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private Doctor findDoctorById(Long doctorId) {
        return doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId));
    }

    private DoctorSummaryResponse toSummary(Doctor d) {
        return DoctorSummaryResponse.builder()
                .doctorId(d.getId()).userId(d.getUser().getId())
                .name(d.getUser().getName()).email(d.getUser().getEmail()).mobile(d.getUser().getMobile())
                .gender(d.getGender().name()).speciality(d.getSpeciality().name())
                .qualification(d.getQualification()).experienceYears(d.getExperienceYears())
                .consultationFee(d.getConsultationFee()).city(d.getCity())
                .licenseNumber(d.getLicenseNumber()).status(d.getStatus().name())
                .rejectionReason(d.getRejectionReason()).adminNote(d.getAdminNote())
                .registeredAt(d.getUser().getCreatedAt())
                .build();
    }

    private DoctorDetailResponse toDetail(Doctor d, List<DoctorDocument> docs) {
        List<DocumentResponse> docResponses = docs.stream().map(doc ->
                DocumentResponse.builder()
                        .documentId(doc.getId()).documentType(doc.getDocumentType().name())
                        .originalFileName(doc.getOriginalFileName()).filePath(doc.getFilePath())
                        .uploadedAt(doc.getUploadedAt()).build()
        ).toList();

        return DoctorDetailResponse.builder()
                .doctorId(d.getId()).userId(d.getUser().getId())
                .name(d.getUser().getName()).email(d.getUser().getEmail()).mobile(d.getUser().getMobile())
                .gender(d.getGender().name()).speciality(d.getSpeciality().name())
                .qualification(d.getQualification()).experienceYears(d.getExperienceYears())
                .consultationFee(d.getConsultationFee()).city(d.getCity()).bio(d.getBio())
                .licenseNumber(d.getLicenseNumber()).profilePhotoPath(d.getProfilePhotoPath())
                .status(d.getStatus().name()).rejectionReason(d.getRejectionReason())
                .adminNote(d.getAdminNote()).documents(docResponses)
                .registeredAt(d.getUser().getCreatedAt())
                .build();
    }

    private PatientSummaryResponse toPatientSummary(Patient p) {
        return PatientSummaryResponse.builder()
                .patientId(p.getId()).userId(p.getUser().getId())
                .name(p.getUser().getName()).email(p.getUser().getEmail()).mobile(p.getUser().getMobile())
                .dob(p.getDob()).bloodGroup(p.getBloodGroup())
                .registeredAt(p.getUser().getCreatedAt())
                .build();
    }

    private AppointmentResponse toAppointmentResponse(Appointment a) {
        return AppointmentResponse.builder()
                .appointmentId(a.getId())
                .doctorId(a.getDoctor().getId()).doctorName(a.getDoctor().getUser().getName())
                .doctorSpeciality(a.getDoctor().getSpeciality().name())
                .consultationFee(a.getDoctor().getConsultationFee())
                .patientId(a.getPatient().getId()).patientName(a.getPatient().getUser().getName())
                .patientEmail(a.getPatient().getUser().getEmail()).patientMobile(a.getPatient().getUser().getMobile())
                .appointmentDate(a.getAppointmentDate())
                .startTime(a.getStartTime()).endTime(a.getEndTime())
                .status(a.getStatus().name()).cancellationReason(a.getCancellationReason())
                .bookedAt(a.getBookedAt()).updatedAt(a.getUpdatedAt())
                .build();
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid value '" + value + "' for filter " + enumClass.getSimpleName());
        }
    }
}

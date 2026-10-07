package com.docspot.service;

import com.docspot.dto.request.*;
import com.docspot.dto.response.*;
import com.docspot.entity.*;
import com.docspot.enums.AppointmentStatus;
import com.docspot.enums.DocumentType;
import com.docspot.exception.BadRequestException;
import com.docspot.exception.ResourceNotFoundException;
import com.docspot.exception.UnauthorizedException;
import com.docspot.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DoctorService {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorDocumentRepository documentRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final AppointmentRepository appointmentRepository;
    private final FileStorageService fileStorageService;
    private final PasswordEncoder passwordEncoder;
    // @Lazy avoids a startup cycle risk — AppointmentService doesn't depend
    // on DoctorService today, but this keeps the wiring defensive.
    @Lazy
    private final AppointmentService appointmentService;

    // ─── Profile ─────────────────────────────────────────────────────────────

    public DoctorDetailResponse getProfile(String email) {
        Doctor doctor = findByEmail(email);
        List<DoctorDocument> docs = documentRepository.findByDoctor_Id(doctor.getId());
        return toDetail(doctor, docs);
    }

    @Transactional
    public DoctorDetailResponse updateProfile(String email, UpdateDoctorProfileRequest req) {
        Doctor doctor = findByEmail(email);

        if (req.getConsultationFee() != null) doctor.setConsultationFee(req.getConsultationFee());
        if (req.getCity() != null) doctor.setCity(req.getCity());
        if (req.getBio() != null) doctor.setBio(req.getBio());
        if (req.getQualification() != null) doctor.setQualification(req.getQualification());

        doctorRepository.save(doctor);
        log.info("Profile updated for doctor: {}", email);

        List<DoctorDocument> docs = documentRepository.findByDoctor_Id(doctor.getId());
        return toDetail(doctor, docs);
    }

    // ─── Change Password ──────────────────────────────────────────────────────

    @Transactional
    public String changePassword(String email, ChangePasswordRequest req) {
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match.");
        }

        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect.");
        }
        if (passwordEncoder.matches(req.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("New password cannot be the same as the current password.");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed for doctor: {}", email);
        return "Password changed successfully.";
    }

    // ─── Documents ────────────────────────────────────────────────────────────

    @Transactional
    public DocumentResponse uploadDocument(String email, String documentType, MultipartFile file) {
        Doctor doctor = findByEmail(email);
        DocumentType docType = parseDocumentType(documentType);

        String filePath = fileStorageService.store(file, doctor.getId());

        DoctorDocument doc = DoctorDocument.builder()
                .doctor(doctor)
                .documentType(docType)
                .originalFileName(file.getOriginalFilename())
                .storedFileName(filePath.substring(filePath.lastIndexOf('/') + 1))
                .filePath(filePath)
                .build();
        documentRepository.save(doc);

        log.info("Document uploaded for doctor {}: {}", email, filePath);
        return toDocumentResponse(doc);
    }

    public List<DocumentResponse> getMyDocuments(String email) {
        Doctor doctor = findByEmail(email);
        return documentRepository.findByDoctor_Id(doctor.getId())
                .stream().map(this::toDocumentResponse).toList();
    }

    // ─── Availability ─────────────────────────────────────────────────────────

    /**
     * POST /api/doctor/availability
     * Each request item is ONE time window on ONE day — this is now
     * additive (creates new rows), not an upsert, because a doctor can
     * legitimately have multiple non-overlapping windows on the same day
     * (e.g. a morning window and a separate evening window). Overlap
     * against existing active windows for that day is rejected with a
     * clear error rather than silently created.
     */
    @Transactional
    public List<AvailabilityResponse> setAvailability(String email, List<AvailabilityRequest> requests) {
        Doctor doctor = findByEmail(email);

        for (AvailabilityRequest req : requests) {
            if (!req.getStartTime().isBefore(req.getEndTime())) {
                throw new BadRequestException(
                        "Start time must be before end time for " + req.getDayOfWeek() + ".");
            }

            validateNoOverlap(doctor, req.getDayOfWeek(), req.getStartTime(), req.getEndTime(), null);

            availabilityRepository.save(DoctorAvailability.builder()
                    .doctor(doctor)
                    .dayOfWeek(req.getDayOfWeek())
                    .startTime(req.getStartTime())
                    .endTime(req.getEndTime())
                    .slotDurationMinutes(req.getSlotDurationMinutes())
                    .active(true)
                    .build());
        }

        log.info("Availability window(s) added for doctor: {}", email);
        return getAvailability(email);
    }

    public List<AvailabilityResponse> getAvailability(String email) {
        Doctor doctor = findByEmail(email);
        return availabilityRepository.findByDoctor_IdAndActiveTrue(doctor.getId())
                .stream().map(this::toAvailabilityResponse).toList();
    }

    /**
     * PUT /api/doctor/availability/{availabilityId}
     * Partial update — only re-validates overlap if start/end actually changed.
     *
     * ── Why the future-booking guard exists ──────────────────────────────
     * A window's (startTime, endTime, slotDurationMinutes) is a TEMPLATE —
     * it's re-read every time slots are generated for a future date, it's
     * never copied onto the Appointment row. If a patient already holds a
     * CONFIRMED booking next Wednesday at 10:00 under a "Wed 10:00–15:00,
     * 30-min" window, and the doctor then edits that window to "11:00–16:00,
     * 45-min", nothing in the booking itself breaks (its startTime/endTime
     * are stored directly on the Appointment row, untouched) — but the
     * *slot grid* shifts under it. The old 10:00 booking may no longer land
     * on a generated slot boundary at all, and worse, a new slot the system
     * now offers (say 10:45) could genuinely overlap the old booking's real
     * time span in a way the DB's exact-time uniqueness check can't catch,
     * since uniqueness only blocks identical start times, not overlapping
     * intervals between two different appointments.
     *
     * So: if this doctor has any CONFIRMED appointment today or later that
     * falls on the SAME day-of-week as this window, and the request is
     * trying to change startTime, endTime, or slotDurationMinutes, we
     * reject it. Toggling `active` on/off is exempt — deactivating only
     * stops NEW slots from being generated, it never touches an existing
     * CONFIRMED appointment's own stored time, so it's always safe.
     *
     * The doctor becomes free to edit again the moment that booked date
     * passes (auto-COMPLETED by the midnight job) or the patient cancels —
     * this check re-runs fresh on every request, there's nothing to unlock
     * manually.
     */
    @Transactional
    public AvailabilityResponse updateAvailability(Long availabilityId, String email, UpdateAvailabilityRequest req) {
        DoctorAvailability slot = findAvailabilityAndVerifyOwner(availabilityId, email);

        boolean timingChanged = req.getStartTime() != null
                || req.getEndTime() != null
                || req.getSlotDurationMinutes() != null;

        if (timingChanged) {
            assertNoFutureConfirmedBookingsOnDay(slot.getDoctor(), slot.getDayOfWeek());
        }

        LocalTime newStart = req.getStartTime() != null ? req.getStartTime() : slot.getStartTime();
        LocalTime newEnd = req.getEndTime() != null ? req.getEndTime() : slot.getEndTime();

        if (!newStart.isBefore(newEnd)) {
            throw new BadRequestException("Start time must be before end time.");
        }

        if (req.getStartTime() != null || req.getEndTime() != null) {
            validateNoOverlap(slot.getDoctor(), slot.getDayOfWeek(), newStart, newEnd, slot.getId());
        }

        slot.setStartTime(newStart);
        slot.setEndTime(newEnd);
        if (req.getSlotDurationMinutes() != null) slot.setSlotDurationMinutes(req.getSlotDurationMinutes());
        if (req.getActive() != null) slot.setActive(req.getActive());

        availabilityRepository.save(slot);
        log.info("Availability {} updated by {}", availabilityId, email);
        return toAvailabilityResponse(slot);
    }

    /**
     * Deleting a window is treated the same as changing its timing — it
     * erases the template entirely, so the same future-booking guard
     * applies unconditionally (unlike update, there's no "just toggling
     * active" exemption possible for a delete).
     */
    @Transactional
    public String deleteAvailability(Long availabilityId, String email) {
        DoctorAvailability slot = findAvailabilityAndVerifyOwner(availabilityId, email);
        assertNoFutureConfirmedBookingsOnDay(slot.getDoctor(), slot.getDayOfWeek());
        availabilityRepository.delete(slot);
        log.info("Availability {} deleted by {}", availabilityId, email);
        return "Availability slot removed.";
    }

    // ─── Appointments ────────────────────────────────────────────────────────

    public Page<AppointmentResponse> getUpcomingAppointments(String email, Pageable pageable) {
        Doctor doctor = findByEmail(email);
        return appointmentRepository
                .findByDoctor_IdAndStatusOrderByAppointmentDateAsc(doctor.getId(), AppointmentStatus.CONFIRMED, pageable)
                .map(appointmentService::toResponse);
    }

    public Page<AppointmentResponse> getAppointmentHistory(String email, Pageable pageable) {
        Doctor doctor = findByEmail(email);
        return appointmentRepository
                .findByDoctor_IdAndStatusInOrderByAppointmentDateDesc(
                        doctor.getId(), List.of(AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED), pageable)
                .map(appointmentService::toResponse);
    }

    /**
     * Delegates all validation + event publishing to AppointmentService.
     * Guards enforced there: ownership, CONFIRMED status, date not future.
     */
    public String markAsCompleted(Long appointmentId, String email) {
        return appointmentService.complete(appointmentId, email);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private Doctor findByEmail(String email) {
        return doctorRepository.findByUser_Email(email)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found."));
    }

    private DoctorAvailability findAvailabilityAndVerifyOwner(Long availabilityId, String email) {
        Doctor doctor = findByEmail(email);
        DoctorAvailability slot = availabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Availability slot", availabilityId));

        if (!slot.getDoctor().getId().equals(doctor.getId())) {
            throw new UnauthorizedException("You are not authorized to modify this availability slot.");
        }
        return slot;
    }

    /**
     * Rejects a new/updated window if it overlaps any other ACTIVE window
     * this doctor already has on the same day. excludeId lets an update
     * skip comparing the window against itself.
     */
    private void validateNoOverlap(Doctor doctor, DayOfWeek day, LocalTime newStart, LocalTime newEnd, Long excludeId) {
        List<DoctorAvailability> existing = availabilityRepository
                .findByDoctor_IdAndDayOfWeekAndActiveTrue(doctor.getId(), day);

        for (DoctorAvailability window : existing) {
            if (excludeId != null && window.getId().equals(excludeId)) continue;

            boolean overlaps = newStart.isBefore(window.getEndTime()) && window.getStartTime().isBefore(newEnd);
            if (overlaps) {
                throw new BadRequestException(
                        "This window overlaps with an existing availability slot ("
                                + window.getStartTime() + "–" + window.getEndTime() + ") on " + day + ".");
            }
        }
    }

    /**
     * Pulls every CONFIRMED appointment this doctor has today-or-later,
     * then filters in Java for ones whose date falls on the given
     * day-of-week. There's no portable JPQL "day of week of a date column"
     * function, so this is done application-side on a doctor-scoped,
     * naturally small result set (bounded further by the patient-facing
     * max-advance-booking-days cap in AppointmentService).
     *
     * Throws with the count and nearest affected date so the doctor gets
     * an actionable message instead of a bare rejection.
     */
    private void assertNoFutureConfirmedBookingsOnDay(Doctor doctor, DayOfWeek day) {
        List<LocalDate> conflictingDates = appointmentRepository
                .findByDoctor_IdAndStatusAndAppointmentDateGreaterThanEqual(
                        doctor.getId(), AppointmentStatus.CONFIRMED, LocalDate.now())
                .stream()
                .map(Appointment::getAppointmentDate)
                .filter(date -> date.getDayOfWeek() == day)
                .sorted()
                .toList();

        if (!conflictingDates.isEmpty()) {
            LocalDate nearest = conflictingDates.get(0);
            throw new BadRequestException(
                    "You have " + conflictingDates.size() + " upcoming confirmed appointment(s) on "
                            + day + " (nearest: " + nearest + "). You can't change or remove this window's "
                            + "timing until those appointments are completed or cancelled. "
                            + "You can still toggle it active/inactive without restriction.");
        }
    }

    private DocumentType parseDocumentType(String value) {
        try {
            return DocumentType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid document type: " + value
                    + ". Allowed: DEGREE_CERTIFICATE, MEDICAL_LICENSE, IDENTITY_PROOF, OTHER");
        }
    }

    private DoctorDetailResponse toDetail(Doctor d, List<DoctorDocument> docs) {
        return DoctorDetailResponse.builder()
                .doctorId(d.getId()).userId(d.getUser().getId())
                .name(d.getUser().getName()).email(d.getUser().getEmail()).mobile(d.getUser().getMobile())
                .gender(d.getGender().name()).speciality(d.getSpeciality().name())
                .qualification(d.getQualification()).experienceYears(d.getExperienceYears())
                .consultationFee(d.getConsultationFee()).city(d.getCity()).bio(d.getBio())
                .licenseNumber(d.getLicenseNumber()).profilePhotoPath(d.getProfilePhotoPath())
                .status(d.getStatus().name()).rejectionReason(d.getRejectionReason())
                .adminNote(d.getAdminNote())
                .documents(docs.stream().map(this::toDocumentResponse).toList())
                .registeredAt(d.getUser().getCreatedAt())
                .build();
    }

    private DocumentResponse toDocumentResponse(DoctorDocument doc) {
        return DocumentResponse.builder()
                .documentId(doc.getId()).documentType(doc.getDocumentType().name())
                .originalFileName(doc.getOriginalFileName()).filePath(doc.getFilePath())
                .uploadedAt(doc.getUploadedAt())
                .build();
    }

    private AvailabilityResponse toAvailabilityResponse(DoctorAvailability a) {
        return AvailabilityResponse.builder()
                .availabilityId(a.getId()).dayOfWeek(a.getDayOfWeek().name())
                .startTime(a.getStartTime()).endTime(a.getEndTime())
                .slotDurationMinutes(a.getSlotDurationMinutes()).active(a.isActive())
                .build();
    }
}

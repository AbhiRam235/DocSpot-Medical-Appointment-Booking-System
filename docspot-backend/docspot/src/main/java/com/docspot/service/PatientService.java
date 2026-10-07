package com.docspot.service;

import com.docspot.dto.request.BookAppointmentRequest;
import com.docspot.dto.request.ChangePasswordRequest;
import com.docspot.dto.request.UpdatePatientProfileRequest;
import com.docspot.dto.response.*;
import com.docspot.entity.*;
import com.docspot.enums.*;
import com.docspot.exception.BadRequestException;
import com.docspot.exception.ResourceNotFoundException;
import com.docspot.repository.*;
import com.docspot.util.SlotGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Handles patient-specific concerns: profile, doctor discovery, appointment
 * list views. Booking and cancellation are delegated to AppointmentService,
 * which owns all state-transition logic and event publishing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PatientService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppointmentService appointmentService;

    // ─── 1. Profile ───────────────────────────────────────────────────────────

    public PatientProfileResponse getProfile(String email) {
        return toProfileResponse(findByEmail(email));
    }

    @Transactional
    public PatientProfileResponse updateProfile(String email, UpdatePatientProfileRequest req) {
        Patient patient = findByEmail(email);
        User user = patient.getUser();

        if (req.getName() != null) user.setName(req.getName());
        if (req.getMobile() != null) user.setMobile(req.getMobile());
        if (req.getDob() != null) patient.setDob(req.getDob());
        if (req.getBloodGroup() != null) patient.setBloodGroup(req.getBloodGroup());

        userRepository.save(user);
        patientRepository.save(patient);

        log.info("Profile updated: {}", email);
        return toProfileResponse(patient);
    }

    // ─── 2. Change Password ───────────────────────────────────────────────────

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
            throw new BadRequestException("New password cannot be the same as the current one.");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed: {}", email);
        return "Password changed successfully.";
    }

    // ─── 3. Doctor Search ─────────────────────────────────────────────────────

    public Page<DoctorSummaryResponse> searchDoctors(
            String speciality, String gender, String city,
            Integer minExp, Integer maxExp, Double minFee, Double maxFee,
            String sortBy, String sortOrder, int page, int size) {

        Sort.Direction dir = "desc".equalsIgnoreCase(sortOrder) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String sortField = "experience".equalsIgnoreCase(sortBy) ? "experienceYears" : "consultationFee";
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sortField));

        Speciality specEnum = parseEnum(Speciality.class, speciality);
        Gender genderEnum = parseEnum(Gender.class, gender);

        return doctorRepository
                .searchDoctors(specEnum, genderEnum, city, minExp, maxExp, minFee, maxFee, pageable)
                .map(this::toPublicSummary);
    }

    // ─── 4. Doctor Public Profile ─────────────────────────────────────────────

    public DoctorDetailResponse getDoctorPublicProfile(Long doctorId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId));

        if (doctor.getStatus() != DoctorStatus.APPROVED || doctor.getUser().isDeleted()) {
            throw new ResourceNotFoundException("Doctor not found.");
        }
        return toPublicDetail(doctor);
    }

    // ─── 5. Slot Availability ─────────────────────────────────────────────────

    /**
     * For a given doctor + date: pull all active availability windows for
     * that day of week, expand each into its discrete slot start times via
     * SlotGenerator, and check each one against the activeSlot column to
     * determine AVAILABLE vs BOOKED.
     *
     * This MUST use the same SlotGenerator.generateSlots() call that
     * AppointmentService.book() uses to validate a booking request — see
     * SlotGenerator's class Javadoc for why that matters.
     */
    @Transactional(readOnly = true)
    public SlotAvailabilityResponse getAvailableSlots(Long doctorId, LocalDate date) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId));

        if (doctor.getStatus() != DoctorStatus.APPROVED) {
            throw new BadRequestException("This doctor is not available for booking.");
        }

        List<DoctorAvailability> windows = availabilityRepository
                .findByDoctor_IdAndDayOfWeekAndActiveTrue(doctorId, date.getDayOfWeek());

        List<SlotAvailabilityResponse.SlotInfo> allSlots = new ArrayList<>();

        for (DoctorAvailability window : windows) {
            List<LocalTime> starts = SlotGenerator.generateSlots(
                    window.getStartTime(), window.getEndTime(), window.getSlotDurationMinutes());

            for (LocalTime start : starts) {
                LocalTime end = start.plusMinutes(window.getSlotDurationMinutes());

                boolean taken = appointmentRepository
                        .existsByDoctor_IdAndAppointmentDateAndActiveSlot(doctorId, date, start);

                allSlots.add(SlotAvailabilityResponse.SlotInfo.builder()
                        .startTime(start)
                        .endTime(end)
                        .status(taken ? "BOOKED" : "AVAILABLE")
                        .build());
            }
        }

        // Windows aren't necessarily entered in chronological order — sort for display
        allSlots.sort(Comparator.comparing(SlotAvailabilityResponse.SlotInfo::getStartTime));

        return SlotAvailabilityResponse.builder()
                .doctorId(doctorId)
                .doctorName(doctor.getUser().getName())
                .date(date)
                .slots(allSlots)
                .build();
    }

    // ─── 6. Book Appointment ─────────────────────────────────────────────────

    public AppointmentResponse bookAppointment(String email, BookAppointmentRequest req) {
        return appointmentService.book(email, req);
    }

    // ─── 7 & 8. Appointment Lists ─────────────────────────────────────────────

    public Page<AppointmentResponse> getUpcomingAppointments(String email, Pageable pageable) {
        Patient patient = findByEmail(email);
        return appointmentRepository
                .findByPatient_IdAndStatusOrderByAppointmentDateAsc(patient.getId(), AppointmentStatus.CONFIRMED, pageable)
                .map(appointmentService::toResponse);
    }

    public Page<AppointmentResponse> getAppointmentHistory(String email, Pageable pageable) {
        Patient patient = findByEmail(email);
        return appointmentRepository
                .findByPatient_IdAndStatusInOrderByAppointmentDateDesc(
                        patient.getId(), List.of(AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED), pageable)
                .map(appointmentService::toResponse);
    }

    // ─── 9. Cancel Appointment ────────────────────────────────────────────────

    public String cancelAppointment(Long appointmentId, String email) {
        return appointmentService.cancel(appointmentId, email);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private Patient findByEmail(String email) {
        return patientRepository.findByUser_Email(email)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found."));
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid value '" + value + "' for " + type.getSimpleName());
        }
    }

    // ─── Mappers ─────────────────────────────────────────────────────────────

    private PatientProfileResponse toProfileResponse(Patient p) {
        return PatientProfileResponse.builder()
                .patientId(p.getId()).userId(p.getUser().getId())
                .name(p.getUser().getName()).email(p.getUser().getEmail()).mobile(p.getUser().getMobile())
                .dob(p.getDob()).bloodGroup(p.getBloodGroup())
                .registeredAt(p.getUser().getCreatedAt())
                .build();
    }

    private DoctorSummaryResponse toPublicSummary(Doctor d) {
        return DoctorSummaryResponse.builder()
                .doctorId(d.getId()).name(d.getUser().getName())
                .gender(d.getGender().name()).speciality(d.getSpeciality().name())
                .qualification(d.getQualification()).experienceYears(d.getExperienceYears())
                .consultationFee(d.getConsultationFee()).city(d.getCity())
                .build();
    }

    private DoctorDetailResponse toPublicDetail(Doctor d) {
        return DoctorDetailResponse.builder()
                .doctorId(d.getId()).name(d.getUser().getName())
                .email(d.getUser().getEmail()).mobile(d.getUser().getMobile())
                .gender(d.getGender().name()).speciality(d.getSpeciality().name())
                .qualification(d.getQualification()).experienceYears(d.getExperienceYears())
                .consultationFee(d.getConsultationFee()).city(d.getCity()).bio(d.getBio())
                .profilePhotoPath(d.getProfilePhotoPath())
                .registeredAt(d.getUser().getCreatedAt())
                .build();
    }
}

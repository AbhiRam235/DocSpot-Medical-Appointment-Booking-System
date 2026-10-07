package com.docspot.service;

import com.docspot.dto.request.BookAppointmentRequest;
import com.docspot.dto.response.AppointmentResponse;
import com.docspot.entity.*;
import com.docspot.enums.AppointmentStatus;
import com.docspot.enums.DoctorStatus;
import com.docspot.event.*;
import com.docspot.exception.BadRequestException;
import com.docspot.exception.ResourceNotFoundException;
import com.docspot.exception.UnauthorizedException;
import com.docspot.repository.*;
import com.docspot.util.SlotGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * AppointmentService is the single source of truth for every appointment
 * state transition: CONFIRMED → CANCELLED and CONFIRMED → COMPLETED.
 *
 * It is NOT exposed via its own controller. Instead:
 *   PatientController → PatientService → AppointmentService.book() / .cancel()
 *   DoctorController  → DoctorService  → AppointmentService.complete()
 *
 * ── Real time slots, not session buckets ───────────────────────────────────
 * Booking now works against discrete start times (09:00, 09:30, 10:00 ...)
 * generated from a doctor's availability windows via SlotGenerator, rather
 * than a shared MORNING/AFTERNOON/EVENING bucket with a patient-count cap.
 * Each generated slot holds exactly one CONFIRMED booking.
 *
 * ── Two layers of double-booking protection ────────────────────────────────
 *  1. Application-level: validateSlotNotTaken() checks before inserting —
 *     fast, gives a friendly error in the common case.
 *  2. DB-level: the Appointment table's unique constraint on
 *     (doctor_id, appointment_date, active_slot) is the real guarantee.
 *     If two requests for the same slot race past check #1 simultaneously,
 *     only one INSERT succeeds; the other hits DataIntegrityViolationException,
 *     caught below and converted into the same friendly error.
 *
 * Every state transition publishes a Spring ApplicationEvent that
 * NotificationService listens to for emails + in-app notifications —
 * completely decoupled from booking logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final ApplicationEventPublisher eventPublisher;

    // How far ahead a patient may book. Bounded deliberately, not just for
    // UX sanity — it also bounds how long a doctor can ever be locked out
    // of editing an availability window by assertNoFutureConfirmedBookingsOnDay
    // in DoctorService: since no booking can exist further out than this,
    // no lock can ever last longer than this either. It self-clears weekly.
    @Value("${app.booking.max-advance-days:7}")
    private int maxAdvanceBookingDays;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    // ─── book() ───────────────────────────────────────────────────────────────

    /**
     * Guard order (cheapest first):
     *  ① Doctor APPROVED + not deleted
     *  ② Date not in the past
     *  ③ Requested startTime falls on a real slot boundary within one of the
     *     doctor's availability windows for that day of week
     *  ④ Patient has no existing live booking with this doctor on this date
     *  ⑤ Slot not already taken (application-level pre-check)
     *  ⑥ INSERT — DB unique constraint is the final word if ⑤ raced
     */
    @Transactional
    public AppointmentResponse book(String patientEmail, BookAppointmentRequest req) {

        Patient patient = findPatient(patientEmail);
        Doctor doctor = findApprovedDoctor(req.getDoctorId());

        validateBookingWindow(req.getDate());
        DoctorAvailability window = findMatchingWindow(doctor, req.getDate(), req.getStartTime());
        validateNoDuplicate(patient, doctor, req.getDate());
        validateSlotNotTaken(doctor, req.getDate(), req.getStartTime());

        LocalTime endTime = req.getStartTime().plusMinutes(window.getSlotDurationMinutes());

        Appointment appointment = Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .appointmentDate(req.getDate())
                .startTime(req.getStartTime())
                .endTime(endTime)
                .activeSlot(req.getStartTime())   // mirrors startTime while CONFIRMED — see entity Javadoc
                .status(AppointmentStatus.CONFIRMED)
                .build();

        try {
            appointmentRepository.save(appointment);
        } catch (DataIntegrityViolationException e) {
            // The DB constraint caught a genuine race: two requests for the
            // same doctor+date+time landed in the same instant, and ours lost.
            throw new BadRequestException(
                    "Sorry, this slot was just booked by someone else. Please choose another slot.");
        }

        eventPublisher.publishEvent(buildBookedEvent(appointment));

        log.info("[book] id={} | patient={} | doctor={} | {} {}",
                appointment.getId(), patientEmail, doctor.getUser().getEmail(), req.getDate(), req.getStartTime());

        return toResponse(appointment);
    }

    // ─── cancel() ─────────────────────────────────────────────────────────────

    @Transactional
    public String cancel(Long appointmentId, String patientEmail) {

        Patient patient = findPatient(patientEmail);
        Appointment appointment = findAppointmentById(appointmentId);

        assertPatientOwns(appointment, patient);
        assertConfirmed(appointment);
        assert2HourRuleHolds(appointment);

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setActiveSlot(null);   // frees this exact slot for anyone else to book
        appointmentRepository.save(appointment);

        eventPublisher.publishEvent(buildCancelledEvent(appointment));

        log.info("[cancel] id={} | patient={}", appointmentId, patientEmail);
        return "Appointment cancelled successfully.";
    }

    // ─── complete() ───────────────────────────────────────────────────────────

    @Transactional
    public String complete(Long appointmentId, String doctorEmail) {

        Doctor doctor = findDoctorByEmail(doctorEmail);
        Appointment appointment = findAppointmentById(appointmentId);

        assertDoctorOwns(appointment, doctor);
        assertConfirmed(appointment);
        assertDateNotFuture(appointment);

        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointment.setActiveSlot(null);
        appointmentRepository.save(appointment);

        eventPublisher.publishEvent(buildCompletedEvent(appointment));

        log.info("[complete] id={} | doctor={}", appointmentId, doctorEmail);
        return "Appointment marked as completed.";
    }

    // ─── Scheduled: midnight auto-complete ────────────────────────────────────

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void autoCompletePastAppointments() {
        List<Appointment> past = appointmentRepository
                .findByStatusAndAppointmentDateBefore(AppointmentStatus.CONFIRMED, LocalDate.now());

        if (past.isEmpty()) {
            log.info("[Scheduler:autoComplete] Nothing to process.");
            return;
        }

        for (Appointment a : past) {
            a.setStatus(AppointmentStatus.COMPLETED);
            a.setActiveSlot(null);
            appointmentRepository.save(a);
            eventPublisher.publishEvent(buildCompletedEvent(a));
        }

        log.info("[Scheduler:autoComplete] Auto-completed {} appointments.", past.size());
    }

    // ─── Scheduled: morning reminders ────────────────────────────────────────

    @Scheduled(cron = "0 0 9 * * *")
    @Transactional(readOnly = true)
    public void publishTomorrowReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        List<Appointment> tomorrowAppts = appointmentRepository
                .findByStatusAndAppointmentDate(AppointmentStatus.CONFIRMED, tomorrow);

        if (tomorrowAppts.isEmpty()) {
            log.info("[Scheduler:reminders] No appointments tomorrow.");
            return;
        }

        tomorrowAppts.forEach(a -> eventPublisher.publishEvent(buildReminderEvent(a)));

        log.info("[Scheduler:reminders] Published reminder events for {} appointments on {}.",
                tomorrowAppts.size(), tomorrow);
    }

    // ─── Guards ───────────────────────────────────────────────────────────────

    /**
     * Two checks in one: not in the past, and not further out than the
     * configured booking horizon (default 7 days, app.booking.max-advance-days).
     *
     * The upper bound isn't just UX — it's what keeps
     * DoctorService.assertNoFutureConfirmedBookingsOnDay's edit-lock finite.
     * Without a cap, a patient could book 6 months out and freeze that
     * window's editability for 6 months. With the cap, the longest any
     * window can ever be locked is one booking cycle — it clears itself
     * automatically as each date passes.
     */
    private void validateBookingWindow(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) {
            throw new BadRequestException("Cannot book an appointment for a past date.");
        }
        LocalDate maxDate = today.plusDays(maxAdvanceBookingDays);
        if (date.isAfter(maxDate)) {
            throw new BadRequestException(
                    "Appointments can only be booked up to " + maxAdvanceBookingDays
                            + " days in advance. Please choose a date on or before " + maxDate + ".");
        }
    }

    /**
     * Finds the doctor's availability window that generates the requested
     * startTime as one of its slot boundaries — i.e. proves the requested
     * time is a real, offered slot and not an arbitrary value the client
     * made up.
     */
    private DoctorAvailability findMatchingWindow(Doctor doctor, LocalDate date, LocalTime requestedStart) {
        DayOfWeek dow = date.getDayOfWeek();

        List<DoctorAvailability> windows = availabilityRepository
                .findByDoctor_IdAndDayOfWeekAndActiveTrue(doctor.getId(), dow);

        if (windows.isEmpty()) {
            throw new BadRequestException(
                    "Dr. " + doctor.getUser().getName() + " is not available on " + dow + ".");
        }

        return windows.stream()
                .filter(w -> SlotGenerator
                        .generateSlots(w.getStartTime(), w.getEndTime(), w.getSlotDurationMinutes())
                        .contains(requestedStart))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "The selected time (" + requestedStart + ") is not a valid slot for Dr. "
                                + doctor.getUser().getName() + " on " + dow + "."));
    }

    private void validateNoDuplicate(Patient patient, Doctor doctor, LocalDate date) {
        boolean exists = appointmentRepository
                .existsByPatient_IdAndDoctor_IdAndAppointmentDateAndStatusNot(
                        patient.getId(), doctor.getId(), date, AppointmentStatus.CANCELLED);
        if (exists) {
            throw new BadRequestException(
                    "You already have an active booking with Dr. " + doctor.getUser().getName()
                            + " on " + date + ". Cancel it first to rebook.");
        }
    }

    /**
     * Application-level pre-check — fast, common-case rejection with a
     * friendly message. The DB unique constraint (see book()'s try/catch)
     * is the real guarantee against a genuine race between two concurrent
     * requests for the same slot.
     */
    private void validateSlotNotTaken(Doctor doctor, LocalDate date, LocalTime startTime) {
        boolean taken = appointmentRepository
                .existsByDoctor_IdAndAppointmentDateAndActiveSlot(doctor.getId(), date, startTime);
        if (taken) {
            throw new BadRequestException(
                    "Sorry, " + startTime + " on " + date + " is already booked. Please choose another slot.");
        }
    }

    private void assertPatientOwns(Appointment a, Patient p) {
        if (!a.getPatient().getId().equals(p.getId())) {
            throw new UnauthorizedException("You are not authorised to cancel this appointment.");
        }
    }

    private void assertDoctorOwns(Appointment a, Doctor d) {
        if (!a.getDoctor().getId().equals(d.getId())) {
            throw new UnauthorizedException("You are not authorised to update this appointment.");
        }
    }

    private void assertConfirmed(Appointment a) {
        if (a.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BadRequestException(
                    "Only CONFIRMED appointments can be changed. Current status: " + a.getStatus() + ".");
        }
    }

    private void assert2HourRuleHolds(Appointment a) {
        LocalDateTime appointmentStart = LocalDateTime.of(a.getAppointmentDate(), a.getStartTime());
        if (LocalDateTime.now().plusHours(2).isAfter(appointmentStart)) {
            throw new BadRequestException(
                    "Cancellations are not allowed within 2 hours of the appointment. "
                            + "This appointment starts at " + appointmentStart + ".");
        }
    }

    private void assertDateNotFuture(Appointment a) {
        if (a.getAppointmentDate().isAfter(LocalDate.now())) {
            throw new BadRequestException("Cannot mark a future appointment as completed.");
        }
    }

    // ─── Lookups ──────────────────────────────────────────────────────────────

    private Patient findPatient(String email) {
        return patientRepository.findByUser_Email(email)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found."));
    }

    private Doctor findApprovedDoctor(Long doctorId) {
        Doctor d = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId));
        if (d.getStatus() != DoctorStatus.APPROVED || d.getUser().isDeleted()) {
            throw new BadRequestException("This doctor is not currently available for booking.");
        }
        return d;
    }

    private Doctor findDoctorByEmail(String email) {
        return doctorRepository.findByUser_Email(email)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found."));
    }

    private Appointment findAppointmentById(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
    }

    // ─── Formatting ───────────────────────────────────────────────────────────

    private String formatTimeRange(LocalTime start, LocalTime end) {
        return start.format(TIME_FMT) + " - " + end.format(TIME_FMT);
    }

    // ─── Event builders ───────────────────────────────────────────────────────

    private AppointmentBookedEvent buildBookedEvent(Appointment a) {
        return new AppointmentBookedEvent(
                a.getId(), a.getDoctor().getUser().getId(), a.getPatient().getUser().getId(),
                a.getDoctor().getUser().getName(), a.getDoctor().getUser().getEmail(),
                a.getPatient().getUser().getName(), a.getPatient().getUser().getEmail(),
                a.getAppointmentDate(), formatTimeRange(a.getStartTime(), a.getEndTime())
        );
    }

    private AppointmentCancelledEvent buildCancelledEvent(Appointment a) {
        return new AppointmentCancelledEvent(
                a.getId(), a.getDoctor().getUser().getId(), a.getPatient().getUser().getId(),
                a.getDoctor().getUser().getName(), a.getDoctor().getUser().getEmail(),
                a.getPatient().getUser().getName(), a.getPatient().getUser().getEmail(),
                a.getAppointmentDate(), formatTimeRange(a.getStartTime(), a.getEndTime())
        );
    }

    private AppointmentCompletedEvent buildCompletedEvent(Appointment a) {
        return new AppointmentCompletedEvent(
                a.getId(), a.getDoctor().getUser().getId(), a.getPatient().getUser().getId(),
                a.getDoctor().getUser().getName(), a.getDoctor().getUser().getEmail(),
                a.getPatient().getUser().getName(), a.getPatient().getUser().getEmail(),
                a.getAppointmentDate(), formatTimeRange(a.getStartTime(), a.getEndTime())
        );
    }

    private AppointmentReminderEvent buildReminderEvent(Appointment a) {
        return new AppointmentReminderEvent(
                a.getId(), a.getDoctor().getUser().getId(), a.getPatient().getUser().getId(),
                a.getDoctor().getUser().getName(), a.getDoctor().getUser().getEmail(),
                a.getPatient().getUser().getName(), a.getPatient().getUser().getEmail(),
                a.getAppointmentDate(), formatTimeRange(a.getStartTime(), a.getEndTime())
        );
    }

    // ─── Response mapper ──────────────────────────────────────────────────────

    AppointmentResponse toResponse(Appointment a) {
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
}

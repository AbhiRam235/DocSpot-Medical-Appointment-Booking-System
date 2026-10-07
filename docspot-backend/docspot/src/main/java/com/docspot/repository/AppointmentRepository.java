package com.docspot.repository;

import com.docspot.entity.Appointment;
import com.docspot.enums.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // ── Slot-taken check ──────────────────────────────────────────────────────
    // Because activeSlot is null for every non-CONFIRMED row, this single
    // check IS the "is this exact time already booked" answer — no need to
    // separately filter by status. See Appointment's class Javadoc.
    boolean existsByDoctor_IdAndAppointmentDateAndActiveSlot(
            Long doctorId, LocalDate date, LocalTime activeSlot);

    // ── Duplicate-booking guard ───────────────────────────────────────────────
    // Same doctor, same date, any time — patient can't hold two live bookings
    // with one doctor on one day, regardless of which time slots they pick.
    boolean existsByPatient_IdAndDoctor_IdAndAppointmentDateAndStatusNot(
            Long patientId, Long doctorId, LocalDate date, AppointmentStatus excludedStatus);

    // ── Doctor views ──────────────────────────────────────────────────────────
    Page<Appointment> findByDoctor_IdAndStatusOrderByAppointmentDateAsc(
            Long doctorId, AppointmentStatus status, Pageable pageable);

    Page<Appointment> findByDoctor_IdAndStatusInOrderByAppointmentDateDesc(
            Long doctorId, List<AppointmentStatus> statuses, Pageable pageable);

    // ── Patient views ─────────────────────────────────────────────────────────
    Page<Appointment> findByPatient_IdAndStatusOrderByAppointmentDateAsc(
            Long patientId, AppointmentStatus status, Pageable pageable);

    Page<Appointment> findByPatient_IdAndStatusInOrderByAppointmentDateDesc(
            Long patientId, List<AppointmentStatus> statuses, Pageable pageable);

    // ── Scheduled jobs ────────────────────────────────────────────────────────
    List<Appointment> findByStatusAndAppointmentDateBefore(AppointmentStatus status, LocalDate date);
    List<Appointment> findByStatusAndAppointmentDate(AppointmentStatus status, LocalDate date);

    // ── Availability-edit guard ───────────────────────────────────────────────
    // Used by DoctorService before allowing a timing change or delete on an
    // availability window — pulls all of this doctor's still-live future
    // bookings so the service can check whether any of them fall on the
    // day-of-week being edited. No DB function for "day of week of a date"
    // is used here (not portable across dialects); the day-of-week filter
    // happens in Java on this small, doctor-scoped result set instead.
    List<Appointment> findByDoctor_IdAndStatusAndAppointmentDateGreaterThanEqual(
            Long doctorId, AppointmentStatus status, LocalDate fromDate);

    // ── Dashboard stat ────────────────────────────────────────────────────────
    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.appointmentDate = :today")
    long countTodayAppointments(@Param("today") LocalDate today);

    // ── Admin: filter by any combination ─────────────────────────────────────
    @Query("""
        SELECT a FROM Appointment a
        WHERE (:status    IS NULL OR a.status          = :status)
          AND (:date      IS NULL OR a.appointmentDate = :date)
          AND (:doctorId  IS NULL OR a.doctor.id       = :doctorId)
          AND (:patientId IS NULL OR a.patient.id      = :patientId)
    """)
    Page<Appointment> findAllByFilters(
            @Param("status") AppointmentStatus status,
            @Param("date") LocalDate date,
            @Param("doctorId") Long doctorId,
            @Param("patientId") Long patientId,
            Pageable pageable
    );
}

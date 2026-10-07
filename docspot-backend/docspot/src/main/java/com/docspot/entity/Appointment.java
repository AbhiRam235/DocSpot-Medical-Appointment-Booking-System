package com.docspot.entity;

import com.docspot.enums.AppointmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Represents one booking at a specific doctor + date + time.
 *
 * ── The activeSlot shadow column ─────────────────────────────────────────
 * The unique constraint below is scoped to (doctor_id, appointment_date,
 * active_slot) — NOT (doctor_id, appointment_date, start_time). This is
 * deliberate:
 *
 *   If the constraint used start_time directly, a single row surviving in
 *   CANCELLED status would permanently block that exact slot from ever
 *   being booked again by anyone — the INSERT would keep failing forever,
 *   because start_time never changes and the row is never deleted (we keep
 *   cancelled bookings for audit history).
 *
 *   activeSlot mirrors startTime ONLY while status == CONFIRMED. The moment
 *   a booking is cancelled or completed, activeSlot is set to null. MySQL's
 *   UNIQUE index treats NULL as distinct across every row (NULL != NULL in
 *   index terms), so any number of cancelled/completed rows can coexist at
 *   the same doctor+date+time — the constraint only ever "sees" and blocks
 *   on the one row that's currently CONFIRMED.
 *
 * Whenever you write code that changes status AWAY from CONFIRMED, you MUST
 * also null out activeSlot in the same place, or this guarantee breaks.
 * (Done in AppointmentService.cancel(), .complete(), and the midnight
 * auto-complete scheduled job — search for "setActiveSlot(null)".)
 */
@Entity
@Table(
        name = "appointments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_doctor_active_slot",
                columnNames = {"doctor_id", "appointment_date", "active_slot"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(nullable = false)
    private LocalDate appointmentDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    // See class-level Javadoc — this is what the DB uniqueness guarantee
    // actually keys off, not startTime directly.
    @Column(name = "active_slot")
    private LocalTime activeSlot;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status = AppointmentStatus.CONFIRMED;

    @Column(columnDefinition = "TEXT")
    private String cancellationReason;

    private LocalDateTime bookedAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.bookedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

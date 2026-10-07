package com.docspot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Represents one bookable time WINDOW for a doctor on a given day of week
 * (e.g. Monday 09:00–12:00, slot length 30 min).
 *
 * A doctor can have MULTIPLE windows per day (e.g. a morning window and a
 * separate evening window) — there's no DB-level uniqueness on
 * (doctor, dayOfWeek) because "no time overlap" can't be expressed as a
 * simple unique constraint. Overlap is validated in DoctorService instead.
 *
 * The actual list of bookable start times (09:00, 09:30, 10:00 ...) is
 * derived on demand from (startTime, endTime, slotDurationMinutes) via
 * SlotGenerator — never stored. This keeps a single source of truth: change
 * the window, and every future date's slots recompute automatically.
 */
@Entity
@Table(name = "doctor_availabilities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private Integer slotDurationMinutes;

    // @Builder.Default is required here — without it, .builder().build() with
    // no explicit .active(...) call silently creates an INACTIVE slot, because
    // primitive boolean defaults to false, not true.
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}

package com.docspot.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Published by AppointmentService in two situations:
 *   1. Doctor manually marks an appointment COMPLETED via PUT /api/doctor/appointments/{id}/complete
 *   2. Midnight scheduler auto-completes all past CONFIRMED appointments
 *
 * NotificationService listens and can log/notify as needed.
 */
@Getter
@AllArgsConstructor
public class AppointmentCompletedEvent {

    private final Long      appointmentId;
    private final Long      doctorUserId;
    private final Long      patientUserId;

    private final String    doctorName;
    private final String    doctorEmail;
    private final String    patientName;
    private final String    patientEmail;
    private final LocalDate appointmentDate;
    private final String    timeRange;
}

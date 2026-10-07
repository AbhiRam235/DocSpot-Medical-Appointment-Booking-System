package com.docspot.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Published by AppointmentService after a booking is confirmed.
 * NotificationService listens and:
 *   - Sends confirmation email to patient
 *   - Sends notification email to doctor
 *   - Saves in-app notification records for both
 *
 * All data needed for emails is embedded — no extra DB queries in the listener.
 */
@Getter
@AllArgsConstructor
public class AppointmentBookedEvent {

    private final Long      appointmentId;

    // User IDs for notification DB records
    private final Long      doctorUserId;
    private final Long      patientUserId;

    // Email content fields
    private final String    doctorName;
    private final String    doctorEmail;
    private final String    patientName;
    private final String    patientEmail;
    private final LocalDate appointmentDate;
    private final String    timeRange;
}

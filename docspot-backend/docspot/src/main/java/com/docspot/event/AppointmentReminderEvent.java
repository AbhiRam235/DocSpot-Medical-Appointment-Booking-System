package com.docspot.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Published by AppointmentService every morning at 09:00 for each
 * CONFIRMED appointment scheduled for tomorrow.
 *
 * NotificationService listens and sends reminder emails to both
 * the patient and the doctor.
 */
@Getter
@AllArgsConstructor
public class AppointmentReminderEvent {

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

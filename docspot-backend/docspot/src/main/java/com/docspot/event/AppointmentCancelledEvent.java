package com.docspot.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Published by AppointmentService when a patient cancels a booking.
 * NotificationService listens and notifies both parties.
 */
@Getter
@AllArgsConstructor
public class AppointmentCancelledEvent {

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

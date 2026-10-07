package com.docspot.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Published by AdminService when admin approves a doctor application.
 *
 * NotificationService listens and:
 *   - Emails doctor: "application approved"
 *   - Saves in-app notification for doctor
 */
@Getter
@AllArgsConstructor
public class DoctorApprovedEvent {

    private final Long   doctorUserId;
    private final String doctorName;
    private final String doctorEmail;
    private final String adminNote;    // Optional note from admin, may be null
}

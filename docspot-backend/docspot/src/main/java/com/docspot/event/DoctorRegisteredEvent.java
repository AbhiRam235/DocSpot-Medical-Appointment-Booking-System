package com.docspot.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Published by AuthService after a doctor account is created (status = PENDING).
 *
 * NotificationService listens and:
 *   - Emails admin: "new application pending review"
 *   - Emails doctor: "application received"
 *   - Saves in-app notification for doctor (visible after approval)
 */
@Getter
@AllArgsConstructor
public class DoctorRegisteredEvent {

    private final Long   doctorUserId;
    private final String doctorName;
    private final String doctorEmail;
    private final String speciality;    // For the admin email body
}

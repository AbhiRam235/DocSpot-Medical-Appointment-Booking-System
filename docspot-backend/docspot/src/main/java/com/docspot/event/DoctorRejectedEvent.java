package com.docspot.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Published by AdminService when admin rejects a doctor application.
 *
 * NotificationService listens and:
 *   - Emails doctor: "application rejected" with mandatory reason
 *   - Saves in-app notification for doctor
 */
@Getter
@AllArgsConstructor
public class DoctorRejectedEvent {

    private final Long   doctorUserId;
    private final String doctorName;
    private final String doctorEmail;
    private final String reason;       // Mandatory rejection reason
}

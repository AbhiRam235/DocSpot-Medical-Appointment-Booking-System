package com.docspot.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentResponse {
    private Long appointmentId;

    private Long doctorId;
    private String doctorName;
    private String doctorSpeciality;
    private double consultationFee;

    private Long patientId;
    private String patientName;
    private String patientEmail;
    private String patientMobile;

    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private String cancellationReason;
    private LocalDateTime bookedAt;
    private LocalDateTime updatedAt;
}

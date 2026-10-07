package com.docspot.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DoctorSummaryResponse {
    private Long   doctorId;
    private Long   userId;
    private String name;
    private String email;
    private String mobile;
    private String gender;
    private String speciality;
    private String qualification;
    private int    experienceYears;
    private double consultationFee;
    private String city;
    private String licenseNumber;
    private String status;           // PENDING | APPROVED | REJECTED
    private String rejectionReason;
    private String adminNote;
    private LocalDateTime registeredAt;
}
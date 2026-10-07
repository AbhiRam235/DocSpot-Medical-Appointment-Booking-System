package com.docspot.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DoctorDetailResponse {
    // User fields
    private Long   doctorId;
    private Long   userId;
    private String name;
    private String email;
    private String mobile;

    // Doctor fields
    private String gender;
    private String speciality;
    private String qualification;
    private int    experienceYears;
    private double consultationFee;
    private String city;
    private String bio;
    private String licenseNumber;
    private String profilePhotoPath;

    // Status fields
    private String status;
    private String rejectionReason;
    private String adminNote;

    // Related data
    private List<DocumentResponse> documents;

    private LocalDateTime registeredAt;
}
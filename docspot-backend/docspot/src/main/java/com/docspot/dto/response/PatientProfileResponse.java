package com.docspot.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientProfileResponse {
    private Long   patientId;
    private Long   userId;
    private String name;
    private String email;
    private String mobile;
    private LocalDate  dob;
    private String bloodGroup;
    private LocalDateTime registeredAt;
}

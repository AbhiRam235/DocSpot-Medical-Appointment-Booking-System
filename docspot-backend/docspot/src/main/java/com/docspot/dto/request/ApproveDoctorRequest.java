package com.docspot.dto.request;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ApproveDoctorRequest {
    // Optional note shown to the doctor in the approval email
    private String note;
}
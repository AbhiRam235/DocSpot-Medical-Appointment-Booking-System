package com.docspot.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RejectDoctorRequest {

    @NotBlank(message = "Rejection reason is mandatory")
    private String reason;
}
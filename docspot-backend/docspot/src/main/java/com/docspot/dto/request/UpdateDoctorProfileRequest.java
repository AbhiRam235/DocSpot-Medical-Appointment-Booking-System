package com.docspot.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class UpdateDoctorProfileRequest {

    @DecimalMin(value = "0.0", inclusive = false, message = "Fee must be greater than 0")
    private Double consultationFee;

    @Size(max = 100, message = "City name too long")
    private String city;

    @Size(max = 1000, message = "Bio cannot exceed 1000 characters")
    private String bio;

    private String qualification;
}
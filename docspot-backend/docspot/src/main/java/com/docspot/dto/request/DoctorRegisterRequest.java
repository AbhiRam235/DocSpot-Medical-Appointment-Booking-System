package com.docspot.dto.request;

import com.docspot.enums.Gender;
import com.docspot.enums.Speciality;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DoctorRegisterRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    private String email;

    @NotBlank(message = "Mobile number is required")
    @Pattern(
            regexp  = "^[6-9][0-9]{9}$",
            message = "Please provide a valid 10-digit Indian mobile number"
    )
    private String mobile;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 50, message = "Password must be at least 6 characters")
    private String password;

    @NotNull(message = "Gender is required")
    private Gender gender;

    @NotNull(message = "Speciality is required")
    private Speciality speciality;

    @NotBlank(message = "Qualification is required")
    private String qualification; // e.g. MBBS, MD, MS

    @NotNull(message = "Years of experience is required")
    @Min(value = 0,  message = "Experience cannot be negative")
    @Max(value = 60, message = "Please enter a valid experience value")
    private Integer experienceYears;

    @NotNull(message = "Consultation fee is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Fee must be greater than 0")
    private Double consultationFee;

    @NotBlank(message = "City is required")
    private String city;

    @Size(max = 1000, message = "Bio cannot exceed 1000 characters")
    private String bio;

    // Collected for admin review — not externally verified via API
    @NotBlank(message = "Medical license number is required")
    private String licenseNumber;
}
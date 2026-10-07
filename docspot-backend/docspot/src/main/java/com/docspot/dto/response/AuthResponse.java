package com.docspot.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    @JsonIgnore
    private String accessToken;
    @JsonIgnore
    private String refreshToken;

    private String role;       // ADMIN | DOCTOR | PATIENT
    private Long userId;
    private String name;
    private String email;
}
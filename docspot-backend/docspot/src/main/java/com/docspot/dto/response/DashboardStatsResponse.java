package com.docspot.dto.response;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DashboardStatsResponse {
    private long totalDoctors;
    private long approvedDoctors;
    private long pendingDoctors;
    private long rejectedDoctors;
    private long totalPatients;
    private long totalAppointments;
    private long todayAppointments;
}
package com.docspot.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Note the simplification versus the old session-bucket model: there's no
 * more maxPatients/bookedCount/remainingSlots per entry. Because the
 * Appointment table's unique constraint allows exactly one CONFIRMED booking
 * per doctor+date+startTime, each generated slot can only ever be
 * AVAILABLE or BOOKED — never "partially full."
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlotAvailabilityResponse {

    private Long doctorId;
    private String doctorName;
    private LocalDate date;
    private List<SlotInfo> slots;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SlotInfo {
        private LocalTime startTime;
        private LocalTime endTime;

        // AVAILABLE | BOOKED
        private String status;
    }
}

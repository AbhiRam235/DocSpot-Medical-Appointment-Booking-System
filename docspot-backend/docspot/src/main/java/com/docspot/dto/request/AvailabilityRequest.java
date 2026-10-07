package com.docspot.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * POST /api/doctor/availability body is a List<AvailabilityRequest> — each
 * item is ONE time window on ONE day (e.g. Monday 09:00-12:00 in 30-min slots).
 *
 * A doctor with a morning window and a separate evening window on the same
 * day sends TWO entries with the same dayOfWeek — there's no longer a
 * concept of "sessions" grouped under one day, each window stands alone.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityRequest {

    @NotNull(message = "Day of week is required")
    private DayOfWeek dayOfWeek;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;

    @NotNull(message = "Slot duration is required")
    @Min(value = 5, message = "Slot duration must be at least 5 minutes")
    @Max(value = 240, message = "Slot duration cannot exceed 240 minutes")
    private Integer slotDurationMinutes;
}

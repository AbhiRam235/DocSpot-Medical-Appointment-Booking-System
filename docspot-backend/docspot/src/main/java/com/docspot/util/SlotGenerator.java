package com.docspot.util;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure, stateless utility — turns a doctor's availability window
 * (startTime, endTime, slotDurationMinutes) into the list of bookable
 * slot start times.
 *
 * CRITICAL: this must be the ONLY place this logic exists. It is called
 * from two places that must never disagree:
 *
 *   1. PatientService.getAvailableSlots()  — what we SHOW a patient
 *   2. AppointmentService.book()            — what we ACCEPT as a valid booking
 *
 * If these two call sites ever compute slots differently, a patient could
 * see a slot in the UI that then gets rejected when they try to book it,
 * or worse, a patient could submit a startTime that was never actually
 * offered and have it silently accepted.
 *
 * Example: generateSlots(09:00, 10:00, 30) → [09:00, 09:30]
 * Note 10:00 itself is NOT included — the last slot must END by or before
 * the window's endTime, it can't just start before it.
 */
public final class SlotGenerator {

    private SlotGenerator() {
        // static utility — never instantiated
    }

    public static List<LocalTime> generateSlots(LocalTime start, LocalTime end, int durationMinutes) {
        List<LocalTime> slots = new ArrayList<>();
        LocalTime cursor = start;

        while (!cursor.plusMinutes(durationMinutes).isAfter(end)) {
            slots.add(cursor);
            cursor = cursor.plusMinutes(durationMinutes);
        }

        return slots;
    }
}

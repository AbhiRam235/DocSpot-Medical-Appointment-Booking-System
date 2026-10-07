import { addDays, format, parseISO } from "date-fns";
import { MAX_ADVANCE_BOOKING_DAYS } from "./enums";

/** "09:30:00" or "09:30" -> "9:30 AM" */
export function formatTime(hhmmss) {
  if (!hhmmss) return "";
  const [h, m] = hhmmss.split(":").map(Number);
  const period = h >= 12 ? "PM" : "AM";
  const hour12 = h % 12 === 0 ? 12 : h % 12;
  return `${hour12}:${String(m).padStart(2, "0")} ${period}`;
}

export function formatTimeRange(start, end) {
  return `${formatTime(start)} – ${formatTime(end)}`;
}

/** "2026-09-30" -> "Wed, 30 Sep 2026" */
export function formatDate(isoDate) {
  if (!isoDate) return "";
  return format(parseISO(isoDate), "EEE, d MMM yyyy");
}

export function todayISO() {
  return format(new Date(), "yyyy-MM-dd");
}

export function maxBookableDateISO() {
  return format(addDays(new Date(), MAX_ADVANCE_BOOKING_DAYS), "yyyy-MM-dd");
}

/**
 * The 2-hour cancellation rule, computed from the exact appointment start
 * time — there's no more session-hour lookup table (v1's SESSION_START_HOUR
 * is gone along with SlotSession itself).
 */
export function canCancel(appointmentDate, startTime) {
  const [h, m] = startTime.split(":").map(Number);
  const apptStart = new Date(appointmentDate);
  apptStart.setHours(h, m, 0, 0);

  const twoHoursFromNow = new Date(Date.now() + 2 * 60 * 60 * 1000);
  return twoHoursFromNow < apptStart;
}

export function isFutureOrToday(appointmentDate) {
  return appointmentDate >= todayISO();
}

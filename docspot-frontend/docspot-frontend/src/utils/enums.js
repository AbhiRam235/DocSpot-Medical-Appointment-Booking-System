export const GENDER = ["MALE", "FEMALE", "OTHER"];
export const DOCTOR_STATUS = ["PENDING", "APPROVED", "REJECTED"];
export const APPOINTMENT_STATUS = ["CONFIRMED", "COMPLETED", "CANCELLED"];
export const DOCUMENT_TYPE = [
  "DEGREE_CERTIFICATE",
  "MEDICAL_LICENSE",
  "IDENTITY_PROOF",
  "OTHER",
];
export const SPECIALITY = [
  "GENERAL_PHYSICIAN",
  "CARDIOLOGIST",
  "DERMATOLOGIST",
  "NEUROLOGIST",
  "ORTHOPEDIC",
  "PEDIATRICIAN",
  "PSYCHIATRIST",
  "GYNECOLOGIST",
  "OPHTHALMOLOGIST",
  "ENT_SPECIALIST",
  "DENTIST",
  "RADIOLOGIST",
  "UROLOGIST",
  "ENDOCRINOLOGIST",
  "GASTROENTEROLOGIST",
  "PULMONOLOGIST",
  "ONCOLOGIST",
  "NEPHROLOGIST",
  "RHEUMATOLOGIST",
  "OTHER",
];
export const DAY_OF_WEEK = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
];

// SlotSession (MORNING/AFTERNOON/EVENING) does not exist in the current API.
// Booking is by exact clock time (startTime) — see timeUtils.js.

export const MAX_ADVANCE_BOOKING_DAYS = 7;

export const humanize = (v) =>
  v
    .toLowerCase()
    .split("_")
    .map((w) => w[0].toUpperCase() + w.slice(1))
    .join(" ");

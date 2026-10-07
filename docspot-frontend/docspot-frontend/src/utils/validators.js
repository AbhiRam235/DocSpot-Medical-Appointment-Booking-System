import { z } from "zod";
import { GENDER, SPECIALITY } from "./enums";

const mobile = z
  .string()
  .regex(/^[6-9][0-9]{9}$/, "Enter a valid 10-digit mobile number");

const password = z.string().min(6, "Password must be at least 6 characters");

export const loginSchema = z.object({
  email: z.string().email("Enter a valid email address"),
  password: z.string().min(1, "Password is required"),
});

export const registerPatientSchema = z.object({
  name: z.string().min(1, "Name is required"),
  email: z.string().email("Enter a valid email address"),
  mobile,
  password,
  dob: z.string().optional().or(z.literal("")),
  bloodGroup: z.string().optional().or(z.literal("")),
});

export const registerDoctorSchema = z.object({
  name: z.string().min(1, "Name is required"),
  email: z.string().email("Enter a valid email address"),
  mobile,
  password,
  gender: z.enum(GENDER, { errorMap: () => ({ message: "Select a gender" }) }),
  speciality: z.enum(SPECIALITY, {
    errorMap: () => ({ message: "Select a speciality" }),
  }),
  qualification: z.string().min(1, "Qualification is required"),
  experienceYears: z.coerce
    .number()
    .min(0, "Must be 0 or more")
    .max(60, "Must be 60 or less"),
  consultationFee: z.coerce.number().positive("Must be greater than 0"),
  city: z.string().min(1, "City is required"),
  bio: z.string().optional().or(z.literal("")),
  licenseNumber: z.string().min(1, "License number is required"),
});

export const forgotPasswordSchema = z.object({
  email: z.string().email("Enter a valid email address"),
});

export const verifyOtpSchema = z.object({
  otp: z.string().regex(/^[0-9]{6}$/, "Enter the 6-digit code"),
});

export const resetPasswordSchema = z
  .object({
    newPassword: password,
    confirmPassword: z.string(),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

export const changePasswordSchema = z
  .object({
    oldPassword: z.string().min(1, "Current password is required"),
    newPassword: password,
    confirmPassword: z.string(),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

export const availabilityWindowSchema = z
  .object({
    dayOfWeek: z.string().min(1, "Select a day"),
    startTime: z.string().min(1, "Start time is required"),
    endTime: z.string().min(1, "End time is required"),
    slotDurationMinutes: z.coerce
      .number()
      .min(5, "Must be at least 5 minutes")
      .max(240, "Must be at most 240 minutes"),
  })
  .refine((data) => data.startTime < data.endTime, {
    message: "Start time must be before end time",
    path: ["endTime"],
  });

export const doctorProfileUpdateSchema = z.object({
  consultationFee: z.coerce.number().positive("Must be greater than 0").optional(),
  city: z.string().min(1).optional(),
  bio: z.string().optional(),
  qualification: z.string().min(1).optional(),
});

export const patientProfileUpdateSchema = z.object({
  name: z.string().min(1).optional(),
  mobile: mobile.optional(),
  dob: z.string().optional().or(z.literal("")),
  bloodGroup: z.string().optional().or(z.literal("")),
});

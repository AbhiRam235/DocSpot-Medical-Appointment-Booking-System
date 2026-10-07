import axiosClient from "./axiosClient";

export const getOwnProfile = () => axiosClient.get("/patient/profile");

export const updateOwnProfile = (payload) =>
  axiosClient.put("/patient/profile", payload);

export const changePassword = (payload) =>
  axiosClient.put("/patient/change-password", payload);

export const searchDoctors = (params) =>
  axiosClient.get("/patient/doctors/search", { params });

export const getDoctorPublicProfile = (doctorId) =>
  axiosClient.get(`/patient/doctors/${doctorId}`);

export const getSlots = (doctorId, date) =>
  axiosClient.get(`/patient/doctors/${doctorId}/slots`, { params: { date } });

export const bookAppointment = (payload) =>
  axiosClient.post("/patient/appointments", payload);

export const getUpcomingAppointments = (params) =>
  axiosClient.get("/patient/appointments", { params });

export const getAppointmentHistory = (params) =>
  axiosClient.get("/patient/appointments/history", { params });

export const cancelAppointment = (appointmentId) =>
  axiosClient.put(`/patient/appointments/${appointmentId}/cancel`);

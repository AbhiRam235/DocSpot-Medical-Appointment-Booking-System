import axiosClient from "./axiosClient";

export const getOwnProfile = () => axiosClient.get("/doctor/profile");

export const updateOwnProfile = (payload) =>
  axiosClient.put("/doctor/profile", payload);

export const changePassword = (payload) =>
  axiosClient.put("/doctor/change-password", payload);

export const uploadDocument = (documentType, file) => {
  const form = new FormData();
  form.append("documentType", documentType);
  form.append("file", file);
  return axiosClient.post("/doctor/documents", form, {
    headers: { "Content-Type": "multipart/form-data" },
  });
};

export const getDocuments = () => axiosClient.get("/doctor/documents");

export const addAvailabilityWindows = (windows) =>
  axiosClient.post("/doctor/availability", windows);

export const getAvailability = () => axiosClient.get("/doctor/availability");

export const updateAvailabilityWindow = (availabilityId, payload) =>
  axiosClient.put(`/doctor/availability/${availabilityId}`, payload);

export const deleteAvailabilityWindow = (availabilityId) =>
  axiosClient.delete(`/doctor/availability/${availabilityId}`);

export const getUpcomingAppointments = (params) =>
  axiosClient.get("/doctor/appointments", { params });

export const getAppointmentHistory = (params) =>
  axiosClient.get("/doctor/appointments/history", { params });

export const completeAppointment = (appointmentId) =>
  axiosClient.put(`/doctor/appointments/${appointmentId}/complete`);

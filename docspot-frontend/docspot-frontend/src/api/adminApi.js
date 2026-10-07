import axiosClient from "./axiosClient";

export const getDashboardStats = () => axiosClient.get("/admin/dashboard/stats");

export const getPendingDoctors = (params) =>
  axiosClient.get("/admin/doctors/pending", { params });

export const getAllDoctors = (params) => axiosClient.get("/admin/doctors", { params });

export const getDoctorDetail = (doctorId) =>
  axiosClient.get(`/admin/doctors/${doctorId}`);

export const approveDoctor = (doctorId, note) =>
  axiosClient.put(`/admin/doctors/${doctorId}/approve`, { note });

export const rejectDoctor = (doctorId, reason) =>
  axiosClient.put(`/admin/doctors/${doctorId}/reject`, { reason });

export const getAllPatients = (params) =>
  axiosClient.get("/admin/patients", { params });

export const getPatientDetail = (patientId) =>
  axiosClient.get(`/admin/patients/${patientId}`);

export const getAllAppointments = (params) =>
  axiosClient.get("/admin/appointments", { params });

export const deactivateDoctor = (doctorId) =>
  axiosClient.delete(`/admin/doctors/${doctorId}`);

export const deactivatePatient = (patientId) =>
  axiosClient.delete(`/admin/patients/${patientId}`);

import axiosClient from "./axiosClient";

// Note: login/refresh no longer hand back accessToken/refreshToken strings
// for the client to store — those are only ever set as HttpOnly cookies by
// the backend response headers. The JSON `data` we still read here is just
// the non-secret profile fields (role, userId, name, email) used to render
// the UI and pick a landing route.

export const login = (payload) => 
  axiosClient.post("/auth/login", payload);


export const registerPatient = (payload) =>
  axiosClient.post("/auth/register/patient", payload);

export const registerDoctor = (payload) =>
  axiosClient.post("/auth/register/doctor", payload);

export const refreshToken = () => axiosClient.post("/auth/refresh-token", {});

export const logout = () => axiosClient.post("/auth/logout", {});

export const forgotPassword = (payload) =>
  axiosClient.post("/auth/forgot-password", payload);

export const verifyOtp = (payload) => axiosClient.post("/auth/verify-otp", payload);

export const resetPassword = (payload) =>
  axiosClient.post("/auth/reset-password", payload);

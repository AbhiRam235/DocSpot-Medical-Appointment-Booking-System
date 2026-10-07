import axiosClient from "./axiosClient";

export const getNotifications = (params) =>
  axiosClient.get("/notifications", { params });

export const markAsRead = (notificationId) =>
  axiosClient.put(`/notifications/${notificationId}/read`);

export const markAllAsRead = () => axiosClient.put("/notifications/read-all");

export const getUnreadCount = () => axiosClient.get("/notifications/unread-count");

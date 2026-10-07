import { Routes, Route, Navigate } from "react-router-dom";
import ProtectedRoute from "./ProtectedRoute";

import PatientLayout from "../layouts/PatientLayout";
import DoctorLayout from "../layouts/DoctorLayout";
import AdminLayout from "../layouts/AdminLayout";

import LoginPage from "../pages/public/LoginPage";
import RegisterPatientPage from "../pages/public/RegisterPatientPage";
import RegisterDoctorPage from "../pages/public/RegisterDoctorPage";
import ForgotPasswordPage from "../pages/public/ForgotPasswordPage";
import VerifyOtpPage from "../pages/public/VerifyOtpPage";
import ResetPasswordPage from "../pages/public/ResetPasswordPage";
import UnauthorizedPage from "../pages/public/UnauthorizedPage";
import NotFoundPage from "../pages/public/NotFoundPage";

import DoctorSearchPage from "../pages/patient/DoctorSearchPage";
import PatientDoctorProfilePage from "../pages/patient/DoctorProfilePage";
import MyAppointmentsPage from "../pages/patient/MyAppointmentsPage";
import PatientProfilePage from "../pages/patient/PatientProfilePage";

import DoctorAppointmentsPage from "../pages/doctor/DoctorAppointmentsPage";
import AvailabilityPage from "../pages/doctor/AvailabilityPage";
import DoctorDocumentsPage from "../pages/doctor/DoctorDocumentsPage";
import DoctorOwnProfilePage from "../pages/doctor/DoctorProfilePage";

import AdminDashboard from "../pages/admin/AdminDashboard";
import PendingDoctorsPage from "../pages/admin/PendingDoctorsPage";
import AllDoctorsPage from "../pages/admin/AllDoctorsPage";
import DoctorDetailPage from "../pages/admin/DoctorDetailPage";
import AllPatientsPage from "../pages/admin/AllPatientsPage";
import AllAppointmentsPage from "../pages/admin/AllAppointmentsPage";

export default function AppRoutes() {
  return (
    <Routes>
      {/* Public */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register/patient" element={<RegisterPatientPage />} />
      <Route path="/register/doctor" element={<RegisterDoctorPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/verify-otp" element={<VerifyOtpPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/unauthorized" element={<UnauthorizedPage />} />

      {/* Patient */}
      <Route
        path="/patient"
        element={
          <ProtectedRoute allowedRoles={["PATIENT"]}>
            <PatientLayout />
          </ProtectedRoute>
        }
      >
        <Route path="search" element={<DoctorSearchPage />} />
        <Route path="doctors/:doctorId" element={<PatientDoctorProfilePage />} />
        <Route path="appointments" element={<MyAppointmentsPage />} />
        <Route path="profile" element={<PatientProfilePage />} />
      </Route>

      {/* Doctor */}
      <Route
        path="/doctor"
        element={
          <ProtectedRoute allowedRoles={["DOCTOR"]}>
            <DoctorLayout />
          </ProtectedRoute>
        }
      >
        <Route path="appointments" element={<DoctorAppointmentsPage />} />
        <Route path="availability" element={<AvailabilityPage />} />
        <Route path="documents" element={<DoctorDocumentsPage />} />
        <Route path="profile" element={<DoctorOwnProfilePage />} />
      </Route>

      {/* Admin */}
      <Route
        path="/admin"
        element={
          <ProtectedRoute allowedRoles={["ADMIN"]}>
            <AdminLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<AdminDashboard />} />
        <Route path="doctors/pending" element={<PendingDoctorsPage />} />
        <Route path="doctors" element={<AllDoctorsPage />} />
        <Route path="doctors/:doctorId" element={<DoctorDetailPage />} />
        <Route path="patients" element={<AllPatientsPage />} />
        <Route path="appointments" element={<AllAppointmentsPage />} />
      </Route>

      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

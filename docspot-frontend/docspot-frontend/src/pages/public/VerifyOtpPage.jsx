import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import * as authApi from "../../api/authApi";
import { verifyOtpSchema } from "../../utils/validators";
import Input from "../../components/common/Input";
import Button from "../../components/common/Button";

export default function VerifyOtpPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const email = location.state?.email;

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(verifyOtpSchema) });

  // No email in router state means this screen was reached directly
  // (refresh, bookmarked URL) rather than via the forgot-password step.
  if (!email) return <Navigate to="/forgot-password" replace />;

  const onSubmit = async ({ otp }) => {
    try {
      // data is a resetToken string (a short-lived JWT) — carried forward
      // via router state only, never persisted to storage.
      const resetToken = await authApi.verifyOtp({ email, otp });
      navigate("/reset-password", { state: { resetToken } });
    } catch (err) {
      toast.error(err.response?.data?.message || "Invalid or expired code.");
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-paper px-4">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <h1 className="font-display text-2xl font-medium text-primary">Enter the code</h1>
          <p className="mt-2 text-sm text-ink-faint">We sent a 6-digit code to {email}.</p>
        </div>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 rounded border border-border bg-white p-6">
          <Input
            label="6-digit code"
            inputMode="numeric"
            maxLength={6}
            error={errors.otp?.message}
            {...register("otp")}
          />
          <Button type="submit" loading={isSubmitting} className="w-full">
            Verify
          </Button>
        </form>
      </div>
    </div>
  );
}

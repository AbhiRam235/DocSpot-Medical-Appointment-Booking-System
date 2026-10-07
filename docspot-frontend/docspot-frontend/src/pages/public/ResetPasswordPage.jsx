import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Navigate, useLocation, useNavigate, Link } from "react-router-dom";
import toast from "react-hot-toast";
import * as authApi from "../../api/authApi";
import { resetPasswordSchema } from "../../utils/validators";
import Input from "../../components/common/Input";
import Button from "../../components/common/Button";

export default function ResetPasswordPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const resetToken = location.state?.resetToken;

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(resetPasswordSchema) });

  if (!resetToken) return <Navigate to="/forgot-password" replace />;

  const onSubmit = async ({ newPassword, confirmPassword }) => {
    try {
      await authApi.resetPassword({ resetToken, newPassword, confirmPassword });
      toast.success("Password reset. Sign in with your new password.");
      navigate("/login");
    } catch (err) {
      toast.error(err.response?.data?.message || "Couldn't reset password.");
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-paper px-4">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <h1 className="font-display text-2xl font-medium text-primary">Set a new password</h1>
        </div>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 rounded border border-border bg-white p-6">
          <Input
            label="New password"
            type="password"
            error={errors.newPassword?.message}
            {...register("newPassword")}
          />
          <Input
            label="Confirm new password"
            type="password"
            error={errors.confirmPassword?.message}
            {...register("confirmPassword")}
          />
          <Button type="submit" loading={isSubmitting} className="w-full">
            Reset password
          </Button>
        </form>
        <p className="mt-6 text-center text-sm text-ink-faint">
          <Link to="/login" className="font-medium text-primary hover:underline">
            Back to sign in
          </Link>
        </p>
      </div>
    </div>
  );
}

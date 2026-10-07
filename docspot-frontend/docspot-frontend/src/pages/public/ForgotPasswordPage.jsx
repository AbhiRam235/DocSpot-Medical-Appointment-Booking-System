import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Link, useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import * as authApi from "../../api/authApi";
import { forgotPasswordSchema } from "../../utils/validators";
import Input from "../../components/common/Input";
import Button from "../../components/common/Button";

export default function ForgotPasswordPage() {
  const navigate = useNavigate();
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(forgotPasswordSchema) });

  // The backend always returns the same success message regardless of
  // whether the email exists, to prevent account enumeration — so we always
  // move to the OTP screen on success.
  const onSubmit = async ({ email }) => {
    try {
      await authApi.forgotPassword({ email });
      navigate("/verify-otp", { state: { email } });
    } catch {
      toast.error("Something went wrong. Please try again.");
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-paper px-4">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <h1 className="font-display text-2xl font-medium text-primary">Reset your password</h1>
          <p className="mt-2 text-sm text-ink-faint">
            Enter your account email and we'll send a code.
          </p>
        </div>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 rounded border border-border bg-white p-6">
          <Input label="Email" type="email" error={errors.email?.message} {...register("email")} />
          <Button type="submit" loading={isSubmitting} className="w-full">
            Send code
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

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Link, useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import { useAuth } from "../../hooks/useAuth";
import { loginSchema } from "../../utils/validators";
import Input from "../../components/common/Input";
import Button from "../../components/common/Button";
import LoadingSpinner from "../../components/common/LoadingSpinner"

const REDIRECT_MAP = {
  ADMIN: "/admin",
  DOCTOR: "/doctor/appointments",
  PATIENT: "/patient/search",
};

export default function LoginPage() {
  const navigate = useNavigate();
  const { login, loading, user } = useAuth();

  // Handle redirect if user is already authenticated
  useEffect(() => {
    if (user?.role && REDIRECT_MAP[user.role]) {
      navigate(REDIRECT_MAP[user.role], { replace: true });
    }
  }, [user, navigate]);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(loginSchema) });

  const onSubmit = async ({ email, password }) => {
    try {
      const profile = await login(email, password);
      navigate(REDIRECT_MAP[profile.role] || "/login");
    } catch (err) {
      toast.error(err.response?.data?.message || "Couldn't log in. Check your details.");
    }
  };

  if (loading) return <LoadingSpinner full />;

  return (
    <div className="flex min-h-screen items-center justify-center bg-paper px-4">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <h1 className="font-display text-3xl font-medium text-primary">DocSpot</h1>
          <p className="mt-2 text-sm text-ink-faint">
            Sign in as a patient, doctor, or admin
          </p>
        </div>
        <form
          onSubmit={handleSubmit(onSubmit)}
          className="space-y-4 rounded border border-border bg-white p-6"
        >
          <Input
            label="Email"
            type="email"
            autoComplete="email"
            error={errors.email?.message}
            {...register("email")}
          />
          <Input
            label="Password"
            type="password"
            autoComplete="current-password"
            error={errors.password?.message}
            {...register("password")}
          />
          <div className="flex justify-end">
            <Link to="/forgot-password" className="text-xs font-medium text-primary hover:underline">
              Forgot password?
            </Link>
          </div>
          <Button type="submit" loading={isSubmitting} className="w-full">
            Sign in
          </Button>
        </form>
        <p className="mt-6 text-center text-sm text-ink-faint">
          New here?{" "}
          <Link to="/register/patient" className="font-medium text-primary hover:underline">
            Register as a patient
          </Link>{" "}
          or{" "}
          <Link to="/register/doctor" className="font-medium text-primary hover:underline">
            apply as a doctor
          </Link>
        </p>
      </div>
    </div>
  );
}

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Link, useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import * as authApi from "../../api/authApi";
import { registerPatientSchema } from "../../utils/validators";
import Input from "../../components/common/Input";
import Button from "../../components/common/Button";

export default function RegisterPatientPage() {
  const navigate = useNavigate();
  const {
    register,
    handleSubmit,
    resetField,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(registerPatientSchema)
   });

  const onSubmit = async (payload) => {
    try {
      await authApi.registerPatient(payload);
      toast.success("Account created — sign in to continue.");
      navigate("/login");
    } catch (err) {
      const data = err.response?.data;
      resetField("password");
      if (data?.data && typeof data.data === "object") {
        toast.error(Object.values(data.data)[0]);
      } else {
        toast.error(data?.message || "Registration failed.");
      }
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-paper px-4 py-10">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <h1 className="font-display text-3xl font-medium text-primary">DocSpot</h1>
          <p className="mt-2 text-sm text-ink-faint">Create your patient account</p>
        </div>
        <form
          onSubmit={handleSubmit(onSubmit)}
          className="space-y-4 rounded border border-border bg-white p-6"
        >
          <Input label="Full name" error={errors.name?.message} {...register("name")} />
          <Input label="Email" type="email" error={errors.email?.message} {...register("email")} />
          <Input label="Mobile number" error={errors.mobile?.message} {...register("mobile")} />
          <Input
            label="Password"
            type="password"
            error={errors.password?.message}
            {...register("password")}
          />
          <div className="grid grid-cols-2 gap-3">
            <Input label="Date of birth (optional)" type="date" {...register("dob")} />
            <Input label="Blood group (optional)" placeholder="O+" {...register("bloodGroup")} />
          </div>
          <Button type="submit" loading={isSubmitting} className="w-full">
            Create account
          </Button>
        </form>
        <p className="mt-6 text-center text-sm text-ink-faint">
          Already have an account?{" "}
          <Link to="/login" className="font-medium text-primary hover:underline">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}

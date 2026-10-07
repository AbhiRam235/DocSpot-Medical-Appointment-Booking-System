import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Link } from "react-router-dom";
import toast from "react-hot-toast";
import * as authApi from "../../api/authApi";
import { registerDoctorSchema } from "../../utils/validators";
import { GENDER, SPECIALITY, humanize } from "../../utils/enums";
import Input from "../../components/common/Input";
import Button from "../../components/common/Button";

export default function RegisterDoctorPage() {
  const [submitted, setSubmitted] = useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(registerDoctorSchema) });

  const onSubmit = async (payload) => {
    try {
      await authApi.registerDoctor(payload);
      setSubmitted(true);
    } catch (err) {
      const data = err.response?.data;
      if (data?.data && typeof data.data === "object") {
        toast.error(Object.values(data.data)[0]);
      } else {
        toast.error(data?.message || "Application failed.");
      }
    }
  };

  if (submitted) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-paper px-4">
        <div className="w-full max-w-md rounded border border-border bg-white p-8 text-center">
          <h1 className="font-display text-2xl font-medium text-primary">
            Application submitted
          </h1>
          <p className="mt-3 text-sm text-ink-soft">
            Thanks for applying. Our admin team reviews every doctor application before
            granting access — you'll be able to sign in once yours is approved. There's
            nothing else to do right now.
          </p>
          <Link to="/login" className="mt-6 inline-block text-sm font-medium text-primary hover:underline">
            Back to sign in
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-paper px-4 py-10">
      <div className="w-full max-w-lg">
        <div className="mb-8 text-center">
          <h1 className="font-display text-3xl font-medium text-primary">DocSpot</h1>
          <p className="mt-2 text-sm text-ink-faint">Apply to join as a doctor</p>
        </div>
        <form
          onSubmit={handleSubmit(onSubmit)}
          className="space-y-4 rounded border border-border bg-white p-6"
        >
          <div className="grid grid-cols-2 gap-3">
            <Input label="Full name" error={errors.name?.message} {...register("name")} />
            <Input label="Email" type="email" error={errors.email?.message} {...register("email")} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Input label="Mobile number" error={errors.mobile?.message} {...register("mobile")} />
            <Input
              label="Password"
              type="password"
              error={errors.password?.message}
              {...register("password")}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Input as="select" label="Gender" error={errors.gender?.message} {...register("gender")}>
              <option value="">Select</option>
              {GENDER.map((g) => (
                <option key={g} value={g}>
                  {humanize(g)}
                </option>
              ))}
            </Input>
            <Input
              as="select"
              label="Speciality"
              error={errors.speciality?.message}
              {...register("speciality")}
            >
              <option value="">Select</option>
              {SPECIALITY.map((s) => (
                <option key={s} value={s}>
                  {humanize(s)}
                </option>
              ))}
            </Input>
          </div>
          <Input
            label="Qualification"
            placeholder="MBBS, MD"
            error={errors.qualification?.message}
            {...register("qualification")}
          />
          <div className="grid grid-cols-2 gap-3">
            <Input
              label="Years of experience"
              type="number"
              error={errors.experienceYears?.message}
              {...register("experienceYears")}
            />
            <Input
              label="Consultation fee"
              type="number"
              step="0.01"
              error={errors.consultationFee?.message}
              {...register("consultationFee")}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Input label="City" error={errors.city?.message} {...register("city")} />
            <Input
              label="License number"
              error={errors.licenseNumber?.message}
              {...register("licenseNumber")}
            />
          </div>
          <Input as="textarea" rows={3} label="Bio (optional)" {...register("bio")} />
          <Button type="submit" loading={isSubmitting} className="w-full">
            Submit application
          </Button>
        </form>
        <p className="mt-6 text-center text-sm text-ink-faint">
          Already approved?{" "}
          <Link to="/login" className="font-medium text-primary hover:underline">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}

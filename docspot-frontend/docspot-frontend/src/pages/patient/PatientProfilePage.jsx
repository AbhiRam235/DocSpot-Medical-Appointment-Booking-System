import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import * as patientApi from "../../api/patientApi";
import { patientProfileUpdateSchema, changePasswordSchema } from "../../utils/validators";
import Input from "../../components/common/Input";
import Button from "../../components/common/Button";
import LoadingSpinner from "../../components/common/LoadingSpinner";

export default function PatientProfilePage() {
  const { data: profile, isLoading } = useQuery({
    queryKey: ["patient-profile"],
    queryFn: patientApi.getOwnProfile,
  });

  if (isLoading) return <LoadingSpinner full />;

  return (
    <div className="max-w-lg space-y-6">
      <h1 className="font-display text-2xl font-medium text-ink">Profile</h1>
      <ProfileForm profile={profile} />
      <ChangePasswordForm />
    </div>
  );
}

function ProfileForm({ profile }) {
  const queryClient = useQueryClient();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(patientProfileUpdateSchema) });

  useEffect(() => {
    if (profile) {
      reset({
        name: profile.name,
        mobile: profile.mobile,
        dob: profile.dob ?? "",
        bloodGroup: profile.bloodGroup ?? "",
      });
    }
  }, [profile, reset]);

  const onSubmit = async (values) => {
    try {
      await patientApi.updateOwnProfile(values);
      toast.success("Profile updated.");
      queryClient.invalidateQueries({ queryKey: ["patient-profile"] });
    } catch (err) {
      toast.error(err.response?.data?.message || "Couldn't update profile.");
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 rounded border border-border bg-white p-5">
      <h2 className="font-medium text-ink">Personal details</h2>
      <Input label="Full name" error={errors.name?.message} {...register("name")} />
      <Input label="Email" value={profile?.email ?? ""} disabled />
      <Input label="Mobile" error={errors.mobile?.message} {...register("mobile")} />
      <div className="grid grid-cols-2 gap-3">
        <Input label="Date of birth" type="date" {...register("dob")} />
        <Input label="Blood group" {...register("bloodGroup")} />
      </div>
      <Button type="submit" loading={isSubmitting}>
        Save changes
      </Button>
    </form>
  );
}

function ChangePasswordForm() {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(changePasswordSchema) });

  const onSubmit = async (values) => {
    try {
      await patientApi.changePassword(values);
      toast.success("Password changed.");
      reset();
    } catch (err) {
      toast.error(err.response?.data?.message || "Couldn't change password.");
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 rounded border border-border bg-white p-5">
      <h2 className="font-medium text-ink">Change password</h2>
      <Input label="Current password" type="password" error={errors.oldPassword?.message} {...register("oldPassword")} />
      <Input label="New password" type="password" error={errors.newPassword?.message} {...register("newPassword")} />
      <Input label="Confirm new password" type="password" error={errors.confirmPassword?.message} {...register("confirmPassword")} />
      <Button type="submit" loading={isSubmitting}>
        Update password
      </Button>
    </form>
  );
}

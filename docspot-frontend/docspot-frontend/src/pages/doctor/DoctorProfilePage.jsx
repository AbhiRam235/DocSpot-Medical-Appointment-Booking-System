import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import * as doctorApi from "../../api/doctorApi";
import { doctorProfileUpdateSchema, changePasswordSchema } from "../../utils/validators";
import { humanize } from "../../utils/enums";
import Input from "../../components/common/Input";
import Button from "../../components/common/Button";
import LoadingSpinner from "../../components/common/LoadingSpinner";
import StatusBadge from "../../components/common/StatusBadge";

export default function DoctorProfilePage() {
  const { data: profile, isLoading } = useQuery({
    queryKey: ["doctor-profile"],
    queryFn: doctorApi.getOwnProfile,
  });

  if (isLoading) return <LoadingSpinner full />;

  return (
    <div className="max-w-lg space-y-6">
      <h1 className="font-display text-2xl font-medium text-ink">Profile</h1>
      <ReadOnlyDetails profile={profile} />
      <ProfileForm profile={profile} />
      <ChangePasswordForm />
    </div>
  );
}

function ReadOnlyDetails({ profile }) {
  if (!profile) return null;
  return (
    <div className="rounded border border-border bg-white p-5">
      <div className="flex items-center gap-2">
        <h2 className="font-medium text-ink">{profile.name}</h2>
        <StatusBadge status={profile.status ?? "APPROVED"} />
      </div>
      <p className="text-sm text-primary">{humanize(profile.speciality)}</p>
      <dl className="mt-3 space-y-1 text-sm">
        <div className="flex justify-between">
          <dt className="text-ink-faint">Email</dt>
          <dd className="text-ink">{profile.email}</dd>
        </div>
        <div className="flex justify-between">
          <dt className="text-ink-faint">Mobile</dt>
          <dd className="text-ink">{profile.mobile}</dd>
        </div>
        <div className="flex justify-between">
          <dt className="text-ink-faint">Experience</dt>
          <dd className="text-ink">{profile.experienceYears} years</dd>
        </div>
        <div className="flex justify-between">
          <dt className="text-ink-faint">License number</dt>
          <dd className="text-ink">{profile.licenseNumber}</dd>
        </div>
      </dl>
      <p className="mt-3 text-xs text-ink-faint">
        Speciality, license, name, experience and gender aren't self-editable — contact
        admin support to correct any of these.
      </p>
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
  } = useForm({ resolver: zodResolver(doctorProfileUpdateSchema) });

  useEffect(() => {
    if (profile) {
      reset({
        consultationFee: profile.consultationFee,
        city: profile.city,
        bio: profile.bio ?? "",
        qualification: profile.qualification,
      });
    }
  }, [profile, reset]);

  const onSubmit = async (values) => {
    try {
      await doctorApi.updateOwnProfile(values);
      toast.success("Profile updated.");
      queryClient.invalidateQueries({ queryKey: ["doctor-profile"] });
    } catch (err) {
      toast.error(err.response?.data?.message || "Couldn't update profile.");
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 rounded border border-border bg-white p-5">
      <h2 className="font-medium text-ink">Editable details</h2>
      <Input label="Qualification" error={errors.qualification?.message} {...register("qualification")} />
      <Input label="City" error={errors.city?.message} {...register("city")} />
      <Input
        label="Consultation fee"
        type="number"
        step="0.01"
        error={errors.consultationFee?.message}
        {...register("consultationFee")}
      />
      <Input as="textarea" rows={3} label="Bio" {...register("bio")} />
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
      await doctorApi.changePassword(values);
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

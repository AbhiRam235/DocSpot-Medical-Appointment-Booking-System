import { useQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import * as doctorApi from "../../api/doctorApi";
import AvailabilityEditor from "../../components/doctor/AvailabilityEditor";
import LoadingSpinner from "../../components/common/LoadingSpinner";

export default function AvailabilityPage() {
  const queryClient = useQueryClient();
  const { data: windows = [], isLoading } = useQuery({
    queryKey: ["doctor-availability"],
    queryFn: doctorApi.getAvailability,
  });

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ["doctor-availability"] });

  const handleAdd = async (values) => {
    await doctorApi.addAvailabilityWindows([
      {
        dayOfWeek: values.dayOfWeek,
        startTime: values.startTime,
        endTime: values.endTime,
        slotDurationMinutes: values.slotDurationMinutes,
      },
    ]);
    toast.success("Window added.");
    invalidate();
  };

  const handleUpdate = async (availabilityId, payload) => {
    await doctorApi.updateAvailabilityWindow(availabilityId, payload);
    invalidate();
  };

  const handleDelete = async (availabilityId) => {
    await doctorApi.deleteAvailabilityWindow(availabilityId);
    toast.success("Window removed.");
    invalidate();
  };

  if (isLoading) return <LoadingSpinner full />;

  return (
    <div>
      <h1 className="font-display text-2xl font-medium text-ink">Weekly availability</h1>
      <p className="mt-1 max-w-2xl text-sm text-ink-faint">
        Add one or more time windows per day. Patients can only book exact times inside
        these windows, split into slots of the length you set.
      </p>
      <div className="mt-5">
        <AvailabilityEditor
          windows={windows}
          onAdd={handleAdd}
          onUpdate={handleUpdate}
          onDelete={handleDelete}
        />
      </div>
    </div>
  );
}

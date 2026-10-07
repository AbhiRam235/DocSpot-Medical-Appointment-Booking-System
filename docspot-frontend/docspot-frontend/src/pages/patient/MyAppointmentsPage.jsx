import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import * as patientApi from "../../api/patientApi";
import { usePaginatedQuery } from "../../hooks/usePaginatedQuery";
import { canCancel } from "../../utils/timeUtils";
import AppointmentCard from "../../components/appointment/AppointmentCard";
import CancelAppointmentModal from "../../components/appointment/CancelAppointmentModal";
import Pagination from "../../components/common/Pagination";
import LoadingSpinner from "../../components/common/LoadingSpinner";
import Button from "../../components/common/Button";

const TABS = [
  { key: "upcoming", label: "Upcoming" },
  { key: "history", label: "History" },
];

export default function MyAppointmentsPage() {
  const [tab, setTab] = useState("upcoming");
  const [page, setPage] = useState(0);
  const [cancelTarget, setCancelTarget] = useState(null);
  const [cancelling, setCancelling] = useState(false);
  const queryClient = useQueryClient();

  const queryFn =
    tab === "upcoming" ? patientApi.getUpcomingAppointments : patientApi.getAppointmentHistory;

  const { content, totalPages, isFirst, isLast, isLoading } = usePaginatedQuery({
    queryKey: ["patient-appointments", tab],
    queryFn,
    page,
  });

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ["patient-appointments"] });

  const handleCancel = async () => {
    setCancelling(true);
    try {
      await patientApi.cancelAppointment(cancelTarget.appointmentId);
      toast.success("Appointment cancelled.");
      setCancelTarget(null);
      invalidate();
    } catch (err) {
      toast.error(err.response?.data?.message || "Couldn't cancel that appointment.");
    } finally {
      setCancelling(false);
    }
  };

  return (
    <div>
      <h1 className="font-display text-2xl font-medium text-ink">My appointments</h1>
      <div className="mt-4 flex gap-1 border-b border-border">
        {TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => {
              setTab(t.key);
              setPage(0);
            }}
            className={`border-b-2 px-4 py-2 text-sm font-medium ${
              tab === t.key ? "border-primary text-primary" : "border-transparent text-ink-faint"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="mt-4 space-y-3">
        {isLoading ? (
          <LoadingSpinner />
        ) : content.length === 0 ? (
          <p className="py-8 text-center text-sm text-ink-faint">
            {tab === "upcoming" ? "No upcoming appointments." : "No past appointments yet."}
          </p>
        ) : (
          content.map((a) => (
            <AppointmentCard
              key={a.appointmentId}
              appointment={a}
              perspective="patient"
              actions={
                tab === "upcoming" &&
                a.status === "CONFIRMED" && (
                  <Button
                    variant="danger"
                    size="sm"
                    disabled={!canCancel(a.appointmentDate, a.startTime)}
                    onClick={() => setCancelTarget(a)}
                    title={
                      !canCancel(a.appointmentDate, a.startTime)
                        ? "Cancellation closes 2 hours before the appointment"
                        : undefined
                    }
                  >
                    Cancel
                  </Button>
                )
              }
            />
          ))
        )}
      </div>

      <Pagination page={page} totalPages={totalPages} isFirst={isFirst} isLast={isLast} onPageChange={setPage} />

      <CancelAppointmentModal
        open={!!cancelTarget}
        appointment={cancelTarget}
        loading={cancelling}
        onClose={() => setCancelTarget(null)}
        onConfirm={handleCancel}
      />
    </div>
  );
}

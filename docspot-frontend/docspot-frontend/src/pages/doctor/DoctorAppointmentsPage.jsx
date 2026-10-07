import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import * as doctorApi from "../../api/doctorApi";
import { usePaginatedQuery } from "../../hooks/usePaginatedQuery";
import { isFutureOrToday, todayISO } from "../../utils/timeUtils";
import AppointmentCard from "../../components/appointment/AppointmentCard";
import Pagination from "../../components/common/Pagination";
import LoadingSpinner from "../../components/common/LoadingSpinner";
import Button from "../../components/common/Button";

const TABS = [
  { key: "upcoming", label: "Upcoming" },
  { key: "history", label: "History" },
];

export default function DoctorAppointmentsPage() {
  const [tab, setTab] = useState("upcoming");
  const [page, setPage] = useState(0);
  const queryClient = useQueryClient();

  const queryFn =
    tab === "upcoming" ? doctorApi.getUpcomingAppointments : doctorApi.getAppointmentHistory;

  const { content, totalPages, isFirst, isLast, isLoading } = usePaginatedQuery({
    queryKey: ["doctor-appointments", tab],
    queryFn,
    page,
  });

  const handleComplete = async (appointmentId) => {
    try {
      await doctorApi.completeAppointment(appointmentId);
      toast.success("Marked as completed.");
      queryClient.invalidateQueries({ queryKey: ["doctor-appointments"] });
    } catch (err) {
      toast.error(err.response?.data?.message || "Couldn't mark as complete.");
    }
  };

  return (
    <div>
      <h1 className="font-display text-2xl font-medium text-ink">Appointments</h1>
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
          content.map((a) => {
            const isFuture = a.appointmentDate > todayISO();
            return (
              <AppointmentCard
                key={a.appointmentId}
                appointment={a}
                perspective="doctor"
                actions={
                  tab === "upcoming" &&
                  a.status === "CONFIRMED" && (
                    <Button
                      size="sm"
                      disabled={isFuture}
                      title={isFuture ? "Can't complete a future appointment yet" : undefined}
                      onClick={() => handleComplete(a.appointmentId)}
                    >
                      Mark complete
                    </Button>
                  )
                }
              />
            );
          })
        )}
      </div>

      <Pagination page={page} totalPages={totalPages} isFirst={isFirst} isLast={isLast} onPageChange={setPage} />
    </div>
  );
}

import { useQuery } from "@tanstack/react-query";
import * as adminApi from "../../api/adminApi";
import LoadingSpinner from "../../components/common/LoadingSpinner";

const CARDS = [
  { key: "totalDoctors", label: "Total doctors" },
  { key: "approvedDoctors", label: "Approved doctors" },
  { key: "pendingDoctors", label: "Pending applications" },
  { key: "rejectedDoctors", label: "Rejected applications" },
  { key: "totalPatients", label: "Total patients" },
  { key: "totalAppointments", label: "Total appointments" },
  { key: "todayAppointments", label: "Appointments today" },
];

export default function AdminDashboard() {
  const { data: stats, isLoading } = useQuery({
    queryKey: ["admin-dashboard-stats"],
    queryFn: adminApi.getDashboardStats,
  });

  if (isLoading) return <LoadingSpinner full />;

  return (
    <div>
      <h1 className="font-display text-2xl font-medium text-ink">Dashboard</h1>
      <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
        {CARDS.map((c) => (
          <div key={c.key} className="rounded border border-border bg-white p-4">
            <p className="text-2xl font-semibold text-ink">{stats?.[c.key] ?? 0}</p>
            <p className="mt-1 text-sm text-ink-faint">{c.label}</p>
          </div>
        ))}
      </div>
    </div>
  );
}

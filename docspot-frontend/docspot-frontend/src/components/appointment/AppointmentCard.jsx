import { formatDate, formatTimeRange } from "../../utils/timeUtils";
import StatusBadge from "../common/StatusBadge";

// `perspective` decides whether we show the doctor's name (patient view)
// or the patient's name (doctor/admin view).
export default function AppointmentCard({ appointment, perspective = "patient", actions }) {
  const a = appointment;
  const title = perspective === "patient" ? a.doctorName : a.patientName;
  const subtitle =
    perspective === "patient" ? a.doctorSpeciality?.replaceAll("_", " ") : a.patientEmail;

  return (
    <div className="flex flex-col gap-3 rounded border border-border bg-white p-4 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <div className="flex items-center gap-2">
          <h3 className="font-medium text-ink">{title}</h3>
          <StatusBadge status={a.status} />
        </div>
        {subtitle && <p className="text-sm text-ink-faint">{subtitle}</p>}
        <p className="mt-1 text-sm text-ink-soft">
          {formatDate(a.appointmentDate)} · {formatTimeRange(a.startTime, a.endTime)}
        </p>
        {a.status === "CANCELLED" && a.cancellationReason && (
          <p className="mt-1 text-xs text-danger">Reason: {a.cancellationReason}</p>
        )}
        {perspective === "patient" && (
          <p className="mt-1 text-xs text-ink-faint">Fee: ₹{a.consultationFee}</p>
        )}
      </div>
      {actions && <div className="flex shrink-0 gap-2">{actions}</div>}
    </div>
  );
}

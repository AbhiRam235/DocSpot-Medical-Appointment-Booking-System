import { useState } from "react";
import * as adminApi from "../../api/adminApi";
import { usePaginatedQuery } from "../../hooks/usePaginatedQuery";
import { APPOINTMENT_STATUS, humanize } from "../../utils/enums";
import { formatDate, formatTimeRange } from "../../utils/timeUtils";
import Input from "../../components/common/Input";
import Pagination from "../../components/common/Pagination";
import LoadingSpinner from "../../components/common/LoadingSpinner";
import StatusBadge from "../../components/common/StatusBadge";

const EMPTY_FILTERS = { status: "", date: "" };

export default function AllAppointmentsPage() {
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [page, setPage] = useState(0);

  const activeFilters = Object.fromEntries(
    Object.entries(filters).filter(([, v]) => v !== "")
  );

  const { content, totalPages, isFirst, isLast, isLoading } = usePaginatedQuery({
    queryKey: ["admin-all-appointments", activeFilters],
    queryFn: ({ page: p, size }) => adminApi.getAllAppointments({ ...activeFilters, page: p, size }),
    page,
  });

  const update = (key) => (e) => {
    setPage(0);
    setFilters((f) => ({ ...f, [key]: e.target.value }));
  };

  return (
    <div>
      <h1 className="font-display text-2xl font-medium text-ink">All appointments</h1>

      <div className="mt-4 grid grid-cols-1 gap-3 rounded border border-border bg-white p-4 sm:grid-cols-3">
        <Input as="select" label="Status" value={filters.status} onChange={update("status")}>
          <option value="">Any</option>
          {APPOINTMENT_STATUS.map((s) => (
            <option key={s} value={s}>
              {humanize(s)}
            </option>
          ))}
        </Input>
        <Input label="Date" type="date" value={filters.date} onChange={update("date")} />
      </div>

      {isLoading ? (
        <LoadingSpinner />
      ) : content.length === 0 ? (
        <p className="mt-8 text-center text-sm text-ink-faint">No appointments match those filters.</p>
      ) : (
        <div className="mt-4 overflow-x-auto rounded border border-border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-border bg-paper text-ink-faint">
              <tr>
                <Th>Patient</Th>
                <Th>Doctor</Th>
                <Th>Date</Th>
                <Th>Time</Th>
                <Th>Fee</Th>
                <Th>Status</Th>
              </tr>
            </thead>
            <tbody>
              {content.map((a) => (
                <tr key={a.appointmentId} className="border-b border-border last:border-b-0">
                  <Td>
                    <p className="font-medium text-ink">{a.patientName}</p>
                    <p className="text-xs text-ink-faint">{a.patientEmail}</p>
                  </Td>
                  <Td>
                    <p className="font-medium text-ink">{a.doctorName}</p>
                    <p className="text-xs text-ink-faint">{humanize(a.doctorSpeciality)}</p>
                  </Td>
                  <Td>{formatDate(a.appointmentDate)}</Td>
                  <Td>{formatTimeRange(a.startTime, a.endTime)}</Td>
                  <Td>₹{a.consultationFee}</Td>
                  <Td>
                    <StatusBadge status={a.status} />
                  </Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Pagination page={page} totalPages={totalPages} isFirst={isFirst} isLast={isLast} onPageChange={setPage} />
    </div>
  );
}

function Th({ children }) {
  return <th className="px-4 py-3 font-medium">{children}</th>;
}
function Td({ children }) {
  return <td className="px-4 py-3 align-top">{children}</td>;
}

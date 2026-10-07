import { useState } from "react";
import { Link } from "react-router-dom";
import * as adminApi from "../../api/adminApi";
import { usePaginatedQuery } from "../../hooks/usePaginatedQuery";
import { DOCTOR_STATUS, SPECIALITY, humanize } from "../../utils/enums";
import Input from "../../components/common/Input";
import Pagination from "../../components/common/Pagination";
import LoadingSpinner from "../../components/common/LoadingSpinner";
import StatusBadge from "../../components/common/StatusBadge";

const EMPTY_FILTERS = { status: "", speciality: "", city: "" };

export default function AllDoctorsPage() {
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [page, setPage] = useState(0);

  const activeFilters = Object.fromEntries(
    Object.entries(filters).filter(([, v]) => v !== "")
  );

  const { content, totalPages, isFirst, isLast, isLoading } = usePaginatedQuery({
    queryKey: ["admin-all-doctors", activeFilters],
    queryFn: ({ page: p, size }) => adminApi.getAllDoctors({ ...activeFilters, page: p, size }),
    page,
  });

  const update = (key) => (e) => {
    setPage(0);
    setFilters((f) => ({ ...f, [key]: e.target.value }));
  };

  return (
    <div>
      <h1 className="font-display text-2xl font-medium text-ink">All doctors</h1>

      <div className="mt-4 grid grid-cols-1 gap-3 rounded border border-border bg-white p-4 sm:grid-cols-3">
        <Input as="select" label="Status" value={filters.status} onChange={update("status")}>
          <option value="">Any</option>
          {DOCTOR_STATUS.map((s) => (
            <option key={s} value={s}>
              {humanize(s)}
            </option>
          ))}
        </Input>
        <Input as="select" label="Speciality" value={filters.speciality} onChange={update("speciality")}>
          <option value="">Any</option>
          {SPECIALITY.map((s) => (
            <option key={s} value={s}>
              {humanize(s)}
            </option>
          ))}
        </Input>
        <Input label="City" value={filters.city} onChange={update("city")} />
      </div>

      {isLoading ? (
        <LoadingSpinner />
      ) : content.length === 0 ? (
        <p className="mt-8 text-center text-sm text-ink-faint">No doctors match those filters.</p>
      ) : (
        <div className="mt-4 overflow-x-auto rounded border border-border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-border bg-paper text-ink-faint">
              <tr>
                <Th>Name</Th>
                <Th>Speciality</Th>
                <Th>City</Th>
                <Th>Status</Th>
                <Th />
              </tr>
            </thead>
            <tbody>
              {content.map((d) => (
                <tr key={d.doctorId} className="border-b border-border last:border-b-0">
                  <Td>
                    <p className="font-medium text-ink">{d.name}</p>
                    <p className="text-xs text-ink-faint">{d.email}</p>
                  </Td>
                  <Td>{humanize(d.speciality)}</Td>
                  <Td>{d.city}</Td>
                  <Td>
                    <StatusBadge status={d.status} />
                  </Td>
                  <Td>
                    <Link to={`/admin/doctors/${d.doctorId}`} className="font-medium text-primary hover:underline">
                      View
                    </Link>
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

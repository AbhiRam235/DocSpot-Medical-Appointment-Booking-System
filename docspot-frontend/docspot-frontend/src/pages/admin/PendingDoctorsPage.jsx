import { useState } from "react";
import { Link } from "react-router-dom";
import * as adminApi from "../../api/adminApi";
import { usePaginatedQuery } from "../../hooks/usePaginatedQuery";
import { humanize } from "../../utils/enums";
import Pagination from "../../components/common/Pagination";
import LoadingSpinner from "../../components/common/LoadingSpinner";

export default function PendingDoctorsPage() {
  const [page, setPage] = useState(0);
  const { content, totalPages, isFirst, isLast, isLoading } = usePaginatedQuery({
    queryKey: ["admin-pending-doctors"],
    queryFn: adminApi.getPendingDoctors,
    page,
  });

  return (
    <div>
      <h1 className="font-display text-2xl font-medium text-ink">Pending doctor applications</h1>

      {isLoading ? (
        <LoadingSpinner />
      ) : content.length === 0 ? (
        <p className="mt-8 text-center text-sm text-ink-faint">No pending applications.</p>
      ) : (
        <div className="mt-4 overflow-x-auto rounded border border-border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-border bg-paper text-ink-faint">
              <tr>
                <Th>Name</Th>
                <Th>Speciality</Th>
                <Th>City</Th>
                <Th>Experience</Th>
                <Th>Fee</Th>
                <Th>Applied</Th>
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
                  <Td>{d.experienceYears} yrs</Td>
                  <Td>₹{d.consultationFee}</Td>
                  <Td>{d.registeredAt?.slice(0, 10)}</Td>
                  <Td>
                    <Link to={`/admin/doctors/${d.doctorId}`} className="font-medium text-primary hover:underline">
                      Review
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

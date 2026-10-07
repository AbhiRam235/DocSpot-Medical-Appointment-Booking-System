import { useState } from "react";
import toast from "react-hot-toast";
import { useQueryClient } from "@tanstack/react-query";
import * as adminApi from "../../api/adminApi";
import { usePaginatedQuery } from "../../hooks/usePaginatedQuery";
import Pagination from "../../components/common/Pagination";
import LoadingSpinner from "../../components/common/LoadingSpinner";
import Button from "../../components/common/Button";

export default function AllPatientsPage() {
  const [page, setPage] = useState(0);
  const queryClient = useQueryClient();

  const { content, totalPages, isFirst, isLast, isLoading } = usePaginatedQuery({
    queryKey: ["admin-all-patients"],
    queryFn: adminApi.getAllPatients,
    page,
  });

  const handleDeactivate = async (patientId) => {
    try {
      await adminApi.deactivatePatient(patientId);
      toast.success("Patient deactivated.");
      queryClient.invalidateQueries({ queryKey: ["admin-all-patients"] });
    } catch (err) {
      toast.error(err.response?.data?.message || "Couldn't deactivate.");
    }
  };

  return (
    <div>
      <h1 className="font-display text-2xl font-medium text-ink">All patients</h1>

      {isLoading ? (
        <LoadingSpinner />
      ) : content.length === 0 ? (
        <p className="mt-8 text-center text-sm text-ink-faint">No patients registered yet.</p>
      ) : (
        <div className="mt-4 overflow-x-auto rounded border border-border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-border bg-paper text-ink-faint">
              <tr>
                <Th>Name</Th>
                <Th>Email</Th>
                <Th>Mobile</Th>
                <Th />
              </tr>
            </thead>
            <tbody>
              {content.map((p) => (
                <tr key={p.patientId} className="border-b border-border last:border-b-0">
                  <Td>{p.name}</Td>
                  <Td>{p.email}</Td>
                  <Td>{p.mobile}</Td>
                  <Td>
                    <Button variant="danger" size="sm" onClick={() => handleDeactivate(p.patientId)}>
                      Deactivate
                    </Button>
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

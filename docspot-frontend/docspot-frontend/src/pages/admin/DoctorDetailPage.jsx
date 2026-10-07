import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import * as adminApi from "../../api/adminApi";
import { humanize } from "../../utils/enums";
import LoadingSpinner from "../../components/common/LoadingSpinner";
import StatusBadge from "../../components/common/StatusBadge";
import Button from "../../components/common/Button";
import Modal from "../../components/common/Modal";
import Input from "../../components/common/Input";

export default function DoctorDetailPage() {
  const { doctorId } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState("");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [deactivating, setDeactivating] = useState(false);

  const { data: doctor, isLoading } = useQuery({
    queryKey: ["admin-doctor-detail", doctorId],
    queryFn: () => adminApi.getDoctorDetail(doctorId),
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["admin-doctor-detail", doctorId] });
    queryClient.invalidateQueries({ queryKey: ["admin-pending-doctors"] });
    queryClient.invalidateQueries({ queryKey: ["admin-all-doctors"] });
    queryClient.invalidateQueries({ queryKey: ["admin-dashboard-stats"] });
  };

  const handleApprove = async () => {
    setBusy(true);
    try {
      await adminApi.approveDoctor(doctorId, note || undefined);
      toast.success("Doctor approved.");
      invalidate();
    } catch (err) {
      toast.error(err.response?.data?.message || "Couldn't approve.");
    } finally {
      setBusy(false);
    }
  };

  const handleReject = async () => {
    if (!reason.trim()) {
      toast.error("A rejection reason is required.");
      return;
    }
    setBusy(true);
    try {
      await adminApi.rejectDoctor(doctorId, reason);
      toast.success("Doctor rejected.");
      setRejecting(false);
      invalidate();
    } catch (err) {
      toast.error(err.response?.data?.message || "Couldn't reject.");
    } finally {
      setBusy(false);
    }
  };

  const handleDeactivate = async () => {
    setDeactivating(true);
    try {
      await adminApi.deactivateDoctor(doctorId);
      toast.success("Doctor deactivated.");
      navigate("/admin/doctors");
    } catch (err) {
      toast.error(err.response?.data?.message || "Couldn't deactivate.");
    } finally {
      setDeactivating(false);
    }
  };

  if (isLoading) return <LoadingSpinner full />;
  if (!doctor) return null;

  return (
    <div className="max-w-2xl">
      <div className="flex items-center gap-3">
        <h1 className="font-display text-2xl font-medium text-ink">{doctor.name}</h1>
        <StatusBadge status={doctor.status} />
      </div>
      <p className="text-sm text-primary">{humanize(doctor.speciality)}</p>

      <div className="mt-5 grid grid-cols-2 gap-x-6 gap-y-2 rounded border border-border bg-white p-5 text-sm">
        <Row label="Email" value={doctor.email} />
        <Row label="Mobile" value={doctor.mobile} />
        <Row label="Gender" value={humanize(doctor.gender)} />
        <Row label="Qualification" value={doctor.qualification} />
        <Row label="Experience" value={`${doctor.experienceYears} years`} />
        <Row label="Fee" value={`₹${doctor.consultationFee}`} />
        <Row label="City" value={doctor.city} />
        <Row label="License" value={doctor.licenseNumber} />
        {doctor.rejectionReason && (
          <Row label="Rejection reason" value={doctor.rejectionReason} span />
        )}
        {doctor.adminNote && <Row label="Admin note" value={doctor.adminNote} span />}
      </div>

      {doctor.bio && (
        <div className="mt-4 rounded border border-border bg-white p-5 text-sm text-ink-soft">
          {doctor.bio}
        </div>
      )}

      <div className="mt-4 rounded border border-border bg-white p-5">
        <h2 className="font-medium text-ink">Documents</h2>
        {(!doctor.documents || doctor.documents.length === 0) ? (
          <p className="mt-2 text-sm text-ink-faint">No documents uploaded.</p>
        ) : (
          <ul className="mt-2 space-y-1 text-sm">
            {doctor.documents.map((doc) => (
              <li key={doc.documentId} className="flex justify-between">
                <span>{humanize(doc.documentType)}</span>
                <a href={doc.fileUrl} target="_blank" rel="noreferrer" className="font-medium text-primary hover:underline">
                  View
                </a>
              </li>
            ))}
          </ul>
        )}
      </div>

      {doctor.status === "PENDING" && (
        <div className="mt-4 rounded border border-border bg-white p-5">
          <h2 className="font-medium text-ink">Decision</h2>
          <div className="mt-3 space-y-3">
            <Input
              as="textarea"
              rows={2}
              label="Approval note (optional)"
              value={note}
              onChange={(e) => setNote(e.target.value)}
            />
            <div className="flex gap-2">
              <Button loading={busy} onClick={handleApprove}>
                Approve
              </Button>
              <Button variant="danger" onClick={() => setRejecting(true)}>
                Reject
              </Button>
            </div>
          </div>
        </div>
      )}

      {doctor.status === "APPROVED" && (
        <div className="mt-4">
          <Button variant="danger" loading={deactivating} onClick={handleDeactivate}>
            Deactivate doctor
          </Button>
        </div>
      )}

      <Modal
        open={rejecting}
        onClose={() => setRejecting(false)}
        title="Reject application"
        footer={
          <>
            <Button variant="secondary" onClick={() => setRejecting(false)}>
              Cancel
            </Button>
            <Button variant="danger" loading={busy} onClick={handleReject}>
              Reject
            </Button>
          </>
        }
      >
        <Input
          as="textarea"
          rows={3}
          label="Reason (required — shown to the applicant)"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </Modal>
    </div>
  );
}

function Row({ label, value, span }) {
  return (
    <div className={span ? "col-span-2" : ""}>
      <dt className="text-ink-faint">{label}</dt>
      <dd className="font-medium text-ink">{value}</dd>
    </div>
  );
}

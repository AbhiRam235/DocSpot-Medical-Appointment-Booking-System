import Modal from "../common/Modal";
import Button from "../common/Button";
import { formatDate, formatTimeRange } from "../../utils/timeUtils";

export default function CancelAppointmentModal({ open, appointment, onClose, onConfirm, loading }) {
  if (!appointment) return null;
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Cancel appointment?"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Keep appointment
          </Button>
          <Button variant="danger" loading={loading} onClick={onConfirm}>
            Cancel appointment
          </Button>
        </>
      }
    >
      <p className="text-sm text-ink-soft">
        You're about to cancel your appointment with{" "}
        <span className="font-medium text-ink">{appointment.doctorName}</span> on{" "}
        {formatDate(appointment.appointmentDate)} at{" "}
        {formatTimeRange(appointment.startTime, appointment.endTime)}. This can't be undone.
      </p>
    </Modal>
  );
}

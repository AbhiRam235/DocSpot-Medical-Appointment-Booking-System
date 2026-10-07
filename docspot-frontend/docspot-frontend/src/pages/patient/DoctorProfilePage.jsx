import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import * as patientApi from "../../api/patientApi";
import { humanize } from "../../utils/enums";
import { todayISO, maxBookableDateISO, formatDate, formatTimeRange } from "../../utils/timeUtils";
import SlotGrid from "../../components/doctor/SlotGrid";
import LoadingSpinner from "../../components/common/LoadingSpinner";
import Modal from "../../components/common/Modal";
import Button from "../../components/common/Button";

export default function DoctorProfilePage() {
  const { doctorId } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [date, setDate] = useState(todayISO());
  const [selectedSlot, setSelectedSlot] = useState(null);
  const [booking, setBooking] = useState(false);

  const { data: doctor, isLoading: loadingDoctor } = useQuery({
    queryKey: ["doctor-profile", doctorId],
    queryFn: () => patientApi.getDoctorPublicProfile(doctorId),
  });

  const { data: slotData, isLoading: loadingSlots } = useQuery({
    queryKey: ["slots", doctorId, date],
    queryFn: () => patientApi.getSlots(doctorId, date),
  });

  if (loadingDoctor) return <LoadingSpinner full />;
  if (!doctor) return null;

  const slots = slotData?.slots ?? [];

  const handleDateChange = (e) => {
    setDate(e.target.value);
    setSelectedSlot(null);
  };

  const handleConfirmBooking = async () => {
    setBooking(true);
    try {
      await patientApi.bookAppointment({
        doctorId: Number(doctorId),
        date,
        startTime: selectedSlot.startTime,
      });
      toast.success("Appointment booked.");
      queryClient.invalidateQueries({ queryKey: ["slots", doctorId, date] });
      navigate("/patient/appointments");
    } catch (err) {
      toast.error(err.response?.data?.message || "Couldn't book that slot.");
      setSelectedSlot(null);
      queryClient.invalidateQueries({ queryKey: ["slots", doctorId, date] });
    } finally {
      setBooking(false);
    }
  };

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
      <div className="lg:col-span-1">
        <div className="rounded border border-border bg-white p-5">
          <h1 className="font-display text-xl font-medium text-ink">{doctor.name}</h1>
          <p className="text-sm text-primary">{humanize(doctor.speciality)}</p>
          <dl className="mt-4 space-y-2 text-sm">
            <Row label="Qualification" value={doctor.qualification} />
            <Row label="Experience" value={`${doctor.experienceYears} years`} />
            <Row label="City" value={doctor.city} />
            <Row label="Consultation fee" value={`₹${doctor.consultationFee}`} />
          </dl>
          {doctor.bio && <p className="mt-4 text-sm text-ink-soft">{doctor.bio}</p>}
        </div>
      </div>

      <div className="lg:col-span-2">
        <div className="rounded border border-border bg-white p-5">
          <div className="flex items-end justify-between gap-4">
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-ink-soft">Date</span>
              <input
                type="date"
                value={date}
                min={todayISO()}
                max={maxBookableDateISO()}
                onChange={handleDateChange}
                className="rounded border border-border px-3 py-2 text-sm"
              />
            </label>
            <p className="text-xs text-ink-faint">
              Bookable up to {formatDate(maxBookableDateISO())}
            </p>
          </div>

          <div className="mt-5">
            {loadingSlots ? (
              <LoadingSpinner />
            ) : (
              <SlotGrid slots={slots} selectedStartTime={selectedSlot?.startTime} onSelect={setSelectedSlot} />
            )}
          </div>
        </div>
      </div>

      <Modal
        open={!!selectedSlot}
        onClose={() => setSelectedSlot(null)}
        title="Confirm appointment"
        footer={
          <>
            <Button variant="secondary" onClick={() => setSelectedSlot(null)}>
              Back
            </Button>
            <Button loading={booking} onClick={handleConfirmBooking}>
              Confirm booking
            </Button>
          </>
        }
      >
        {selectedSlot && (
          <div className="space-y-1 text-sm text-ink-soft">
            <p>
              <span className="font-medium text-ink">{doctor.name}</span> ·{" "}
              {humanize(doctor.speciality)}
            </p>
            <p>{formatDate(date)}</p>
            <p>{formatTimeRange(selectedSlot.startTime, selectedSlot.endTime)}</p>
            <p className="mt-2 font-medium text-ink">Fee: ₹{doctor.consultationFee}</p>
          </div>
        )}
      </Modal>
    </div>
  );
}

function Row({ label, value }) {
  return (
    <div className="flex justify-between">
      <dt className="text-ink-faint">{label}</dt>
      <dd className="font-medium text-ink">{value}</dd>
    </div>
  );
}

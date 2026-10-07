import { Link } from "react-router-dom";
import { humanize } from "../../utils/enums";

export default function DoctorCard({ doctor }) {
  return (
    <Link
      to={`/patient/doctors/${doctor.doctorId}`}
      className="block rounded border border-border bg-white p-4 hover:border-primary"
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="font-medium text-ink">{doctor.name}</h3>
          <p className="text-sm text-primary">{humanize(doctor.speciality)}</p>
          <p className="mt-1 text-sm text-ink-faint">
            {doctor.qualification} · {doctor.experienceYears} yrs experience
          </p>
          <p className="text-sm text-ink-faint">{doctor.city}</p>
        </div>
        <span className="shrink-0 rounded bg-primary-light px-2.5 py-1 text-sm font-semibold text-primary-dark">
          ₹{doctor.consultationFee}
        </span>
      </div>
    </Link>
  );
}

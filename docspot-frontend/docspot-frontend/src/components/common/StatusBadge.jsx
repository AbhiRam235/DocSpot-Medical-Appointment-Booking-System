const STYLES = {
  CONFIRMED: "bg-primary-light text-primary-dark",
  COMPLETED: "bg-success-light text-success",
  CANCELLED: "bg-danger-light text-danger",
  PENDING: "bg-accent-light text-accent",
  APPROVED: "bg-success-light text-success",
  REJECTED: "bg-danger-light text-danger",
  AVAILABLE: "bg-success-light text-success",
  BOOKED: "bg-ink/10 text-ink-faint",
};

export default function StatusBadge({ status }) {
  const style = STYLES[status] || "bg-ink/10 text-ink-soft";
  return (
    <span className={`inline-block rounded px-2 py-0.5 text-xs font-semibold ${style}`}>
      {status}
    </span>
  );
}

import { formatTime } from "../../utils/timeUtils";

// Slots are binary now — AVAILABLE or BOOKED — no FULL, no UNAVAILABLE.
// A time that isn't inside any of the doctor's windows for that day simply
// isn't in the array at all, so an empty array means "no availability on
// this day", not "still loading".
export default function SlotGrid({ slots, selectedStartTime, onSelect }) {
  if (slots.length === 0) {
    return (
      <div className="rounded border border-dashed border-border py-10 text-center">
        <p className="text-sm text-ink-faint">No availability on this day.</p>
        <p className="mt-1 text-xs text-ink-faint">Try another date.</p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-3 gap-2 sm:grid-cols-4">
      {slots.map((slot) => {
        const isSelected = selectedStartTime === slot.startTime;
        const isBooked = slot.status === "BOOKED";
        return (
          <button
            key={slot.startTime}
            disabled={isBooked}
            onClick={() => onSelect(slot)}
            className={`rounded border px-2 py-2 text-sm font-medium transition-colors ${
              isBooked
                ? "cursor-not-allowed border-border bg-ink/5 text-ink-faint line-through"
                : isSelected
                  ? "border-primary bg-primary text-white"
                  : "border-border bg-white text-ink hover:border-primary"
            }`}
          >
            {formatTime(slot.startTime)}
          </button>
        );
      })}
    </div>
  );
}

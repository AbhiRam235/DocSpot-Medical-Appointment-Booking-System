import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import toast from "react-hot-toast";
import { DAY_OF_WEEK, humanize } from "../../utils/enums";
import { formatTimeRange } from "../../utils/timeUtils";
import { availabilityWindowSchema } from "../../utils/validators";
import Input from "../common/Input";
import Button from "../common/Button";

// One row per weekday. Each row lists existing windows as removable chips,
// plus an "Add window" action — no more MORNING/AFTERNOON/EVENING checkbox
// grid, since a day can hold any number of arbitrary time windows now.
export default function AvailabilityEditor({ windows, onAdd, onUpdate, onDelete }) {
  const byDay = DAY_OF_WEEK.reduce((acc, day) => {
    acc[day] = windows.filter((w) => w.dayOfWeek === day);
    return acc;
  }, {});

  return (
    <div className="space-y-3">
      {DAY_OF_WEEK.map((day) => (
        <DayRow
          key={day}
          day={day}
          windows={byDay[day]}
          onAdd={onAdd}
          onUpdate={onUpdate}
          onDelete={onDelete}
        />
      ))}
    </div>
  );
}

function DayRow({ day, windows, onAdd, onUpdate, onDelete }) {
  const [adding, setAdding] = useState(false);
  const [lockMessage, setLockMessage] = useState(null);
  const [pendingId, setPendingId] = useState(null);

  const clearLock = () => setLockMessage(null);

  const handleRemove = async (window) => {
    clearLock();
    setPendingId(window.availabilityId);
    try {
      await onDelete(window.availabilityId);
    } catch (err) {
      const message = err.response?.data?.message;
      if (message) setLockMessage(message);
      else toast.error("Couldn't remove that window.");
    } finally {
      setPendingId(null);
    }
  };

  const handleToggleActive = async (window) => {
    // Toggling active is never subject to the future-booking lock — safe
    // to fire without the lock-handling wrapper above.
    try {
      await onUpdate(window.availabilityId, { active: !window.active });
    } catch {
      toast.error("Couldn't update that window.");
    }
  };

  return (
    <div className="rounded border border-border bg-white p-4">
      <div className="flex items-center justify-between">
        <h3 className="font-medium text-ink">{humanize(day)}</h3>
        <button
          onClick={() => setAdding((a) => !a)}
          className="text-sm font-medium text-primary hover:underline"
        >
          {adding ? "Cancel" : "+ Add window"}
        </button>
      </div>

      {windows.length === 0 && !adding && (
        <p className="mt-2 text-sm text-ink-faint">No windows set.</p>
      )}

      {windows.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-2">
          {windows.map((w) => (
            <span
              key={w.availabilityId}
              className={`inline-flex items-center gap-2 rounded border px-3 py-1.5 text-sm ${
                w.active ? "border-primary/40 bg-primary-light text-primary-dark" : "border-border bg-ink/5 text-ink-faint"
              }`}
            >
              {formatTimeRange(w.startTime, w.endTime)} · {w.slotDurationMinutes} min
              <button
                onClick={() => handleToggleActive(w)}
                className="text-xs font-medium underline"
                title="Toggle active"
              >
                {w.active ? "active" : "inactive"}
              </button>
              <button
                onClick={() => handleRemove(w)}
                disabled={pendingId === w.availabilityId}
                aria-label="Remove window"
                className="text-ink-faint hover:text-danger"
              >
                ✕
              </button>
            </span>
          ))}
        </div>
      )}

      {lockMessage && (
        <p className="mt-3 rounded bg-accent-light px-3 py-2 text-sm text-ink-soft">
          Can't edit — {lockMessage}
        </p>
      )}

      {adding && (
        <AddWindowForm
          day={day}
          onCancel={() => setAdding(false)}
          onSubmit={async (values) => {
            try {
              await onAdd(values);
              setAdding(false);
            } catch (err) {
              toast.error(err.response?.data?.message || "Couldn't add that window.");
            }
          }}
        />
      )}
    </div>
  );
}

function AddWindowForm({ day, onCancel, onSubmit }) {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(availabilityWindowSchema),
    defaultValues: { dayOfWeek: day, slotDurationMinutes: 30 },
  });

  return (
    <form
      onSubmit={handleSubmit(onSubmit)}
      className="mt-3 grid grid-cols-2 gap-3 rounded bg-paper p-3 sm:grid-cols-4"
    >
      <input type="hidden" {...register("dayOfWeek")} />
      <Input label="Start" type="time" error={errors.startTime?.message} {...register("startTime")} />
      <Input label="End" type="time" error={errors.endTime?.message} {...register("endTime")} />
      <Input
        label="Slot length (min)"
        type="number"
        error={errors.slotDurationMinutes?.message}
        {...register("slotDurationMinutes")}
      />
      <div className="flex items-end gap-2">
        <Button type="submit" size="sm" loading={isSubmitting}>
          Save
        </Button>
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  );
}

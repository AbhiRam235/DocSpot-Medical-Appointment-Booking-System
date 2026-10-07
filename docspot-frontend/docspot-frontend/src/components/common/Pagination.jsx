import Button from "./Button";

// `page` is 0-indexed, matching the backend's Page<T>.number.
export default function Pagination({ page, totalPages, onPageChange, isFirst, isLast }) {
  if (totalPages <= 1) return null;

  return (
    <div className="mt-4 flex items-center justify-between">
      <span className="text-sm text-ink-faint">
        Page {page + 1} of {totalPages}
      </span>
      <div className="flex gap-2">
        <Button
          variant="secondary"
          size="sm"
          disabled={isFirst}
          onClick={() => onPageChange(page - 1)}
        >
          Previous
        </Button>
        <Button
          variant="secondary"
          size="sm"
          disabled={isLast}
          onClick={() => onPageChange(page + 1)}
        >
          Next
        </Button>
      </div>
    </div>
  );
}

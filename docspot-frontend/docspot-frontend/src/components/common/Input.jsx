import { forwardRef } from "react";

const Input = forwardRef(function Input(
  { label, error, as = "input", children, className = "", ...props },
  ref
) {
  const Tag = as;
  const fieldClasses = `w-full rounded border px-3 py-2 text-sm text-ink placeholder:text-ink-faint focus:border-primary focus:outline-none ${
    error ? "border-danger" : "border-border"
  } ${className}`;
  return (
    <label className="block">
      {label && (
        <span className="mb-1 block text-sm font-medium text-ink-soft">{label}</span>
      )}
      <Tag ref={ref} className={fieldClasses} {...props}>
        {children}
      </Tag>
      {error && <span className="mt-1 block text-xs text-danger">{error}</span>}
    </label>
  );
});

export default Input;

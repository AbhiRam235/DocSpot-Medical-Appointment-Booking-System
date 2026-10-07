const VARIANTS = {
  primary: "bg-primary text-white hover:bg-primary-dark disabled:bg-ink-faint",
  secondary:
    "bg-white text-ink border border-border hover:border-ink-faint disabled:text-ink-faint",
  danger: "bg-danger text-white hover:bg-danger/90 disabled:bg-ink-faint",
  ghost: "bg-transparent text-ink-soft hover:bg-primary-light disabled:text-ink-faint",
};

export default function Button({
  variant = "primary",
  size = "md",
  loading = false,
  disabled = false,
  className = "",
  children,
  ...props
}) {
  const sizeClasses = size === "sm" ? "px-3 py-1.5 text-sm" : "px-4 py-2.5 text-sm";
  return (
    <button
      disabled={disabled || loading}
      className={`inline-flex items-center justify-center gap-2 rounded font-semibold transition-colors disabled:cursor-not-allowed ${VARIANTS[variant]} ${sizeClasses} ${className}`}
      {...props}
    >
      {loading && (
        <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent" />
      )}
      {children}
    </button>
  );
}

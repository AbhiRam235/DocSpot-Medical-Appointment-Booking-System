export default function LoadingSpinner({ full = false }) {
  const spinner = (
    <span className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
  );

  if (full) {
    return (
      <div className="flex h-full min-h-[300px] w-full items-center justify-center">
        {spinner}
      </div>
    );
  }
  return <div className="flex justify-center py-6">{spinner}</div>;
}

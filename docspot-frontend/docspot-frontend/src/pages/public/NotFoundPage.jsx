import { Link } from "react-router-dom";

export default function NotFoundPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-paper px-4 text-center">
      <h1 className="font-display text-3xl font-medium text-ink">Page not found</h1>
      <p className="mt-2 max-w-sm text-sm text-ink-faint">
        The page you're looking for doesn't exist or may have moved.
      </p>
      <Link to="/login" className="mt-6 font-medium text-primary hover:underline">
        Back to sign in
      </Link>
    </div>
  );
}

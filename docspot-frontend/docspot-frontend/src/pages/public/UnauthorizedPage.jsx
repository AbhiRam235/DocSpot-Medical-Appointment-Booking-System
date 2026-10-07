import { Link } from "react-router-dom";

export default function UnauthorizedPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-paper px-4 text-center">
      <h1 className="font-display text-3xl font-medium text-ink">You don't have access to this page</h1>
      <p className="mt-2 max-w-sm text-sm text-ink-faint">
        Your account role doesn't include this section. Try going back, or sign in with a
        different account.
      </p>
      <Link to="/login" className="mt-6 font-medium text-primary hover:underline">
        Back to sign in
      </Link>
    </div>
  );
}

import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";

const NAV = [
  { to: "/admin", label: "Dashboard", end: true },
  { to: "/admin/doctors/pending", label: "Pending doctors" },
  { to: "/admin/doctors", label: "All doctors" },
  { to: "/admin/patients", label: "All patients" },
  { to: "/admin/appointments", label: "All appointments" },
];

export default function AdminLayout() {
  const { user, logout } = useAuth();

  return (
    <div className="flex min-h-screen">
      <aside className="hidden w-56 shrink-0 border-r border-border bg-white sm:block">
        <div className="px-5 py-5">
          <span className="font-display text-lg font-medium text-primary">DocSpot</span>
          <p className="mt-0.5 text-xs text-ink-faint">Admin console</p>
        </div>
        <nav className="flex flex-col gap-0.5 px-3">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `rounded px-3 py-2 text-sm font-medium ${
                  isActive ? "bg-primary-light text-primary-dark" : "text-ink-soft hover:bg-paper"
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="flex-1">
        <header className="flex items-center justify-between border-b border-border bg-white px-4 py-3 sm:px-6">
          <span className="font-display text-base font-medium text-primary sm:hidden">DocSpot Admin</span>
          <span className="hidden text-sm text-ink-soft sm:inline">{user?.name}</span>
          <button onClick={logout} className="rounded px-3 py-2 text-sm font-medium text-ink-soft hover:bg-paper">
            Log out
          </button>
        </header>
        <nav className="flex gap-1 overflow-x-auto border-b border-border px-4 py-2 sm:hidden">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `whitespace-nowrap rounded px-3 py-1.5 text-xs font-medium ${
                  isActive ? "bg-primary-light text-primary-dark" : "text-ink-soft"
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

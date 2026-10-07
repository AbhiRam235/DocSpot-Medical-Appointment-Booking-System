import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";
import NotificationBell from "../components/notification/NotificationBell";

const NAV = [
  { to: "/patient/search", label: "Find a doctor" },
  { to: "/patient/appointments", label: "My appointments" },
  { to: "/patient/profile", label: "Profile" },
];

export default function PatientLayout() {
  const { user, logout } = useAuth();

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-30 border-b border-border bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3 sm:px-6">
          <div className="flex items-center gap-8">
            <span className="font-display text-lg font-medium text-primary">DocSpot</span>
            <nav className="hidden gap-1 sm:flex">
              {NAV.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
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
          </div>
          <div className="flex items-center gap-3">
            <NotificationBell />
            <span className="hidden text-sm text-ink-soft sm:inline">{user?.name}</span>
            <button
              onClick={logout}
              className="rounded px-3 py-2 text-sm font-medium text-ink-soft hover:bg-paper"
            >
              Log out
            </button>
          </div>
        </div>
        <nav className="flex gap-1 border-t border-border px-4 py-2 sm:hidden">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `rounded px-3 py-1.5 text-xs font-medium ${
                  isActive ? "bg-primary-light text-primary-dark" : "text-ink-soft"
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6">
        <Outlet />
      </main>
    </div>
  );
}

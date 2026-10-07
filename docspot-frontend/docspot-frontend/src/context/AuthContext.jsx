import { createContext, useCallback, useEffect, useMemo, useState } from "react";
import * as authApi from "../api/authApi";

export const AuthContext = createContext(null);

// We never store accessToken/refreshToken anywhere in JS-reachable storage —
// they live only in HttpOnly cookies set by the backend. What we do keep,
// in sessionStorage, is the small non-secret profile echo the login
// response includes (role, userId, name, email) so a page refresh can
// redraw the right nav/layout instantly instead of flashing a blank state.
// It is a UI convenience, not a credential, and it is NOT trusted for
// authorization — every protected API call still relies on the browser
// sending the real cookie, and a 401 from the backend is what actually
// gates access. If someone tampers with this sessionStorage value with no
// valid cookie, the very first API call simply 401s and they're bounced to
// /login.
const PROFILE_KEY = "docspot_profile";

function readStoredProfile() {
  try {
    const raw = sessionStorage.getItem(PROFILE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function storeProfile(profile) {
  try {
    if (profile) sessionStorage.setItem(PROFILE_KEY, JSON.stringify(profile));
    else sessionStorage.removeItem(PROFILE_KEY);
  } catch {
    // sessionStorage unavailable (private mode etc.) — non-fatal, session
    // still works via the cookie, it just won't survive a hard refresh.
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => readStoredProfile());
  const [loading, setLoading] = useState(true);

  useEffect(() => {
  let isMounted = true; // 1. Flag to prevent state updates on unmounted components

  async function verifySession() {
    try {
      // 2. Send request to backend. Browser automatically attaches HttpOnly cookie.
      const data = await authApi.refreshToken();
      
      // 3. If request succeeds AND component is still on screen, update state
      if (isMounted) {
        const profile = { role: data.role, userId: data.userId, name: data.name, email: data.email };
        storeProfile(profile);
        setUser(profile);
      }
    } catch (err) {
      // 4. If request fails (401/403/network error), clear cached state
      if (isMounted) {
        storeProfile(null);
        setUser(null);
      }
    } finally {
      // 5. Turn off loading state so ProtectedRoute knows revalidation is complete
      if (isMounted) setLoading(false);
    }
  }

  verifySession();

  // 6. Cleanup function runs if the component unmounts before API call finishes
  return () => {
    isMounted = false;
  };
}, []);


  // If a refresh attempt anywhere in the app fails (see axiosClient), the
  // session is over — drop the local profile echo and go to login.
  useEffect(() => {
    const onExpired = () => {
      storeProfile(null);
      setUser(null);
      if (!window.location.pathname.startsWith("/login")) {
        window.location.href = "/login";
      }
    };
    window.addEventListener("docspot:session-expired", onExpired);
    return () => window.removeEventListener("docspot:session-expired", onExpired);
  }, []);


  const login = useCallback(async (email, password) => {
    setLoading(true);
    try {
      const data = await authApi.login({ email, password });
      // data: { role, userId, name, email } — tokens arrived as Set-Cookie
      // headers on this same response and are already stored by the browser.
      const profile = {
        role: data.role,
        userId: data.userId,
        name: data.name,
        email: data.email,
      };
      storeProfile(profile);
      setUser(profile);
      return profile;
    } finally {
      setLoading(false);
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      storeProfile(null);
      setUser(null);
    }
  }, []);

  const value = useMemo(
    () => ({
      user,
      isAuthenticated: !!user,
      login,
      logout,
      loading,
    }),
    [user, login, logout, loading]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

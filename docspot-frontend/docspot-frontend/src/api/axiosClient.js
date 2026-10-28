import axios from "axios";

/**
 * Base Axios instance.
 * - withCredentials: true ensures HttpOnly cookies travel with every request.
 * - baseURL is the relative "/api":
 *     dev  -> Vite proxy        (localhost:5173/api -> localhost:8080/api)
 *     prod -> Vercel rewrite    (your-site.vercel.app/api -> Render /api)
 *   so the browser only ever talks to ONE origin and cookies stay first-party.
 *   Do NOT set VITE_API_BASE_URL in Vercel.
 */
const axiosClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
  withCredentials: true,
});

// ============================================================================
// CSRF (double-submit cookie)
// Backend sets a readable XSRF-TOKEN cookie; we echo it in X-XSRF-TOKEN on
// every state-changing request. A third-party site can't read our cookie,
// so it can't forge the header.
// ============================================================================
const MUTATING_METHODS = ["post", "put", "patch", "delete"];

export function getCookie(name) {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

/** Call once on app start so the XSRF-TOKEN cookie exists before the first POST. */
export function initCsrf() {
  return axiosClient.get("/auth/csrf").catch(() => {});
}

axiosClient.interceptors.request.use((config) => {
  if (MUTATING_METHODS.includes(config.method?.toLowerCase())) {
    const token = getCookie("XSRF-TOKEN");
    if (token) {
      config.headers["X-XSRF-TOKEN"] = token;
    }
  }
  return config;
});

// Flag to track whether a refresh-token API call is currently in progress
let isRefreshing = false;

// Queue array to hold pending requests that fail with 401 while a refresh is happening
let refreshQueue = [];

// ============================================================================
// RESPONSE INTERCEPTOR
// Handles unwrapping backend responses and catching 401 Unauthorized errors
// ============================================================================
axiosClient.interceptors.response.use(
  // --------------------------------------------------------------------------
  // SUCCESS HANDLER (HTTP 2xx statuses)
  // --------------------------------------------------------------------------
  (response) => {
    // Backend envelope: { success, message, data }
    if (
      response.data &&
      Object.prototype.hasOwnProperty.call(response.data, "data")
    ) {
      return response.data.data;
    }
    return response.data;
  },

  // --------------------------------------------------------------------------
  // ERROR HANDLER (HTTP 4xx/5xx statuses or network failures)
  // --------------------------------------------------------------------------
  async (error) => {
    const originalRequest = error.config;
    const status = error.response?.status;

    // Auth endpoints (login, refresh, logout...) must never trigger a refresh
    const isAuthEndpoint = originalRequest?.url?.includes("/auth/");

    // ========================================================================
    // REFRESH TOKEN LOGIC
    // 401 = access token expired/invalid. Not retried twice, not for /auth/*.
    // ========================================================================
    if (status === 401 && !originalRequest._retry && !isAuthEndpoint) {

      // CASE 1: A refresh is already in progress -> queue this request
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          refreshQueue.push({ resolve, reject, originalRequest });
        });
      }

      // CASE 2: First 401 -> trigger the refresh
      originalRequest._retry = true;
      isRefreshing = true;

      try {
        // Bare axios (no response unwrapping / no 401 handling), so we must
        // attach the CSRF header ourselves: refresh-token is CSRF-protected.
        await axios.post(
          `${axiosClient.defaults.baseURL}/auth/refresh-token`,
          {},
          {
            withCredentials: true,
            headers: { "X-XSRF-TOKEN": getCookie("XSRF-TOKEN") ?? "" },
          }
        );

        // Retry everything that was waiting
        refreshQueue.forEach(({ resolve, originalRequest: req }) => {
          resolve(axiosClient(req));
        });
        refreshQueue = [];

        // Retry the request that triggered the refresh
        return axiosClient(originalRequest);

      } catch (refreshError) {
        // Refresh failed: reject everyone waiting, tell the app to log out
        refreshQueue.forEach(({ reject }) => reject(refreshError));
        refreshQueue = [];

        window.dispatchEvent(new CustomEvent("docspot:session-expired"));

        return Promise.reject(refreshError);

      } finally {
        isRefreshing = false;
      }
    }

    // Everything else (400, 403, 404, 500, ...) goes straight to the caller
    return Promise.reject(error);
  }
);

export default axiosClient;
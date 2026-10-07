import axios from "axios";

/**
 * Base Axios instance.
 * - withCredentials: true ensures HttpOnly cookies travel with every request.
 * - baseURL uses relative "/api" so requests route through Vite dev proxy
 *   (e.g., http://localhost:5173/api -> http://localhost:8080/api),
 *   preventing cross-origin cookie-blocking issues.
 */
const axiosClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
  withCredentials: true,
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
    // Check if the response matches the backend envelope { success, message, data }
    if (
      response.data &&
      Object.prototype.hasOwnProperty.call(response.data, "data")
    ) {
      // Return only the payload (`data`), skipping unnecessary envelope metadata
      return response.data.data;
    }
    // Return standard response data if no nested `.data` property exists
    return response.data;
  },

  // --------------------------------------------------------------------------
  // ERROR HANDLER (HTTP 4xx/5xx statuses or network failures)
  // --------------------------------------------------------------------------
  async (error) => {
    // Extract configuration of the failed request to enable retries
    const originalRequest = error.config;

    // Extract HTTP status code (e.g., 401)
    const status = error.response?.status;

    // Check if the failing request is an authentication endpoint (e.g., /auth/login)
    const isAuthEndpoint = originalRequest?.url?.includes("/auth/");

    // ========================================================================
    // REFRESH TOKEN LOGIC
    // Conditions:
    // 1. Status must be 401 (Unauthorized - Access Token expired/invalid)
    // 2. !originalRequest._retry: Prevents infinite retry loops if retried call fails again
    // 3. !isAuthEndpoint: Excludes auth calls to prevent infinite loops on failed logins/refreshes
    // ========================================================================
    if (status === 401 && !originalRequest._retry && !isAuthEndpoint) {
      
      // ----------------------------------------------------------------------
      // CASE 1: A refresh request is ALREADY in progress (Concurrent 401s)
      // ----------------------------------------------------------------------
      if (isRefreshing) {
        // Return a pending Promise to pause this request until token refresh completes
        return new Promise((resolve, reject) => {
          // Push resolve, reject, and original request configuration into queue
          refreshQueue.push({ resolve, reject, originalRequest });
        });
      }

      // ----------------------------------------------------------------------
      // CASE 2: This is the FIRST request to hit 401 (Triggers the refresh)
      // ----------------------------------------------------------------------
      // Mark original request so it won't trigger another refresh if retried call fails
      originalRequest._retry = true;
      
      // Lock the system so subsequent failed requests go into `refreshQueue`
      isRefreshing = true;

      try {
        // 1. Call the backend refresh-token endpoint (uses HttpOnly Refresh Cookie)
        await axios.post(
          `${axiosClient.defaults.baseURL}/auth/refresh-token`,
          {},
          { withCredentials: true }
        );

        // 2. Refresh succeeded! Iterate through all queued requests and retry them
        refreshQueue.forEach(({ resolve, originalRequest: req }) => {
          // Resolve the pending promise by re-executing request with new cookie attached
          resolve(axiosClient(req));
        });

        // 3. Clear the queue array after processing all waiting requests
        refreshQueue = [];

        // 4. Retry and return the original (first) request that triggered the refresh
        return axiosClient(originalRequest);

      } catch (refreshError) {
        // --------------------------------------------------------------------
        // REFRESH FAILED: Refresh token is expired, missing, or invalid
        // --------------------------------------------------------------------
        // 1. Reject all waiting promises in queue so UI/component catch blocks trigger
        refreshQueue.forEach(({ reject }) => reject(refreshError));
        
        // Clear the queue array
        refreshQueue = [];

        // 2. Dispatch global event to notify app (e.g., clear user state, redirect to /login)
        window.dispatchEvent(new CustomEvent("docspot:session-expired"));

        // 3. Reject the original request promise
        return Promise.reject(refreshError);

      } finally {
        // Unlock the refresh flag regardless of success or failure
        isRefreshing = false;
      }
    }

    // Pass through any other errors (e.g., 400, 403, 404, 500) directly to the calling code
    return Promise.reject(error);
  }
);

export default axiosClient;
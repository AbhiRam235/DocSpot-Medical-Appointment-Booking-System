import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Dev server proxies /api to the Spring Boot backend so cookies set by the
// backend (Set-Cookie: HttpOnly) are treated as same-origin by the browser.
// This avoids third-party-cookie blocking that a cross-origin XHR would hit.


export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080", // Proxies http://localhost:5173/api -> http://localhost:8080/api
        changeOrigin: true,
        secure: false,
      },
    },
  },
});
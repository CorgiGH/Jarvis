import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import path from "node:path";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  base: "/tutor/",
  build: {
    outDir: path.resolve(__dirname, "../src/main/resources/tutor-dist"),
    emptyOutDir: true,
    sourcemap: true,
  },
  server: {
    port: 5173,
    proxy: {
      // Backend default port is 8080 (WebMain.kt DEFAULT_PORT). Override with JARVIS_PORT env on backend side.
      "/api": "http://localhost:8080",
      "/auth": "http://localhost:8080",
      // "/tutor" proxy intentionally OMITTED in dev — vite serves the SPA directly at /tutor/* so React Router routes resolve without 404 from backend.
      // Re-enable for prod-mirror testing if needed.
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/setupTests.ts"],
  },
});

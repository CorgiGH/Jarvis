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
      "/tutor": "http://localhost:8080",
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/setupTests.ts"],
  },
});

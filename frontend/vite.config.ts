import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ command }) => ({
  base: command === "serve" ? "/" : "/miniapp/",
  plugins: [react()],
  server: {
    port: 5173,
    allowedHosts: ["landless-upstairs-drainage.ngrok-free.dev"],
    proxy: {
      "/api": "http://localhost:8080",
      "/line/login": "http://localhost:8080",
    },
  },
  build: {
    outDir: "../src/main/resources/static/miniapp",
    emptyOutDir: true,
  },
}));
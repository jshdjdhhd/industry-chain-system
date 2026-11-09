import { defineConfig } from "vitest/config";
import vue from "@vitejs/plugin-vue";
import type { LoggingFunction, RollupLog } from "rollup";
import { fileURLToPath, URL } from "node:url";

function ignoreKnownRollupWarnings(warning: RollupLog, warn: LoggingFunction) {
  if (warning.code === "INVALID_ANNOTATION") return;
  warn(warning);
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      "/api/matching/ai-chat/stream": {
        target: "http://localhost:8080",
        changeOrigin: true,
        // SSE 流式不能缓冲
        headers: { "X-Accel-Buffering": "no" },
      },
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  build: {
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      onwarn: ignoreKnownRollupWarnings,
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
  },
});

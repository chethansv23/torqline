/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In dev (npm run dev) API calls go to the gateway on :8080. In Docker, nginx does the same proxying.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['src/test/setup.ts'],
    restoreMocks: true,
    coverage: { include: ['src/**/*.{ts,tsx}'], exclude: ['src/main.tsx', 'src/test/**', 'src/**/*.test.*'] },
  },
});

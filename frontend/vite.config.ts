import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the SPA calls /api on the Vite server, which forwards to the Spring Boot API.
// Point it elsewhere with PULSE_API_TARGET, e.g. PULSE_API_TARGET=http://localhost:8081 npm run dev
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': process.env.PULSE_API_TARGET ?? 'http://localhost:8080',
    },
  },
});

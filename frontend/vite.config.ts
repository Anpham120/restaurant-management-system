import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev: /api đi qua proxy tới backend (mặc định compose app trên 127.0.0.1:8081) → không cần CORS.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': process.env.API_TARGET ?? 'http://127.0.0.1:8081',
    },
  },
});

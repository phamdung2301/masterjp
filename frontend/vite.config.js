import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Proxy bắt buộc: API và audio tĩnh đều đi qua backend :8080.
// KHÔNG hardcode localhost trong axios client (baseURL '/api').
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/audio': 'http://localhost:8080',
    },
  },
});

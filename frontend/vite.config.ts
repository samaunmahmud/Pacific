import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// In development the API runs on :8080; proxying /api means the browser sees one origin (no CORS needed).
// Set API_PROXY to point at a different backend (e.g. API_PROXY=http://localhost:8081 npm run dev).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: { '/api': env.API_PROXY || 'http://localhost:8080' },
    },
  };
});

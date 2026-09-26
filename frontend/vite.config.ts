import { defineConfig, loadEnv, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Content Security Policy for production builds: scripts and API calls only from the shop's own origin (the API is
 * served under /api on the same origin), so an injected script can't run or send a signed-in shopper's token
 * elsewhere. Product photos may come from any https address; blob: is for photo previews before upload. Inline
 * style attributes are allowed because React components use them. Not applied in development, where Vite needs
 * inline scripts. frame-ancestors can't be set in a meta tag: send it as a header from the web server too.
 */
const CSP = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' https: data: blob:",
  "font-src 'self'",
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-src 'none'",
].join('; ');

const contentSecurityPolicy = (): Plugin => ({
  name: 'pacific-csp',
  apply: 'build',
  transformIndexHtml: (html) => html.replace('<head>', `<head>\n    <meta http-equiv="Content-Security-Policy" content="${CSP}" />`),
});

// In development the API runs on :8080; proxying /api means the browser sees one origin (no CORS needed).
// Set API_PROXY to point at a different backend (e.g. API_PROXY=http://localhost:8081 npm run dev).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const proxy = { '/api': env.API_PROXY || 'http://localhost:8080' };
  return {
    plugins: [react(), contentSecurityPolicy()],
    server: { port: 5173, proxy },
    preview: { proxy },
  };
});

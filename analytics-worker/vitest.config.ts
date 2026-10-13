import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

// Node has no module `cloudflare:workers`. The tests use a stand-in.
export default defineConfig({
  resolve: { alias: { 'cloudflare:workers': fileURLToPath(new URL('./test/cloudflare-workers.ts', import.meta.url)) } },
});

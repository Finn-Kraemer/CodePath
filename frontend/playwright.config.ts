import { defineConfig } from '@playwright/test';

/**
 * End-to-End-Test gegen eine laufende Instanz (App + PostgreSQL), z. B. aus docker compose.
 *   E2E_BASE_URL=http://localhost:8080 E2E_ADMIN_PASSWORD=... pnpm test:e2e
 */
export default defineConfig({
	testDir: 'e2e',
	timeout: 60_000,
	retries: 0,
	use: {
		baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080',
		locale: 'de-DE',
		trace: 'retain-on-failure'
	}
});

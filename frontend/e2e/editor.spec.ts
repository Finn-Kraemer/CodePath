import { expect, test } from '@playwright/test';

const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin-pw-123';

/** Gate von Phase 3: neue Aufgabe im Editor anlegen, prüfen, veröffentlichen – ohne Deployment. */
test('Aufgabe im Editor anlegen und veröffentlichen', async ({ page }) => {
	const slug = `e2e-${Date.now().toString(36)}`;
	await page.goto('/login');
	await page.fill('#username', 'admin');
	await page.fill('#password', ADMIN_PASSWORD);
	await page.click('button[type=submit]');
	await expect(page).toHaveURL(/\/modules/);

	await page.goto('/admin/content');
	const gitSection = page
		.locator('section', { hasText: 'git' })
		.filter({ hasText: 'Warum brauchen wir Git?' });
	await gitSection.getByLabel('Neue Aufgabe: Titel').fill('Was macht git clone?');
	await gitSection.getByLabel('Slug').fill(slug);
	await gitSection.getByRole('button', { name: 'Anlegen' }).click();
	await expect(page).toHaveURL(/\/admin\/content\/\d+/);

	await page.getByLabel('Auftrag (Markdown)').fill('Was macht `git clone`?');
	await page
		.getByLabel('Antworttext')
		.first()
		.fill('Es kopiert ein entferntes Repository auf den eigenen Rechner');
	await page.getByLabel('Antworttext').nth(1).fill('Es löscht das Repository');
	await page.getByRole('button', { name: 'Entwurf speichern' }).click();
	await expect(page.getByRole('status')).toContainText('Entwurf gespeichert');

	await page.getByRole('button', { name: 'Prüfen', exact: true }).click();
	await expect(page.getByText('Prüfung bestanden')).toBeVisible();
	await page.getByRole('button', { name: 'Veröffentlichen' }).click();
	await expect(page.getByRole('status')).toContainText('Veröffentlicht');
	await expect(page.getByText(/veröffentlicht: v1/)).toBeVisible();

	// Lehrkraft sieht die Aufgabe sofort in der Schüleransicht
	await page.goto(`/modules/git/${slug}`);
	await expect(page.getByRole('heading', { name: 'Was macht git clone?' })).toBeVisible();
});

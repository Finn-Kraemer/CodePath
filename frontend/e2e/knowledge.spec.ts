import { expect, test } from '@playwright/test';

const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin-pw-123';

test.beforeEach(async ({ page }) => {
	await page.goto('/login');
	await page.fill('#username', 'admin');
	await page.fill('#password', ADMIN_PASSWORD);
	await page.click('button[type=submit]');
	await expect(page).toHaveURL(/\/modules/);
});

/** Volltextsuche: Treffer mit Ausschnitt, Fundstelle im Artikel markiert. */
test('Wissensdatenbank durchsuchen', async ({ page }) => {
	await page.goto('/knowledge');
	await page.getByLabel('Wissensdatenbank durchsuchen').fill('commit');
	const results = page.getByRole('region', { name: 'Suchergebnisse' });
	const hit = results.getByRole('button', { name: /Versionskontrolle/ });
	await expect(hit.locator('mark').first()).toBeVisible();
	await hit.click();
	await expect(page).toHaveURL(/artikel=git&suche=commit/);
	await expect(page.getByRole('heading', { level: 2, name: /Versionskontrolle/ })).toBeVisible();
	await expect(
		page.locator('article, .knowledge').locator('mark.search-hit').first()
	).toBeVisible();

	await page.getByLabel('Wissensdatenbank durchsuchen').fill('gibtesnichtxyz');
	await expect(results).toContainText('Nichts gefunden');
});

/** Aufgabe zeigt den passenden Artikel (hier: Artikel zum Modul) und führt dorthin. */
test('Aufgabe verlinkt den passenden Wissensartikel', async ({ page }) => {
	await page.goto('/modules/datenbanken');
	await page.locator('a[href^="/modules/datenbanken/"]').first().click();
	const link = page.getByRole('link', { name: /Mehr dazu im Wiki/ });
	await expect(link).toContainText('Datenbanken (SQL)');
	await link.click();
	await expect(page).toHaveURL(/\/knowledge\?artikel=datenbanken/);
	await expect(page.getByRole('heading', { level: 2, name: 'Datenbanken (SQL)' })).toBeVisible();
});

import { expect, test, type Page } from '@playwright/test';

const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin-pw-123';

async function login(page: Page) {
	await page.goto('/login');
	await page.fill('#username', 'admin');
	await page.fill('#password', ADMIN_PASSWORD);
	await page.click('button[type=submit]');
	await expect(page).toHaveURL(/\/modules/);
}

/** Lehrkraft legt eine eigene Seite an, veröffentlicht sie öffentlich und stellt sie ins Menü. */
test('Eigene Seite anlegen und veröffentlichen', async ({ page, browser }) => {
	const title = `Anfahrt ${Date.now().toString(36)}`;
	page.on('dialog', (dialog) => dialog.accept());
	await login(page);

	await page.goto('/admin/pages');
	await page.getByLabel('Titel der neuen Seite').fill(title);
	await page.getByRole('button', { name: 'Anlegen' }).click();
	await expect(page).toHaveURL(/\/admin\/pages\/\d+/);

	const settings = page.getByRole('form', { name: 'Seiteneinstellungen' });
	await settings.getByLabel('Wer darf die Seite sehen?').selectOption('PUBLIC');
	await settings.getByLabel('Im Menü anzeigen').check();
	await settings.getByRole('button', { name: 'Einstellungen speichern' }).click();
	await expect(page.getByText('Einstellungen gespeichert.')).toBeVisible();

	await page
		.getByRole('button', { name: /Textabschnitt/ })
		.first()
		.click();
	await page.getByLabel('Text (Markdown)').fill('Mit dem **Bus 42** bis zur Schule.');
	await page.getByRole('button', { name: 'Veröffentlichen' }).click();
	await expect(page.getByRole('status').filter({ hasText: 'Veröffentlicht' })).toBeVisible();

	// Besucher ohne Anmeldung sehen die Seite und den Menüpunkt
	const visitor = await browser.newPage({ viewport: { width: 1400, height: 900 } });
	await visitor.goto('/');
	await visitor.getByRole('link', { name: title }).click();
	await expect(visitor.locator('strong', { hasText: 'Bus 42' })).toBeVisible();

	// Aufräumen
	await page.goto('/admin/pages');
	await page
		.getByRole('listitem')
		.filter({ hasText: title })
		.getByRole('button', { name: 'Löschen' })
		.click();
	await expect(page.getByRole('listitem').filter({ hasText: title })).toHaveCount(0);
});

/** Lehrkraft schreibt einen Wissensartikel und veröffentlicht ihn. */
test('Wissensartikel anlegen und bearbeiten', async ({ page }) => {
	const title = `Netiquette ${Date.now().toString(36)}`;
	page.on('dialog', (dialog) => dialog.accept());
	await login(page);

	await page.goto('/knowledge');
	await expect(page.getByRole('button', { name: /Versionskontrolle/ })).toBeVisible();

	await page.goto('/admin/knowledge');
	await page.getByLabel('Titel des neuen Artikels').fill(title);
	await page.getByRole('button', { name: 'Anlegen' }).click();
	const editor = page.getByRole('region', { name: 'Artikel bearbeiten' });
	await expect(editor.getByLabel('Titel', { exact: true })).toHaveValue(title);
	await editor.getByLabel('Text (Markdown)').fill(`# ${title}\n\nImmer **freundlich** bleiben.`);
	await page.getByLabel('Veröffentlicht').check();
	await page.getByRole('button', { name: 'Speichern' }).click();
	await expect(page.getByRole('status')).toContainText('Gespeichert');

	await page.getByRole('link', { name: 'Ansehen' }).click();
	await expect(page.locator('strong', { hasText: 'freundlich' })).toBeVisible();

	await page.goto('/admin/knowledge');
	await page.getByRole('button', { name: title }).click();
	await page.getByRole('button', { name: 'Löschen' }).click();
	await expect(page.getByRole('status')).toContainText('gelöscht');
});

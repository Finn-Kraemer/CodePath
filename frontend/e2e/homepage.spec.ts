import { expect, test } from '@playwright/test';

const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin-pw-123';

/** Lehrkraft ändert die Startseite im Editor; Besucher sehen sie erst nach dem Veröffentlichen. */
test('Startseite bearbeiten und veröffentlichen', async ({ page, browser }) => {
	const title = `Willkommen ${Date.now().toString(36)}`;
	await page.goto('/login');
	await page.fill('#username', 'admin');
	await page.fill('#password', ADMIN_PASSWORD);
	await page.click('button[type=submit]');
	await expect(page).toHaveURL(/\/modules/);

	await page.goto('/admin/homepage');
	await page.getByRole('button', { name: '+ Abschnitt hinzufügen' }).click();
	await page.getByRole('button', { name: /^Textabschnitt/ }).click();
	await page.getByLabel('Überschrift', { exact: true }).fill(title);
	await page.getByLabel('Text (Markdown)').fill('Heute mit **Pizza**.');
	const preview = page.getByRole('region', { name: 'Vorschau' });
	await expect(preview.getByRole('heading', { name: title })).toBeVisible();

	await page.getByRole('button', { name: 'Entwurf speichern' }).click();
	await expect(page.getByRole('status')).toContainText('Entwurf gespeichert');

	// Entwurf ist für Besucher noch unsichtbar
	const visitor = await browser.newPage();
	await visitor.goto('/');
	await expect(visitor.getByRole('heading', { name: 'The Path to Code' })).toBeVisible();
	await expect(visitor.getByRole('heading', { name: title })).toHaveCount(0);

	await page.getByRole('button', { name: 'Veröffentlichen' }).click();
	await expect(page.getByRole('status')).toContainText('Veröffentlicht');

	await visitor.reload();
	await expect(visitor.getByRole('heading', { name: title })).toBeVisible();
	await expect(visitor.locator('strong', { hasText: 'Pizza' })).toBeVisible();

	// Aufräumen: zurück zur Standardseite
	page.on('dialog', (dialog) => dialog.accept());
	await page.getByRole('button', { name: 'Auf Standard zurücksetzen' }).click();
	await expect(page.getByRole('status')).toContainText('Standardseite');
	await visitor.reload();
	await expect(visitor.getByRole('heading', { name: title })).toHaveCount(0);
});

/** Buttons im Aufruf-Block führen wahlweise auf interne Seiten oder externe Adressen. */
test('Eigene Buttons mit internem und externem Ziel', async ({ page }) => {
	await page.goto('/login');
	await page.fill('#username', 'admin');
	await page.fill('#password', ADMIN_PASSWORD);
	await page.click('button[type=submit]');
	await expect(page).toHaveURL(/\/modules/);

	await page.goto('/admin/homepage');
	const preview = page.getByRole('region', { name: 'Vorschau' });
	await preview.getByText('Starte jetzt mit dem').click();

	await page.getByRole('button', { name: '+ Button hinzufügen' }).click();
	await page.getByLabel('Beschriftung').fill('Wissen');
	await page.getByLabel('Ziel').selectOption('/knowledge');

	await page.getByRole('button', { name: '+ Button hinzufügen' }).click();
	await page.getByLabel('Beschriftung').nth(1).fill('Schulwebsite');
	await page
		.getByLabel('Ziel')
		.nth(1)
		.selectOption({ label: 'Externe Adresse oder eigener Pfad …' });
	await page.locator('input.mono').fill('www.schule.de');

	await expect(preview.getByRole('link', { name: 'Wissen' })).toHaveAttribute('href', '/knowledge');
	const external = preview.getByRole('link', { name: 'Schulwebsite' });
	await expect(external).toHaveAttribute('href', 'https://www.schule.de');
	await expect(external).toHaveAttribute('target', '_blank');
});

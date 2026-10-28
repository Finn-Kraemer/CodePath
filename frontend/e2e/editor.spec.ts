import { expect, test } from '@playwright/test';
import { fileURLToPath } from 'node:url';

const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin-pw-123';
// Ein Bild aus den mitgelieferten Inhalten (Clean-Desk-Aufgabe)
const fixture = fileURLToPath(
	new URL(
		'../../content/it-sicherheit/media/4206d5871e58d7238b3ef9b0ee4947a35c784946158c73fd75b97f59344913e9.png',
		import.meta.url
	)
);

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

/** Bild hochladen, Zielbereiche aufziehen, prüfen, veröffentlichen – und als Schüleransicht öffnen. */
test('Bild-Aufgabe mit Upload im Editor anlegen', async ({ page }) => {
	const slug = `bild-${Date.now().toString(36)}`;
	await page.goto('/login');
	await page.fill('#username', 'admin');
	await page.fill('#password', ADMIN_PASSWORD);
	await page.click('button[type=submit]');
	await expect(page).toHaveURL(/\/modules/);

	await page.goto('/admin/content');
	const gitSection = page
		.locator('section', { hasText: 'git' })
		.filter({ hasText: 'Warum brauchen wir Git?' });
	await gitSection.getByLabel('Neue Aufgabe: Titel').fill('Finde den Monitor');
	await gitSection.getByLabel('Slug').fill(slug);
	await gitSection.getByRole('combobox').selectOption('IMAGE_HOTSPOT');
	await gitSection.getByRole('button', { name: 'Anlegen' }).click();
	await expect(page).toHaveURL(/\/admin\/content\/\d+/);

	await page.getByRole('button', { name: 'Bild wählen oder hochladen' }).click();
	await page.getByLabel(/Neues Bild hochladen/).setInputFiles(fixture);
	await page.getByLabel(/Bildbeschreibung für Screenreader/).fill('Ein Schreibtisch');

	// Zielbereich aufziehen: Monitor in der Bildmitte oben
	const canvas = page.getByRole('application', { name: 'Zielbereiche zeichnen' });
	await canvas.scrollIntoViewIfNeeded();
	const box = (await canvas.boundingBox())!;
	await page.mouse.move(box.x + box.width * 0.34, box.y + box.height * 0.17);
	await page.mouse.down();
	await page.mouse.move(box.x + box.width * 0.66, box.y + box.height * 0.47, { steps: 5 });
	await page.mouse.up();
	await page.getByLabel('Bezeichnung Zielbereich 1').fill('Monitor');

	await page.getByRole('button', { name: 'Entwurf speichern' }).click();
	await expect(page.getByRole('status')).toContainText('Entwurf gespeichert');
	await page.getByRole('button', { name: 'Prüfen', exact: true }).click();
	await expect(page.getByText('Prüfung bestanden')).toBeVisible();
	await page.getByRole('button', { name: 'Veröffentlichen' }).click();
	await expect(page.getByRole('status')).toContainText('Veröffentlicht');

	await page.goto(`/modules/git/${slug}`);
	await expect(page.getByRole('img', { name: 'Ein Schreibtisch' })).toBeVisible();
});

/** Archivieren, Folgen ansehen, mit Slug bestätigen, endgültig löschen. */
test('Aufgabe endgültig löschen', async ({ page }) => {
	const slug = `weg-${Date.now().toString(36)}`;
	await page.goto('/login');
	await page.fill('#username', 'admin');
	await page.fill('#password', ADMIN_PASSWORD);
	await page.click('button[type=submit]');
	await expect(page).toHaveURL(/\/modules/);

	await page.goto('/admin/content');
	const gitSection = page
		.locator('section', { hasText: 'git' })
		.filter({ hasText: 'Warum brauchen wir Git?' });
	await gitSection.getByLabel('Neue Aufgabe: Titel').fill('Wird gelöscht');
	await gitSection.getByLabel('Slug').fill(slug);
	await gitSection.getByRole('button', { name: 'Anlegen' }).click();
	await expect(page).toHaveURL(/\/admin\/content\/\d+/);
	const editorUrl = page.url();

	// Löschen gibt es erst nach dem Archivieren
	await expect(page.getByRole('button', { name: 'Endgültig löschen …' })).toHaveCount(0);
	await page.getByRole('button', { name: 'Archivieren' }).click();
	await expect(page.getByRole('status')).toContainText('Archiviert');
	await page.getByRole('button', { name: 'Endgültig löschen …' }).click();

	const panel = page.getByRole('region', { name: 'Endgültig löschen' });
	await expect(panel).toContainText('0 Versuche');
	const confirm = panel.getByRole('button', { name: 'Endgültig löschen', exact: true });
	await expect(confirm).toBeDisabled();
	await panel.getByLabel(/Slug .* eingeben/).fill(slug);
	await confirm.click();
	await expect(page).toHaveURL(/\/admin\/content$/);

	await page.goto(editorUrl);
	await expect(page.getByRole('alert')).toContainText('nicht gefunden');
});

import { expect, test, type Page } from '@playwright/test';

const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin-pw-123';

/** Stiller Hilfe-Ruf: Schüler ruft → Lehrkraft sieht ihn live → „Ich komme“ → erledigt. */
test('Ich hänge fest: Schüler ruft, Lehrkraft kommt und erledigt', async ({ browser }) => {
	const suffix = Date.now().toString(36);
	const courseName = `Hilfe ${suffix}`;
	const username = `hilfe-${suffix}`;

	const teacher = await (await browser.newContext()).newPage();
	await login(teacher, 'admin', ADMIN_PASSWORD);
	await teacher.goto('/admin/courses');
	await teacher.getByPlaceholder('z. B. 8a Projekttag').fill(courseName);
	await teacher.getByRole('button', { name: 'Kurs anlegen' }).click();
	const joinCode = (
		await teacher
			.locator('article', { hasText: courseName })
			.locator('.font-mono.text-3xl')
			.innerText()
	).trim();
	await teacher.goto('/admin/modules');
	await Promise.all([
		teacher.waitForResponse((r) => r.url().includes('/api/admin/modules?courseId=')),
		teacher.getByLabel('Kurs').selectOption({ label: courseName })
	]);
	await teacher.waitForLoadState('networkidle');
	const programmierung = teacher.locator('[data-module="programmierung"]');
	await programmierung.getByRole('button', { name: 'Freigeben' }).click();
	await expect(programmierung.getByRole('button', { name: 'Sperren' })).toBeVisible();

	// Lehrkraft hat die Warteschlange offen
	await teacher.goto('/admin/help');
	await expect(teacher.getByRole('heading', { name: 'Hilfe-Rufe' })).toBeVisible();

	const student = await (await browser.newContext()).newPage();
	await student.goto(`/register?code=${joinCode}`);
	await student.fill('#username', username);
	await student.fill('#password', 'e2e-passwort');
	await student.fill('#confirmPassword', 'e2e-passwort');
	await student.click('button[type=submit]');
	await expect(student).toHaveURL(/\/modules/);

	await student.goto('/modules/programmierung/p1');
	const help = student.getByTestId('help');
	await help.getByRole('button', { name: /Ich hänge fest/ }).click();
	await help.getByLabel('Verstehe die Aufgabe nicht').check();
	await help.getByLabel('Notiz (optional)').fill('Was ist eine Variable?');
	await help.getByRole('button', { name: 'Hilfe rufen' }).click();
	await expect(help).toContainText('Deine Lehrkraft weiß Bescheid');

	// Live beim Lehrer – ohne Neuladen
	const entry = teacher.locator(`[data-testid="help-entry"][data-user="${username}"]`);
	await expect(entry).toBeVisible({ timeout: 15_000 });
	await expect(entry).toContainText('Was ist eine Variable?');
	await expect(entry).toContainText('Verstehe die Aufgabe nicht');
	await expect(entry).toContainText(courseName);

	await entry.getByRole('button', { name: 'Ich komme' }).click();
	await expect(help).toContainText('Deine Lehrkraft ist unterwegs', { timeout: 15_000 });

	await entry.getByRole('button', { name: 'Nachricht schicken' }).click();
	await entry.getByLabel(/Nachricht an/).fill('Schau dir Tipp 1 an');
	await entry.getByRole('button', { name: 'Senden' }).click();
	await expect(help.getByTestId('help-message')).toContainText('Schau dir Tipp 1 an', {
		timeout: 15_000
	});

	await entry.getByRole('button', { name: 'Erledigt' }).click();
	await expect(entry).toHaveCount(0);
	await expect(help.getByRole('button', { name: /Ich hänge fest/ })).toBeVisible({
		timeout: 15_000
	});

	// Mitschüler und Beamer sehen nichts
	await teacher.goto('/projector');
	await expect(teacher.getByTestId('help-badge')).toHaveCount(0);
	await expect(teacher.getByText('Was ist eine Variable?')).toHaveCount(0);
});

async function login(page: Page, username: string, password: string) {
	await page.goto('/login');
	await page.fill('#username', username);
	await page.fill('#password', password);
	await page.click('button[type=submit]');
	await expect(page).toHaveURL(/\/modules/);
}

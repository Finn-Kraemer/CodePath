import { expect, test } from '@playwright/test';

const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin-pw-123';

/**
 * Der Weg aus dem Plan: Kurs anlegen, Modul freischalten, mit Code beitreten, Aufgabe lösen,
 * Punkte in der Rangliste sehen.
 */
test('Kurs anlegen, beitreten, Aufgabe lösen, Punkte sehen', async ({ browser }) => {
	const suffix = Date.now().toString(36);
	const courseName = `E2E ${suffix}`;

	// Lehrkraft
	const teacher = await (await browser.newContext()).newPage();
	await teacher.goto('/login');
	await teacher.fill('#username', 'admin');
	await teacher.fill('#password', ADMIN_PASSWORD);
	await teacher.click('button[type=submit]');
	await expect(teacher).toHaveURL(/\/modules/);

	await teacher.goto('/admin/courses');
	await teacher.getByPlaceholder('z. B. 8a Projekttag').fill(courseName);
	await teacher.getByRole('button', { name: 'Kurs anlegen' }).click();
	const card = teacher.locator('article', { hasText: courseName });
	const joinCode = (await card.locator('.font-mono.text-3xl').innerText()).trim();
	expect(joinCode).toMatch(/^[A-Z0-9]{6}$/);

	await teacher.goto('/admin/modules');
	await teacher.getByLabel('Kurs').selectOption({ label: courseName });
	await teacher.locator('[data-module="git"]').getByRole('button', { name: 'Freigeben' }).click();
	await expect(
		teacher.locator('[data-module="git"]').getByRole('button', { name: 'Sperren' })
	).toBeVisible();

	// Schüler
	const student = await (await browser.newContext()).newPage();
	await student.goto(`/register?code=${joinCode}`);
	await student.fill('#username', `e2e-${suffix}`);
	await student.fill('#password', 'e2e-passwort');
	await student.fill('#confirmPassword', 'e2e-passwort');
	await student.click('button[type=submit]');
	await expect(student).toHaveURL(/\/modules/);

	await student.getByRole('link', { name: 'Öffnen' }).first().click();
	await student.getByRole('link', { name: /Projekt-Setup im Terminal/ }).click();
	const terminal = student.getByLabel('Befehl eingeben');
	for (const command of ['mkdir website', 'cd website', 'touch index.html styles.css']) {
		await terminal.fill(command);
		await terminal.press('Enter');
	}
	await student.getByRole('button', { name: 'Ergebnis prüfen' }).click();
	await expect(student.getByRole('status')).toContainText('Richtig');

	await student.goto('/leaderboard');
	await expect(student.getByText(`e2e-${suffix}`)).toBeVisible();
	await expect(student.getByText('24', { exact: true }).first()).toBeVisible();
});

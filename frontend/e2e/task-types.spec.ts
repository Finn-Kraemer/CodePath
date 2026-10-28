import { expect, test, type Page } from '@playwright/test';

const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin-pw-123';

/** Phase 5: Die neuen Aufgabentypen lassen sich in der Schüleransicht bedienen und werden serverseitig bewertet. */
test('Zuordnen, Code-Puzzle, Zahlensysteme und Markieren lösen', async ({ browser }) => {
	const suffix = Date.now().toString(36);
	const courseName = `Typen ${suffix}`;

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
	for (const module of ['cloud-netze', 'programmierung', 'digitaltechnik', 'it-sicherheit']) {
		const card = teacher.locator(`[data-module="${module}"]`);
		await card.getByRole('button', { name: 'Freigeben' }).click();
		await expect(card.getByRole('button', { name: 'Sperren' })).toBeVisible();
	}

	const student = await (await browser.newContext()).newPage();
	await student.goto(`/register?code=${joinCode}`);
	await student.fill('#username', `typen-${suffix}`);
	await student.fill('#password', 'e2e-passwort');
	await student.fill('#confirmPassword', 'e2e-passwort');
	await student.click('button[type=submit]');
	await expect(student).toHaveURL(/\/modules/);

	// Zuordnen
	await student.goto('/modules/cloud-netze/c12');
	const ports: Record<string, string> = {
		'HTTP (Webseiten)': '80',
		'HTTPS (verschlüsselte Webseiten)': '443',
		'SSH (Fernwartung)': '22',
		'DNS (Namensauflösung)': '53',
		'SMTP (E-Mail-Versand)': '25'
	};
	for (const [service, port] of Object.entries(ports)) {
		await student.getByLabel(service, { exact: true }).selectOption({ label: port });
	}
	await student.getByRole('button', { name: 'Zuordnung prüfen' }).click();
	await expect(student.getByRole('status')).toContainText('Alle Paare stimmen');

	// Code-Puzzle: Bausteine in Reihenfolge anklicken, dann einrücken
	await student.goto('/modules/programmierung/p15');
	const blocks = student.getByRole('region', { name: 'Bausteine' });
	for (const code of [
		'zahl = 3',
		'while zahl > 0:',
		'print(zahl)',
		'zahl = zahl - 1',
		"print('Start!')"
	]) {
		await blocks.getByRole('button', { name: code, exact: true }).click();
	}
	const program = student.getByRole('region', { name: 'Dein Programm' });
	// Neue Zeilen übernehmen die Einrückung der vorigen – hier stehen noch alle ganz links
	await program.getByRole('button', { name: 'Mehr einrücken' }).nth(2).click();
	await program.getByRole('button', { name: 'Mehr einrücken' }).nth(3).click();
	await student.getByRole('button', { name: 'Programm prüfen' }).click();
	await expect(student.getByRole('status')).toContainText('richtig zusammengesetzt');

	// Zahlensysteme: jede Person hat eigene Zahlen
	await student.goto('/modules/digitaltechnik/d15');
	const inputs = student.locator('input[id^="convert-"]');
	await expect(inputs).toHaveCount(3);
	for (let i = 0; i < 3; i++) {
		const label = await student.locator(`label[for="convert-${i}"] [data-value]`).innerText();
		const decimal = parseInt(label, 10);
		await inputs.nth(i).fill(decimal.toString(2));
	}
	await student.getByRole('button', { name: 'Antwort prüfen' }).click();
	await expect(student.getByRole('status')).toContainText('Alles richtig umgerechnet');

	// Markieren: Warnzeichen der Phishing-Mail
	await student.goto('/modules/it-sicherheit/s14');
	for (const line of [
		'service@paypa1-sicherheit.com',
		'Sehr geehrter Kunde',
		'innerhalb von 24 Stunden',
		'Ihr Passwort',
		'paypa1-konto-check'
	]) {
		await student.getByRole('button', { name: line }).click();
	}
	await student.getByRole('button', { name: 'Markierungen prüfen' }).click();
	await expect(student.getByRole('status')).toContainText('Alles gefunden');
});

async function login(page: Page, username: string, password: string) {
	await page.goto('/login');
	await page.fill('#username', username);
	await page.fill('#password', password);
	await page.click('button[type=submit]');
	await expect(page).toHaveURL(/\/modules/);
}

import { expect, test, type Page } from '@playwright/test';

const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin-pw-123';

/** Terminal mit Antwortfeld und Git mit Merge-Konflikt: Browser-Simulation und Bewertung auf dem Server passen zusammen. */
test('Kommandozeile und Git im simulierten Terminal lösen', async ({ browser }) => {
	const suffix = Date.now().toString(36);
	const courseName = `Terminal ${suffix}`;

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
	for (const module of ['terminal', 'git']) {
		const card = teacher.locator(`[data-module="${module}"]`);
		await card.getByRole('button', { name: 'Freigeben' }).click();
		await expect(card.getByRole('button', { name: 'Sperren' })).toBeVisible();
	}

	const student = await (await browser.newContext()).newPage();
	await student.goto(`/register?code=${joinCode}`);
	await student.fill('#username', `terminal-${suffix}`);
	await student.fill('#password', 'e2e-passwort');
	await student.fill('#confirmPassword', 'e2e-passwort');
	await student.click('button[type=submit]');
	await expect(student).toHaveURL(/\/modules/);

	// Log durchsuchen: Pipe, Tab-Vervollständigung, Antwortfeld
	await student.goto('/modules/terminal/terminal-grep');
	const terminal = student.getByLabel('Befehl eingeben');
	await terminal.fill('cd ser');
	await terminal.press('Tab');
	await expect(terminal).toHaveValue('cd server/');
	await terminal.press('Enter');
	await run(student, 'grep ERROR server.log');
	const errorLine = await student.getByText(/ERROR .* antwortet nicht/).innerText();
	const service = /ERROR (.*) antwortet nicht/.exec(errorLine)![1];
	await student.getByLabel('Welcher Dienst ist ausgefallen?').fill(service);
	await student.getByRole('button', { name: 'Ergebnis prüfen' }).click();
	await expect(student.getByRole('status')).toContainText('Richtig');

	// Merge-Konflikt: Der Commit-Graph zeigt beide Linien, der Server prüft das Repository
	await student.goto('/modules/git/git-konflikt');
	const graph = student.getByLabel('Commit-Verlauf');
	await expect(graph).toContainText('Überschriften lila');
	for (const command of ['cd schulplaner', 'git merge design']) await run(student, command);
	await expect(student.getByText('KONFLIKT: Beide Seiten haben style.css geändert.')).toBeVisible();
	await student.getByRole('button', { name: 'Ergebnis prüfen' }).click();
	await expect(student.getByRole('status')).toContainText('Anforderungen erfüllt');
	for (const command of [
		'git checkout --theirs style.css',
		'git add style.css',
		'git commit -m "Lila gewinnt"'
	])
		await run(student, command);
	await expect(graph).toContainText('Lila gewinnt');
	await student.getByRole('button', { name: 'Ergebnis prüfen' }).click();
	await expect(student.getByRole('status')).toContainText('Richtig');
});

async function run(page: Page, command: string) {
	const terminal = page.getByLabel('Befehl eingeben');
	await terminal.fill(command);
	await terminal.press('Enter');
}

async function login(page: Page, username: string, password: string) {
	await page.goto('/login');
	await page.fill('#username', username);
	await page.fill('#password', password);
	await page.click('button[type=submit]');
	await expect(page).toHaveURL(/\/modules/);
}

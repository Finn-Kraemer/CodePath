import { expect, test, type Page } from '@playwright/test';

const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin-pw-123';

/** Einzelne Aufgaben freigeben, Aufgaben umsortieren und die Python-Werkbank in Praxisaufgaben. */
test('Einzelne Aufgabe freigeben und Praxisaufgabe mit Code abgeben', async ({ browser }) => {
	const suffix = Date.now().toString(36);
	const courseName = `Freigabe ${suffix}`;

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

	// Git bleibt gesperrt, nur die erste Aufgabe wird freigegeben
	const git = teacher.locator('[data-module="git"]');
	await git.getByText('Einzelne Aufgaben').click();
	await Promise.all([
		teacher.waitForResponse((r) => r.url().includes('/release?mode=SHOWN')),
		git.getByLabel('Freigabe Warum brauchen wir Git?').selectOption('SHOWN')
	]);
	await expect(git.locator('[data-adjusted]')).toContainText('1 Aufgabe einzeln eingestellt');

	const programmierung = teacher.locator('[data-module="programmierung"]');
	await programmierung.getByRole('button', { name: 'Freigeben' }).click();
	await expect(programmierung.getByRole('button', { name: 'Sperren' })).toBeVisible();

	const student = await (await browser.newContext()).newPage();
	await student.goto(`/register?code=${joinCode}`);
	await student.fill('#username', `frei-${suffix}`);
	await student.fill('#password', 'e2e-passwort');
	await student.fill('#confirmPassword', 'e2e-passwort');
	await student.click('button[type=submit]');
	await expect(student).toHaveURL(/\/modules/);

	await student.goto('/modules/git');
	await expect(student.getByRole('link', { name: /Warum brauchen wir Git/ })).toBeVisible();
	await expect(student.getByRole('link', { name: /Was ist ein Repository/ })).toHaveCount(0);

	// Praxisaufgabe mit Python-Werkbank: Programm ausführen und mit abgeben
	await student.goto('/modules/programmierung/p14');
	const editor = student.getByLabel('Python-Editor');
	await expect(editor.locator('.monaco-editor')).toBeVisible();
	await editor.locator('.view-lines').click();
	await student.keyboard.press('Control+End');
	await student.keyboard.type('print("Hallo Werkbank")');
	await student.getByRole('button', { name: 'Programm ausführen' }).click({ timeout: 60_000 });
	await expect(student.getByText('Hallo Werkbank', { exact: true })).toBeVisible({
		timeout: 30_000
	});
	await student.locator('.ProseMirror').click();
	await student.keyboard.type('Mein Plan: erst einlesen, dann ausgeben.');
	await student.getByRole('button', { name: 'Zur Bewertung einreichen' }).click();
	await expect(student.getByText('Abgabe liegt bei der Lehrkraft zur Bewertung')).toBeVisible();
});

test('Aufgaben im Editor umsortieren', async ({ page }) => {
	await login(page, 'admin', ADMIN_PASSWORD);
	await page.goto('/admin/content');
	const list = page.getByRole('list', { name: /Aufgaben von Git/ });
	const first = list.locator('li').first();
	const title = (await first.locator('a > span').first().innerText()).replace(/^1\s*/, '').trim();
	await Promise.all([
		page.waitForResponse((r) => r.url().endsWith('/order') && r.ok()),
		list.getByRole('button', { name: `${title} nach unten` }).click()
	]);
	await expect(list.locator('li').nth(1)).toContainText(title);

	// Nach dem Neuladen gilt die gespeicherte Reihenfolge – danach zurückschieben
	await page.reload();
	await expect(list.locator('li').nth(1)).toContainText(title);
	await Promise.all([
		page.waitForResponse((r) => r.url().endsWith('/order') && r.ok()),
		list.getByRole('button', { name: `${title} nach oben` }).click()
	]);
	await expect(list.locator('li').first()).toContainText(title);
});

async function login(page: Page, username: string, password: string) {
	await page.goto('/login');
	await page.fill('#username', username);
	await page.fill('#password', password);
	await page.click('button[type=submit]');
	await expect(page).toHaveURL(/\/modules/);
}

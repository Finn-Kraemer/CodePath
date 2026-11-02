import { expect, type Browser, type Page } from '@playwright/test';

export const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin-pw-123';

export async function login(page: Page, username: string, password: string) {
	await page.goto('/login');
	await page.fill('#username', username);
	await page.fill('#password', password);
	await page.click('button[type=submit]');
	await expect(page).toHaveURL(/\/modules/);
}

/** Lehrkraft legt einen Kurs an und gibt Module frei; liefert Seite und Beitrittscode. */
export async function courseWithModules(browser: Browser, courseName: string, modules: string[]) {
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
	for (const module of modules) {
		const card = teacher.locator(`[data-module="${module}"]`);
		await card.getByRole('button', { name: 'Freigeben' }).click();
		await expect(card.getByRole('button', { name: 'Sperren' })).toBeVisible();
	}
	return { teacher, joinCode };
}

export async function registerStudent(browser: Browser, joinCode: string, username: string) {
	const student = await (await browser.newContext()).newPage();
	await student.goto(`/register?code=${joinCode}`);
	await student.fill('#username', username);
	await student.fill('#password', 'e2e-passwort');
	await student.fill('#confirmPassword', 'e2e-passwort');
	await student.click('button[type=submit]');
	await expect(student).toHaveURL(/\/modules/);
	return student;
}

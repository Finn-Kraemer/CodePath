import { expect, test, type Page } from '@playwright/test';
import { courseWithModules, registerStudent } from './helpers';

async function submitPractice(page: Page, text: string) {
	await page.goto('/modules/web-entwicklung/w13');
	await page.locator('.ProseMirror').click();
	await page.keyboard.type(text);
	await page.getByRole('button', { name: 'Zur Bewertung einreichen' }).click();
	await expect(page.getByText('Abgabe liegt bei der Lehrkraft zur Bewertung')).toBeVisible();
}

/** Peer-Review: Abgabe einer Mitschülerin anonym bewerten, Rückmeldung erscheint bei ihr. */
test('Praxisabgabe anonym bewerten', async ({ browser }) => {
	const suffix = Date.now().toString(36);
	const { joinCode } = await courseWithModules(browser, `Peer ${suffix}`, ['web-entwicklung']);
	const lena = await registerStudent(browser, joinCode, `peer-a-${suffix}`);
	const tom = await registerStudent(browser, joinCode, `peer-b-${suffix}`);

	await submitPractice(tom, 'Toms Stadtseite über Bremen');
	await submitPractice(lena, 'Lenas Stadtseite über Kiel');

	const panel = lena.getByTestId('peer-review');
	await panel.getByRole('button', { name: 'Abgaben anderer bewerten' }).click();
	const assignment = panel.getByTestId('peer-assignment');
	await expect(assignment).toHaveCount(1);
	await expect(assignment).toContainText('Toms Stadtseite');
	await expect(assignment).not.toContainText(`peer-b-${suffix}`);
	const inputs = assignment.locator('input[type=number]');
	for (let i = 0; i < (await inputs.count()); i++) await inputs.nth(i).fill('5');
	await assignment.getByRole('textbox').fill('Gute Liste, Link fehlt noch.');
	await assignment.getByRole('button', { name: /Bewertung abschicken/ }).click();
	await expect(assignment).toContainText('✓ bewertet');

	await tom.reload();
	await expect(tom.getByTestId('peer-received')).toContainText('Gute Liste, Link fehlt noch.');
	await expect(tom.getByTestId('peer-received')).not.toContainText(`peer-a-${suffix}`);
});

import { expect, test, type Page } from '@playwright/test';

const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin-pw-123';
const PART = /Teil \d: ([A-Z2-9]{4})/;

/**
 * Browser-Labor im echten Browser: Playwright übernimmt, was Schüler mit den Entwicklerwerkzeugen
 * tun (Antworten im Netzwerk lesen, localStorage setzen, CSS und IndexedDB untersuchen, Konsole).
 */
test('Browser-Labor: Wetterstation und Escape-Zimmer lösen', async ({ browser }) => {
	const suffix = Date.now().toString(36);
	const teacher = await (await browser.newContext()).newPage();
	await login(teacher, 'admin', ADMIN_PASSWORD);
	await teacher.goto('/admin/courses');
	await teacher.getByPlaceholder('z. B. 8a Projekttag').fill(`Labor ${suffix}`);
	await teacher.getByRole('button', { name: 'Kurs anlegen' }).click();
	const joinCode = (
		await teacher
			.locator('article', { hasText: `Labor ${suffix}` })
			.locator('.font-mono.text-3xl')
			.innerText()
	).trim();
	await teacher.goto('/admin/modules');
	await Promise.all([
		teacher.waitForResponse((r) => r.url().includes('/api/admin/modules?courseId=')),
		teacher.getByLabel('Kurs').selectOption({ label: `Labor ${suffix}` })
	]);
	await teacher.waitForLoadState('networkidle');
	const card = teacher.locator('[data-module="web-entwicklung"]');
	await card.getByRole('button', { name: 'Freigeben' }).click();
	await expect(card.getByRole('button', { name: 'Sperren' })).toBeVisible();

	const context = await browser.newContext();
	const student = await context.newPage();
	await student.goto(`/register?code=${joinCode}`);
	await student.fill('#username', `labor-${suffix}`);
	await student.fill('#password', 'e2e-passwort');
	await student.fill('#confirmPassword', 'e2e-passwort');
	await student.click('button[type=submit]');
	await expect(student).toHaveURL(/\/modules/);

	// --- Wetterstation ---------------------------------------------------------------------
	await student.goto('/modules/web-entwicklung/lab-wetter');
	const [lab] = await Promise.all([
		context.waitForEvent('page'),
		student.getByRole('link', { name: /Wetterstation öffnen/ }).click()
	]);
	const consoleMessages: string[] = [];
	lab.on('console', (m) => consoleMessages.push(m.text()));
	await lab.reload();
	await expect(lab.locator('#fehler')).toBeVisible();
	expect(consoleMessages.join('\n')).toContain('Wetterdaten unvollständig');

	// Netzwerk: v2 abrufen, Antwort und Header lesen
	const v2 = await lab.evaluate(async () => {
		const r = await fetch(location.pathname.replace(/\/$/, '') + '/daten?v=2');
		return { body: await r.text(), key: r.headers.get('X-Api-Schluessel') };
	});
	const teil1 = v2.body.match(PART)![1];

	// Web-Speicher: Diagnosemodus einschalten und neu laden
	await lab.evaluate(() => localStorage.setItem('wetter-diagnose', 'an'));
	await lab.reload();
	await expect(lab.locator('#diagnose pre')).toContainText('Teil 2');
	const teil2 = (await lab.locator('#diagnose pre').innerText()).match(PART)![1];

	// Konsole: Verlauf mit dem Schlüssel
	const tage = await lab.evaluate(async (key) => {
		const r = await fetch(location.pathname.replace(/\/$/, '') + '/verlauf', {
			headers: { 'X-Api-Schluessel': key ?? '' }
		});
		return r.text();
	}, v2.key);
	const teil3 = tage.match(PART)![1];
	await lab.close();

	await student.getByLabel(/Dein Code/).fill(`${teil1}-${teil2}-${teil3}`);
	await student.getByRole('button', { name: 'Code prüfen' }).click();
	await expect(student.getByRole('status')).toContainText('Alle Teile stimmen');

	// --- Escape-Zimmer ---------------------------------------------------------------------
	await student.goto('/modules/web-entwicklung/lab-escape');
	const [room] = await Promise.all([
		context.waitForEvent('page'),
		student.getByRole('link', { name: /Escape-Zimmer öffnen/ }).click()
	]);
	await expect(room.locator('#truhe')).toHaveText('Die Truhe ist verschlossen.');

	// Inspektor: berechneter Inhalt des ::after
	const after = await room.evaluate(
		() => getComputedStyle(document.querySelector('.wand')!, '::after').content
	);
	const e1 = after.replace(/"/g, '').match(PART)![1];
	// Netzwerk: Inhalt der 404-Antwort
	const e2 = (
		await room.evaluate(async () =>
			(await fetch(location.pathname.replace(/\/$/, '') + '/schluessel')).text()
		)
	).match(PART)![1];
	// Web-Speicher: IndexedDB, rückwärts gespeichert
	const stored = await room.evaluate(
		() =>
			new Promise<string>((resolve) => {
				const open = indexedDB.open('escape-zimmer', 1);
				open.onsuccess = () => {
					const get = open.result.transaction('truhe').objectStore('truhe').get(3);
					get.onsuccess = () => resolve(get.result.inhalt);
				};
			})
	);
	const e3 = stored.split('').reverse().join('').match(PART)![1];
	// Konsole: Tür öffnen
	await room.evaluate(
		([a, b, c]) =>
			(
				window as unknown as { zimmer: { oeffnen: (...t: string[]) => Promise<void> } }
			).zimmer.oeffnen(a, b, c),
		[e1, e2, e3]
	);
	await expect(room.locator('#tuer')).toContainText('Die Tür ist offen');
	const e4 = (await room.locator('#tuer').innerText()).match(PART)![1];
	await room.close();

	await student.getByLabel(/Dein Code/).fill(`${e1}-${e2}-${e3}-${e4}`.toLowerCase());
	await student.getByRole('button', { name: 'Code prüfen' }).click();
	await expect(student.getByRole('status')).toContainText('Alle Teile stimmen');
});

async function login(page: Page, username: string, password: string) {
	await page.goto('/login');
	await page.fill('#username', username);
	await page.fill('#password', password);
	await page.click('button[type=submit]');
	await expect(page).toHaveURL(/\/modules/);
}

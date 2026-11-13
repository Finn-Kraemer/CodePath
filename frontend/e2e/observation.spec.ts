import { expect, test } from '@playwright/test';
import { courseWithModules, registerStudent } from './helpers';

/** Klassenraum: Raum anlegen → Person zuweisen → Kriterium mit Optionen → bewerten und Notiz → PDF/Export. */
test('Klassenraum mit Sitzplan und Beobachtungsbogen', async ({ browser }) => {
	const suffix = Date.now().toString(36);
	const courseName = `Raum ${suffix}`;
	const { teacher, joinCode } = await courseWithModules(browser, courseName, []);
	const student = await registerStudent(browser, joinCode, `mia-${suffix}`);

	await teacher.goto('/admin/classroom');
	const [loaded] = await Promise.all([
		teacher.waitForResponse(
			(r) => /\/api\/admin\/observations\/\d+\/open$/.test(r.url()) && r.ok()
		),
		teacher.getByLabel('Kurs').selectOption({ label: courseName })
	]);
	const courseId = (await loaded.json()).courseId as number;

	// Raum anlegen: leerer Raum öffnet direkt „Raum bearbeiten“
	await expect(teacher.getByRole('tab', { name: 'Raum bearbeiten' })).toHaveAttribute(
		'aria-selected',
		'true'
	);
	await Promise.all([
		teacher.waitForResponse((r) => r.url().endsWith('/layout') && r.ok()),
		teacher.getByRole('button', { name: '+ Zweiertisch' }).click()
	]);
	await expect(teacher.getByTestId('item-panel')).toBeVisible();

	// Person zuweisen
	await teacher.getByRole('tab', { name: 'Sitzplan' }).click();
	const seat = teacher.locator('[data-seat-id]').first();
	await seat.click();
	const panel = teacher.getByTestId('seat-panel');
	await panel.getByLabel('Person zuweisen').selectOption({ label: `mia-${suffix}` });
	await Promise.all([
		teacher.waitForResponse((r) => r.url().endsWith('/seat') && r.ok()),
		panel.getByRole('button', { name: 'Zuweisen', exact: true }).click()
	]);
	await expect(seat).toHaveAttribute('aria-label', new RegExp(`mia-${suffix}`));
	await expect(teacher.getByTestId('unseated')).toContainText('Ohne Platz (0)');

	// Kriterium mit Optionen
	await teacher.getByRole('button', { name: '+ Kriterium' }).click();
	const form = teacher.getByTestId('criterion-form');
	await form.getByLabel('Name').fill('Interesse');
	await form.getByLabel('Beschreibung der Option 1').fill('Neugier durch Fragen zum Beruf');
	await form.getByLabel('Beschreibung der Option 2').fill('Wirkt motiviert und aufgeweckt');
	await Promise.all([
		teacher.waitForResponse((r) => r.url().endsWith('/criteria') && r.ok()),
		form.getByRole('button', { name: 'Speichern' }).click()
	]);

	// Bewerten und Notiz schreiben – ohne Extra-Klick gespeichert
	const select = teacher.getByLabel(`Interesse für mia-${suffix}`);
	await Promise.all([
		teacher.waitForResponse((r) => r.url().includes('/ratings/') && r.ok()),
		select.selectOption('1')
	]);
	await expect(teacher.getByText('Neugier durch Fragen zum Beruf', { exact: true })).toBeVisible();
	await Promise.all([
		teacher.waitForResponse((r) => r.url().endsWith('/note') && r.ok()),
		teacher.getByLabel(`Notizen zu mia-${suffix}`).fill('Fragt viel zu Ausbildungswegen')
	]);
	await expect(teacher.getByText('gespeichert ✓')).toBeVisible();

	// Nach dem Neuladen ist alles da
	await teacher.reload();
	await expect(teacher.getByLabel(`Interesse für mia-${suffix}`)).toHaveValue('1');
	await expect(teacher.getByLabel(`Notizen zu mia-${suffix}`)).toHaveValue(
		'Fragt viel zu Ausbildungswegen'
	);

	// Klick auf den belegten Platz springt zur Zeile
	await teacher.locator('[data-seat-id]').first().click();
	await expect(teacher.getByLabel(`Interesse für mia-${suffix}`)).toBeFocused();

	// PDF und Export
	const pdf = await teacher.request.get(`/api/admin/observations/${courseId}/pdf?perPerson=true`);
	expect(pdf.ok()).toBe(true);
	expect(pdf.headers()['content-type']).toContain('application/pdf');
	const exported = await teacher.request.get(`/api/admin/observations/${courseId}/export.json`);
	const data = await exported.json();
	expect(data.criteria[0].options[0].description).toBe('Neugier durch Fragen zum Beruf');
	expect(data.people[0].ratings.Interesse).toBe('1');
	const csv = await teacher.request.get(`/api/admin/observations/${courseId}/export.csv`);
	expect(await csv.text()).toContain('Fragt viel zu Ausbildungswegen');

	// Schülerinnen und Schüler sehen nichts davon
	const forbidden = await student.request.get(`/api/admin/observations?courseId=${courseId}`);
	expect(forbidden.status()).toBe(403);
	await student.goto('/admin/classroom');
	await expect(student.getByText('Fragt viel zu Ausbildungswegen')).toHaveCount(0);
});
